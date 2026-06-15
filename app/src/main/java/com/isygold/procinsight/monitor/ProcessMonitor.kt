package com.isygold.procinsight.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.isygold.procinsight.data.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProcessMonitor(private val context: Context? = null) {

    suspend fun getProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableListOf<ProcessInfo>()

        try {
            val pid = Process.myPid()
            val uid = Process.myUid()
            val comm = File("/proc/self/comm").readText().trim()
            val stat = File("/proc/self/stat").readText().trim()
            val parts = stat.split(" ")
            val state = parts.getOrNull(2)?.firstOrNull()?.toString() ?: "?"
            val priority = parts.getOrNull(17)?.toIntOrNull() ?: 0
            val nice = parts.getOrNull(18)?.toIntOrNull() ?: 0
            val rssKb = (parts.getOrNull(23)?.toLongOrNull() ?: 0L) * 4L

            val statusLines = File("/proc/self/status").readLines()
            val threads = statusLines.firstOrNull { it.startsWith("Threads:") }
                ?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull() ?: 1
            val name = statusLines.firstOrNull { it.startsWith("Name:") }
                ?.substringAfter(":")?.trim() ?: comm

            processes.add(ProcessInfo(
                pid = pid, name = name, packageName = context?.packageName ?: "",
                cpuPercent = 0f, userCpu = 0, kernelCpu = 0, totalCpu = 0,
                threads = threads, memoryRss = rssKb, memoryPss = null,
                priority = priority, nice = nice, state = state,
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
