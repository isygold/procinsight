package com.isygold.procinsight.detective.analysis

import com.isygold.procinsight.detective.session.DataPoint

/**
 * Cross-references findings from all detectors to identify the primary culprit
 * and correlate patterns (e.g., an app that both hogs CPU and holds wake locks).
 */
class Correlator {

    data class CorrelatedCulprit(
        val packageName: String,
        val displayName: String,
        val totalScore: Int,
        val cpuHog: CpuHogDetector.CpuHogResult?,
        val wakeLockAbuse: WakeLockDetector.WakeLockAbuseResult?,
        val alarmSpam: AlarmSpamDetector.AlarmSpamResult?,
        val primaryOffense: String,
        val uid: Int
    )

    fun correlate(
        cpuHogs: List<CpuHogDetector.CpuHogResult>,
        wakeLocks: List<WakeLockDetector.WakeLockAbuseResult>,
        alarms: List<AlarmSpamDetector.AlarmSpamResult>,
        points: List<DataPoint>
    ): List<CorrelatedCulprit> {
        val map = mutableMapOf<String, CulpritBuilder>()

        // Score CPU hogs (weight: 40)
        for (hog in cpuHogs) {
            val pkg = hog.packageName
            val b = map.getOrPut(pkg) { CulpritBuilder(pkg, hog.processName, hog.pid) }
            b.cpuHog = hog
            b.score += (hog.avgCpuPercent * 0.4f).toInt()
            b.uid = resolveUid(pkg)
        }

        // Score wake lock abuse (weight: 30)
        for (wl in wakeLocks) {
            val pkg = wl.packageName
            val b = map.getOrPut(pkg) { CulpritBuilder(pkg, wl.packageName, -1) }
            b.wakeLockAbuse = wl
            b.score += (wl.heldPercentage * 0.3f).toInt()
            b.uid = wl.uid
        }

        // Score alarm spam (weight: 20)
        for (a in alarms) {
            val pkg = a.packageName
            val b = map.getOrPut(pkg) { CulpritBuilder(pkg, pkg, -1) }
            b.alarmSpam = a
            b.score += (a.totalAlarms * 0.03f).toInt().coerceAtMost(100)
            b.uid = a.uid
        }

        return map.values.map { it.build() }.sortedByDescending { it.totalScore }
    }

    private fun resolveUid(pkg: String): Int {
        return try {
            val pkgFile = java.io.File("/data/system/packages.list")
            if (pkgFile.exists()) {
                pkgFile.useLines { lines ->
                    lines.firstOrNull { it.startsWith(pkg) }
                        ?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: -1
                }
            } else -1
        } catch (_: Exception) { -1 }
    }

    private class CulpritBuilder(
        val packageName: String,
        var displayName: String,
        var pid: Int
    ) {
        var score = 0
        var cpuHog: CpuHogDetector.CpuHogResult? = null
        var wakeLockAbuse: WakeLockDetector.WakeLockAbuseResult? = null
        var alarmSpam: AlarmSpamDetector.AlarmSpamResult? = null
        var uid = -1

        fun build(): CorrelatedCulprit {
            val offenses = mutableListOf<String>()
            if (cpuHog != null) offenses.add("CPU Hog")
            if (wakeLockAbuse != null) offenses.add("Wake Lock")
            if (alarmSpam != null) offenses.add("Alarm Spam")

            return CorrelatedCulprit(
                packageName = packageName,
                displayName = displayName,
                totalScore = score,
                cpuHog = cpuHog,
                wakeLockAbuse = wakeLockAbuse,
                alarmSpam = alarmSpam,
                primaryOffense = offenses.firstOrNull() ?: "Unknown",
                uid = uid
            )
        }
    }
}
