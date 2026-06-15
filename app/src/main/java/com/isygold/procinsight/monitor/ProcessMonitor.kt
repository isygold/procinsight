package com.isygold.procinsight.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.isygold.procinsight.data.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProcessMonitor(private val context: Context? = null) {

    /** Parse /proc/self/stat fields. The comm field (index 1) is in parentheses
     *  and may contain spaces, so we find it by locating the first '(' and last ')'. */
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

    suspend fun getProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableListOf<ProcessInfo>()

        try {
            val pid = Process.myPid()
            val uid = Process.myUid()
            val comm = File("/proc/self/comm").readText().trim()
            val statText = File("/proc/self/stat").readText().trim()
            val fields = parseStat(statText)

            val statusLines = File("/proc/self/status").readLines()
            val threads = statusLines.firstOrNull { it.startsWith("Threads:") }
                ?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull() ?: 1
            val name = statusLines.firstOrNull { it.startsWith("Name:") }
                ?.substringAfter(":")?.trim() ?: comm

            processes.add(ProcessInfo(
                pid = pid, name = name, packageName = context?.packageName ?: "",
                cpuPercent = 0f,
                userCpu = fields?.utime ?: 0,
                kernelCpu = fields?.stime ?: 0,
                totalCpu = (fields?.utime ?: 0L) + (fields?.stime ?: 0L),
                threads = threads, memoryRss = (fields?.rssPages ?: 0L) * 4L,
                memoryPss = null,
                priority = fields?.priority ?: 0, nice = fields?.nice ?: 0,
                state = fields?.state ?: "?",
                foreground = true, wakeups = 0, uid = uid
            ))
        } catch (_: Exception) { }

        try {
            val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val runningApps = am?.runningAppProcesses ?: emptyList()
            for (app in runningApps) {
                if (processes.any { it.pid == app.pid }) continue
                processes.add(ProcessInfo(
                    pid = app.pid, name = app.processName ?: "unknown",
                    packageName = "",
                    cpuPercent = 0f, userCpu = 0, kernelCpu = 0, totalCpu = 0,
                    threads = 0, memoryRss = 0, memoryPss = null,
                    priority = 0, nice = 0,
                    state = if (app.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) "R" else "S",
                    foreground = app.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    wakeups = 0, uid = app.uid
                ))
            }
        } catch (_: Exception) { }

        processes.sortedByDescending { it.cpuPercent }
    }
}
