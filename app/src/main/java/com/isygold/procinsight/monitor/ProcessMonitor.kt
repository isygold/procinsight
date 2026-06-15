package com.isygold.procinsight.monitor

import com.isygold.procinsight.data.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProcessMonitor {

    suspend fun getProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableListOf<ProcessInfo>()

        try {
            val pb = ProcessBuilder("sh", "-c", "ps -A -o pid,pcpu,rss,pri,ni,s,comm 2>/dev/null")
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            val lines = output.lines()
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isBlank()) continue

                val parts = line.split("\\s+".toRegex())
                if (parts.size < 7) continue

                val pid = parts[0].toIntOrNull() ?: continue
                if (pid <= 0) continue

                val cpuPercent = (parts[1].toFloatOrNull() ?: 0f).coerceIn(0f, 100f)
                val rssKb = parts[2].toLongOrNull() ?: 0L
                val priority = parts[3].toIntOrNull() ?: 0
                val nice = parts[4].toIntOrNull() ?: 0
                val stateChar = parts[5].firstOrNull()?.toString() ?: "?"
                val name = parts.drop(6).joinToString(" ").trim()

                if (name.isBlank() || name == "ps" || name == "sh") continue

                processes.add(ProcessInfo(
                    pid = pid,
                    name = name,
                    packageName = "",
                    cpuPercent = cpuPercent,
                    userCpu = 0,
                    kernelCpu = 0,
                    totalCpu = 0,
                    threads = 0,
                    memoryRss = rssKb,
                    memoryPss = null,
                    priority = priority,
                    nice = nice,
                    state = stateChar,
                    foreground = false,
                    lastActivityTime = 0L,
                    wakeups = 0,
                    uid = -1
                ))
            }
        } catch (_: Exception) { }

        processes.sortedByDescending { it.cpuPercent }
    }
}
