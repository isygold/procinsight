package com.isygold.procinsight.monitor

import com.isygold.procinsight.data.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdvancedProcessMonitor {

    private val psCommands = listOf(
        "ps -A -o pid,pcpu,rss,pri,ni,s,comm 2>/dev/null",
        "ps -o pid,pcpu,rss,pri,ni,s,comm 2>/dev/null",
        "ps -A -o pid,%cpu,rss,pri,ni,s,comm 2>/dev/null",
        "ps -o pid,%cpu,rss,pri,ni,s,comm 2>/dev/null",
        "ps -A -o pid,pcpu,rss,priority,nice,stat,comm 2>/dev/null",
        "ps -o pid,pcpu,rss,priority,nice,stat,comm 2>/dev/null",
        "top -b -n 1 -o pid,%cpu,rss,pri,ni,s,comm 2>/dev/null",
    )

    suspend fun getProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableListOf<ProcessInfo>()

        // Try each command variant until one produces output
        var output: String? = null
        for (cmd in psCommands) {
            val result = ShizukuManager.execute(cmd)
            if (result != null && result.lines().size > 1) {
                output = result
                break
            }
        }

        if (output == null) return@withContext processes

        val lines = output.lines()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue

            val parts = line.split("\\s+".toRegex())
            // Accept 6+ parts (some commands may omit state)
            if (parts.size < 6) continue

            val pid = parts[0].toIntOrNull() ?: continue
            if (pid <= 0) continue

            // Parse CPU% — may be "0.0" or "0" or "?"
            val cpuStr = parts[1].trim('?', '%')
            val cpuPercent = (cpuStr.toFloatOrNull() ?: 0f).coerceIn(0f, 100f)

            val rssKb = parts[2].toLongOrNull() ?: 0L

            // Priority, nice, state vary across formats
            val priority = parts.getOrNull(3)?.toIntOrNull() ?: 0
            val nice = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val stateChar = parts.getOrNull(5)?.firstOrNull()?.toString() ?: "?"
            val name = parts.drop(6).joinToString(" ").trim()
            val cleanName = name.trimEnd('}').trimStart('{').takeIf { it.isNotEmpty() } ?: name

            if (cleanName.isBlank() || cleanName == "ps" || cleanName == "sh" || cleanName == "top") continue

            processes.add(ProcessInfo(
                pid = pid, name = cleanName, packageName = "",
                cpuPercent = cpuPercent, userCpu = 0, kernelCpu = 0, totalCpu = 0,
                threads = 0, memoryRss = rssKb, memoryPss = null,
                priority = priority, nice = nice, state = stateChar,
                foreground = false, wakeups = 0, uid = -1
            ))
        }

        processes.sortedByDescending { it.cpuPercent }
    }
}
