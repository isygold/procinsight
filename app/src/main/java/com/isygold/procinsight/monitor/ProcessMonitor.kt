package com.isygold.procinsight.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.isygold.procinsight.data.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProcessMonitor(private val context: Context? = null) {

    private data class StatFields(
        val pid: Int,
        val comm: String,
        val state: String,
        val utime: Long,
        val stime: Long,
        val priority: Int,
        val nice: Int,
        val rssPages: Long
    )

    // Per-PID previous CPU ticks for delta computation
    private val prevCpuTicks = mutableMapOf<Int, Long>()
    private var lastPollTime = 0L

    private fun parseStat(text: String): StatFields? {
        return try {
            val firstParen = text.indexOf('(')
            val lastParen = text.lastIndexOf(')')
            if (firstParen == -1 || lastParen == -1 || lastParen <= firstParen) return null

            val pid = text.substring(0, firstParen).trim().toIntOrNull() ?: return null
            val comm = text.substring(firstParen + 1, lastParen)
            val rest = text.substring(lastParen + 1).trim().split("\\s+".toRegex())

            StatFields(
                pid = pid,
                comm = comm,
                state = rest.getOrNull(0)?.firstOrNull()?.toString() ?: "?",
                utime = rest.getOrNull(11)?.toLongOrNull() ?: 0L,
                stime = rest.getOrNull(12)?.toLongOrNull() ?: 0L,
                priority = rest.getOrNull(15)?.toIntOrNull() ?: 0,
                nice = rest.getOrNull(16)?.toIntOrNull() ?: 0,
                rssPages = rest.getOrNull(21)?.toLongOrNull() ?: 0L
            )
        } catch (_: Exception) { null }
    }

    /** Read a single /proc/[pid]/stat file, trying direct read then exec fallback. */
    private fun readProcStat(pid: Int): String? {
        // Direct file read
        try {
            return File("/proc/$pid/stat").readText().trim()
        } catch (_: Exception) { }

        // Runtime.exec fallback — subprocess inherits our SELinux context
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("cat", "/proc/$pid/stat"))
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (text.isNotEmpty() && text[0].isDigit()) return text
        } catch (_: Exception) { }

        return null
    }

    /** Read a single /proc/[pid]/status line, e.g. "Name:" or "Threads:" */
    private fun readStatusLine(pid: Int, prefix: String): String? {
        try {
            val lines = File("/proc/$pid/status").readLines()
            return lines.firstOrNull { it.startsWith(prefix) }?.substringAfter(":")?.trim()
        } catch (_: Exception) { }
        return null
    }

    private fun readComm(pid: Int): String? {
        try { return File("/proc/$pid/comm").readText().trim() } catch (_: Exception) { }
        return null
    }

    /** Map a PID to a package name via ActivityManager. */
    private fun pidToPackage(pid: Int): String {
        try {
            val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return ""
            val procs = am.runningAppProcesses ?: return ""
            return procs.firstOrNull { it.pid == pid }?.processName ?: ""
        } catch (_: Exception) { return "" }
    }

    suspend fun getProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableListOf<ProcessInfo>()
        val now = System.currentTimeMillis()
        val elapsedSec = if (lastPollTime > 0) (now - lastPollTime) / 1000f else 1f

        // List all PIDs from /proc/
        val allPids = try {
            File("/proc").listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } }
                ?.map { it.name.toInt() }?.sorted() ?: emptyList()
        } catch (_: Exception) { emptyList() }

        // Always include our own process first
        val ourPid = Process.myPid()
        val ourUid = Process.myUid()

        // Read our own process
        try {
            val statText = File("/proc/self/stat").readText().trim()
            val fields = parseStat(statText)
            val statusLines = try { File("/proc/self/status").readLines() } catch (_: Exception) { emptyList() }
            val threads = statusLines.firstOrNull { it.startsWith("Threads:") }
                ?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull() ?: 1
            val name = statusLines.firstOrNull { it.startsWith("Name:") }
                ?.substringAfter(":")?.trim() ?: fields?.comm ?: "procinsight"

            val totalCpu = (fields?.utime ?: 0L) + (fields?.stime ?: 0L)
            val prevTicks = prevCpuTicks[ourPid] ?: 0L
            val cpuPercent = if (prevTicks > 0 && elapsedSec > 0) {
                ((totalCpu - prevTicks).toFloat() / elapsedSec / 100f).coerceIn(0f, 100f)
            } else 0f
            prevCpuTicks[ourPid] = totalCpu

            processes.add(ProcessInfo(
                pid = ourPid, name = name, packageName = context?.packageName ?: "",
                cpuPercent = cpuPercent,
                userCpu = fields?.utime ?: 0,
                kernelCpu = fields?.stime ?: 0,
                totalCpu = totalCpu,
                threads = threads,
                memoryRss = (fields?.rssPages ?: 0L) * 4L,
                memoryPss = null,
                priority = fields?.priority ?: 0, nice = fields?.nice ?: 0,
                state = fields?.state ?: "?",
                foreground = true, wakeups = 0, uid = ourUid
            ))
        } catch (_: Exception) { }

        // Try to read OTHER processes via /proc/[pid]/stat
        val targetPids = allPids.filter { it != ourPid && it > 0 }
        for (pid in targetPids) {
            try {
                val statText = readProcStat(pid) ?: continue
                val fields = parseStat(statText) ?: continue

                val totalCpu = fields.utime + fields.stime
                val prevTicks = prevCpuTicks[pid] ?: 0L
                val cpuPercent = if (prevTicks > 0 && elapsedSec > 0) {
                    ((totalCpu - prevTicks).toFloat() / elapsedSec / 100f).coerceIn(0f, 100f)
                } else 0f
                prevCpuTicks[pid] = totalCpu

                val comm = readComm(pid) ?: fields.comm
                val threads = readStatusLine(pid, "Threads:")?.toIntOrNull() ?: 0
                val name = readStatusLine(pid, "Name:") ?: comm
                val pkg = pidToPackage(pid)

                processes.add(ProcessInfo(
                    pid = pid, name = name, packageName = pkg,
                    cpuPercent = cpuPercent,
                    userCpu = fields.utime, kernelCpu = fields.stime,
                    totalCpu = totalCpu,
                    threads = threads,
                    memoryRss = fields.rssPages * 4L,
                    memoryPss = null,
                    priority = fields.priority, nice = fields.nice,
                    state = fields.state,
                    foreground = false, wakeups = 0, uid = -1
                ))
            } catch (_: Exception) { }
        }

        // Also try ActivityManager for additional process names
        try {
            val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val runningApps = am?.runningAppProcesses ?: emptyList()
            for (app in runningApps) {
                if (processes.any { it.pid == app.pid }) {
                    // Update package name for already-added process
                    val idx = processes.indexOfFirst { it.pid == app.pid }
                    if (idx >= 0 && processes[idx].packageName.isEmpty()) {
                        processes[idx] = processes[idx].copy(
                            packageName = app.processName,
                            uid = app.uid,
                            foreground = app.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
                        )
                    }
                } else {
                    // Add from ActivityManager if we couldn't read /proc
                    processes.add(ProcessInfo(
                        pid = app.pid, name = app.processName ?: "unknown",
                        packageName = app.processName ?: "",
                        cpuPercent = 0f, userCpu = 0, kernelCpu = 0, totalCpu = 0,
                        threads = 0, memoryRss = 0, memoryPss = null,
                        priority = 0, nice = 0,
                        state = if (app.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) "R" else "S",
                        foreground = app.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                        wakeups = 0, uid = app.uid
                    ))
                }
            }
        } catch (_: Exception) { }

        lastPollTime = now
        processes.sortedByDescending { it.cpuPercent }
    }
}
