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

    /**
     * Try to read /proc/stat using every available method.
     * Returns parsed lines on success, empty list on total failure.
     */
    private fun readProcStat(): List<String> {
        // Layer 1: direct File read
        try {
            val lines = File("/proc/stat").readLines()
            if (lines.isNotEmpty()) {
                val cpuLines = lines.filter { it.startsWith("cpu") && it.length > 3 }
                if (cpuLines.isNotEmpty()) return cpuLines
            }
        } catch (_: Exception) { }

        // Layer 2: Runtime.exec("cat /proc/stat") — works on some MIUI where direct File fails
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("cat", "/proc/stat"))
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (text.isNotEmpty()) {
                val cpuLines = text.lines().filter { it.startsWith("cpu") && it.length > 3 }
                if (cpuLines.isNotEmpty()) return cpuLines
            }
        } catch (_: Exception) { }

        // Layer 3: Runtime.exec with "sh" to handle SELinux context differences
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat /proc/stat"))
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (text.isNotEmpty()) {
                val cpuLines = text.lines().filter { it.startsWith("cpu") && it.length > 3 }
                if (cpuLines.isNotEmpty()) return cpuLines
            }
        } catch (_: Exception) { }

        // Layer 4: try via Shizuku if available
        try {
            val result = ShizukuManager.execute("cat /proc/stat")
            if (result != null && result.isNotEmpty()) {
                val cpuLines = result.lines().filter { it.startsWith("cpu") && it.length > 3 }
                if (cpuLines.isNotEmpty()) return cpuLines
            }
        } catch (_: Exception) { }

        return emptyList()
    }

    suspend fun getCpuStats(): List<CpuCoreInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cores = mutableListOf<CpuCoreInfo>()
        val cpuDir = File("/sys/devices/system/cpu")

        val statLines = readProcStat()

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
        }

        if (cores.isEmpty()) {
            // Total fallback: count cores from sysfs, 0% usage
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
        val present = try {
            File(cpuDir, "present").readText().trim()
        } catch (_: Exception) { null }
        if (present != null) {
            val parts = present.split("-")
            if (parts.size == 2) {
                val start = parts[0].toIntOrNull()
                val end = parts[1].toIntOrNull()
                if (start != null && end != null) return end - start + 1
            }
        }
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
