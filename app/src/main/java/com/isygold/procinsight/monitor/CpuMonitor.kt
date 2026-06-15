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

        // Read per-core lines from /proc/stat
        val statLines = try {
            File("/proc/stat").readLines().filter { it.startsWith("cpu") && it.length > 3 }
        } catch (_: Exception) { emptyList() }

        if (statLines.isNotEmpty()) {
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
        } else {
            // Fallback: /proc/stat not readable (SELinux block on MIUI).
            // Count CPU cores from sysfs and return zero-filled entries.
            val coreCount = countCpuCores(cpuDir)
            for (i in 0 until coreCount) {
                val cpuPath = "$cpuDir/cpu$i"
                val freq = readInt("$cpuPath/cpufreq/scaling_cur_freq") ?: 0
                val gov = readString("$cpuPath/cpufreq/scaling_governor") ?: "unknown"
                val online = readInt("$cpuPath/online") ?: 1

                cores.add(
                    CpuCoreInfo(
                        core = i,
                        user = 0, nice = 0, system = 0,
                        idle = 0, iowait = 0, irq = 0,
                        softirq = 0, steal = 0,
                        total = 0, active = 0,
                        usagePercent = 0f,
                        frequencyKhz = freq,
                        governor = gov,
                        online = online == 1,
                        minFreq = readInt("$cpuPath/cpufreq/cpuinfo_min_freq") ?: 0,
                        maxFreq = readInt("$cpuPath/cpufreq/cpuinfo_max_freq") ?: 0
                    )
                )
            }
        }

        lastTime = now
        cores.sortedBy { it.core }
    }

    private fun countCpuCores(cpuDir: File): Int {
        // Try /sys/devices/system/cpu/present first
        val present = try {
            File(cpuDir, "present").readText().trim()
        } catch (_: Exception) { null }
        if (present != null) {
            // Format: "0-7"
            val parts = present.split("-")
            if (parts.size == 2) {
                val start = parts[0].toIntOrNull()
                val end = parts[1].toIntOrNull()
                if (start != null && end != null) return end - start + 1
            }
        }

        // Count cpuN directories as fallback
        return try {
            cpuDir.listFiles { f -> f.isDirectory && f.name.startsWith("cpu") && f.name.length > 3 && f.name[3].isDigit() }?.size ?: 4
        } catch (_: Exception) { 4 }
    }

    private fun readInt(path: String): Int? {
        return try { File(path).readText().trim().toIntOrNull() } catch (_: Exception) { null }
    }

    private fun readString(path: String): String? {
        return try { File(path).readText().trim() } catch (_: Exception) { null }
    }
}
