package com.isygold.procinsight.detective.analysis

import com.isygold.procinsight.detective.diagnosis.DrainOffense
import com.isygold.procinsight.detective.diagnosis.Severity
import com.isygold.procinsight.detective.session.DataPoint

/**
 * Analyzes a profiling session for offenders that aggressively use CPU cores.
 */
class CpuHogDetector {

    data class CpuHogResult(
        val packageName: String,
        val processName: String,
        val pid: Int,
        val avgCpuPercent: Float,
        val maxCpuPercent: Float,
        val sustainedDurationMs: Long,
        val severity: Severity
    )

    /**
     * Find the top CPU-consuming process across the session.
     * A "hog" must sustain >15% CPU for at least 2 minutes.
     */
    fun detect(points: List<DataPoint>): List<CpuHogResult> {
        if (points.size < 10) return emptyList()

        val sampleInterval = if (points.size > 1)
            points[1].timestampMs - points[0].timestampMs else 5000L

        // Track CPU per process across all data points
        val cpuByProcess = mutableMapOf<Int, MutableList<Float>>() // pid -> list of cpu%
        val nameByPid = mutableMapOf<Int, Pair<String, String>>() // pid -> (name, package)

        for (point in points) {
            for (proc in point.processes) {
                cpuByProcess.getOrPut(proc.pid) { mutableListOf() }.add(proc.cpuPercent)
                nameByPid[proc.pid] = proc.name to proc.packageName
            }
        }

        val results = mutableListOf<CpuHogResult>()
        val requiredSustainedSamples = (120_000L / sampleInterval).toInt().coerceAtLeast(5)

        for ((pid, cpus) in cpuByProcess) {
            if (cpus.size < requiredSustainedSamples) continue

            val avg = cpus.average().toFloat()
            val max = cpus.maxOrNull() ?: 0f

            // Count sustained samples above threshold (15%)
            val sustained = cpus.count { it > 15f }
            val sustainedDuration = sustained * sampleInterval

            if (avg > 10f && sustainedDuration > 120_000L) {
                val (name, pkg) = nameByPid[pid] ?: "unknown" to "unknown"
                val severity = when {
                    avg > 40f -> Severity.CRITICAL
                    avg > 25f -> Severity.HIGH
                    avg > 15f -> Severity.MEDIUM
                    else -> Severity.LOW
                }
                results.add(
                    CpuHogResult(
                        packageName = pkg,
                        processName = name,
                        pid = pid,
                        avgCpuPercent = avg,
                        maxCpuPercent = max,
                        sustainedDurationMs = sustainedDuration,
                        severity = severity
                    )
                )
            }
        }

        return results.sortedByDescending { it.avgCpuPercent }
    }
}
