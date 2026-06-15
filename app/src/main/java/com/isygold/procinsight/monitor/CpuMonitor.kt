package com.isygold.procinsight.monitor

import com.isygold.procinsight.data.CpuCoreInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CpuMonitor {

    private val snapshots = mutableMapOf<Int, PreviousCoreStat>()
    private var lastTime = 0L

    private data class PreviousCoreStat(
        val total: Long, val idle: Long
    )

    suspend fun getCpuStats(): List<CpuCoreInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cores = mutableListOf<CpuCoreInfo>()
        val cpuDir = File("/sys/devices/system/cpu")

        val statLines = try {
            File("/proc/stat").readLines().filter { it.startsWith("cpu") && it.length > 3 }
        } catch (_: Exception) { emptyList() }

        for (line in statLines) {
            try {
                val parts = line.split("\\s+".toRegex())
                val coreName = parts[0]
                val coreNum = coreName.removePrefix("cpu").toIntOrNull() ?: continue

                val user = parts.getOrNull(1)?.toLongOrNull() ?: 0
                val nice = parts.getOrNull(2)?.toLongOrNull() ?: 0
                val system = parts.getOrNull(3)?.toLongOrNull() ?: 0
                val idle = parts.getOrNull(4)?.toLongOrNull() ?: 0
                val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0
                val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0
                val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0
                val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0
                val total = user + nice + system + idle + iowait + irq + softirq + steal
                val active = total - idle

                val prev = snapshots[coreNum]
                val usagePercent = if (prev != null && total > prev.total) {
                    val deltaTotal = total - prev.total
                    val deltaIdle = idle - prev.idle
                    if (deltaTotal > 0) ((deltaTotal - deltaIdle).toFloat() / deltaTotal * 100).coerceIn(0f, 100f) else 0f
                } else 0f

                snapshots[coreNum] = PreviousCoreStat(total, idle)

                val cpuPath = "$cpuDir/cpu$coreNum"
                val freq = readInt("$cpuPath/cpufreq/scaling_cur_freq") ?: 0
                val gov = readString("$cpuPath/cpufreq/scaling_governor") ?: "unknown"
                val online = readInt("$cpuPath/online") ?: 1
                val minFreq = readInt("$cpuPath/cpufreq/cpuinfo_min_freq") ?: 0
                val maxFreq = readInt("$cpuPath/cpufreq/cpuinfo_max_freq") ?: 0

                cores.add(
                    CpuCoreInfo(
                        core = coreNum,
                        user = user, nice = nice, system = system,
                        idle = idle, iowait = iowait, irq = irq,
                        softirq = softirq, steal = steal,
                        total = total, active = active,
                        usagePercent = usagePercent,
                        frequencyKhz = freq,
                        governor = gov,
                        online = online == 1,
                        minFreq = minFreq,
                        maxFreq = maxFreq
                    )
                )
            } catch (_: Exception) { }
        }

        lastTime = now
        cores.sortedBy { it.core }
    }

    private fun readInt(path: String): Int? {
        return try { File(path).readText().trim().toIntOrNull() } catch (_: Exception) { null }
    }

    private fun readString(path: String): String? {
        return try { File(path).readText().trim() } catch (_: Exception) { null }
    }
}
