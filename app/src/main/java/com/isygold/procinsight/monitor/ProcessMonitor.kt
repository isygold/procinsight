package com.isygold.procinsight.monitor

import android.os.Debug
import com.isygold.procinsight.data.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class ProcessMonitor {

    private val procDir = File("/proc")
    private val snapshots = mutableMapOf<Int, CpuSnapshot>()
    private var lastSnapshotTime = 0L

    private data class CpuSnapshot(
        val utime: Long,
        val stime: Long,
        val totalTime: Long
    )

    suspend fun getProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val elapsed = if (lastSnapshotTime > 0) (now - lastSnapshotTime).coerceAtLeast(1) else 1000L
        val totalCpuTime = readTotalCpuTime()

        val processes = mutableListOf<ProcessInfo>()
        val dirs = procDir.listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } } ?: return@withContext emptyList()

        for (dir in dirs) {
            try {
                val pid = dir.name.toInt()
                val stat = parseStat(pid) ?: continue
                val status = parseStatus(pid)
                val cmdline = readCmdline(pid)
                val name = cmdline ?: status?.get("Name")?.removeSurrounding("(", ")") ?: "unknown"
                val uid = status?.get("Uid")?.split("\\s+".toRegex())?.firstOrNull()?.toIntOrNull() ?: -1
                val threads = status?.get("Threads")?.toIntOrNull() ?: 0
                val memoryRss = (stat.vmRssKB)
                val state = stat.state
                val priority = stat.priority
                val nice = stat.nice

                val prev = snapshots[pid]
                val cpuDelta = if (prev != null && totalCpuTime > prev.totalTime) {
                    val procDelta = (stat.utime + stat.stime) - (prev.utime + prev.stime)
                    val sysDelta = totalCpuTime - prev.totalTime
                    if (sysDelta > 0) (procDelta.toFloat() / sysDelta * 100).coerceIn(0f, 100f) else 0f
                } else 0f

                snapshots[pid] = CpuSnapshot(stat.utime, stat.stime, totalCpuTime)

                processes.add(
                    ProcessInfo(
                        pid = pid,
                        name = name,
                        packageName = resolvePackageName(uid),
                        cpuPercent = cpuDelta,
                        userCpu = stat.utime,
                        kernelCpu = stat.stime,
                        totalCpu = stat.utime + stat.stime,
                        threads = threads,
                        memoryRss = memoryRss,
                        memoryPss = try { Debug.getPss() / 1024L } catch (_: Exception) { null },
                        priority = priority,
                        nice = nice,
                        state = state,
                        foreground = false,
                        lastActivityTime = 0L,
                        wakeups = 0,
                        uid = uid
                    )
                )
            } catch (_: Exception) { }
        }

        lastSnapshotTime = now
        processes.sortedByDescending { it.cpuPercent }
    }

    private fun readTotalCpuTime(): Long {
        return try {
            val line = File("/proc/stat").readLines().firstOrNull() ?: return 0
            val parts = line.split("\\s+".toRegex())
            if (parts.size > 5) {
                parts.drop(1).sumOf { it.toLongOrNull() ?: 0L }
            } else 0L
        } catch (_: Exception) { 0L }
    }

    private data class StatData(
        val utime: Long, val stime: Long, val vmRssKB: Long,
        val priority: Int, val nice: Int, val state: String
    )

    private fun parseStat(pid: Int): StatData? {
        return try {
            val content = File("/proc/$pid/stat").readText()
            val parts = content.split(" ")
            val state = parts[2]
            val utime = parts[13].toLongOrNull() ?: 0L
            val stime = parts[14].toLongOrNull() ?: 0L
            val vmRss = (parts[23].toLongOrNull() ?: 0L) * 4L
            val priority = parts[17].toIntOrNull() ?: 0
            val nice = parts[18].toIntOrNull() ?: 0
            StatData(utime, stime, vmRss, priority, nice, state)
        } catch (_: Exception) { null }
    }

    private fun parseStatus(pid: Int): Map<String, String>? {
        return try {
            val map = mutableMapOf<String, String>()
            File("/proc/$pid/status").forEachLine { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                }
            }
            map
        } catch (_: Exception) { null }
    }

    private fun readCmdline(pid: Int): String? {
        return try {
            val cmd = File("/proc/$pid/cmdline").readText().trimEnd('\u0000')
            if (cmd.isNotBlank()) cmd else null
        } catch (_: Exception) { null }
    }

    private fun resolvePackageName(uid: Int): String {
        return try {
            val pkgFile = File("/data/system/packages.list")
            pkgFile.useLines { lines ->
                lines.firstOrNull { line ->
                    val parts = line.split(" ")
                    parts.size > 1 && parts[1].toIntOrNull() == uid
                }?.split(" ")?.firstOrNull() ?: "android:$uid"
            }
        } catch (_: Exception) { "android:$uid" }
    }
}
