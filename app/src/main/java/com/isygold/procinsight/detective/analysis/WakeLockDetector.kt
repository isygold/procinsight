package com.isygold.procinsight.detective.analysis

import com.isygold.procinsight.detective.diagnosis.Severity
import com.isygold.procinsight.detective.session.DataPoint

/**
 * Detects apps that hold wake locks aggressively.
 */
class WakeLockDetector {

    data class WakeLockAbuseResult(
        val packageName: String,
        val wakeLockName: String,
        val uid: Int,
        val totalHeldDurationMs: Long,
        val heldPercentage: Float,
        val isActive: Boolean,
        val severity: Severity
    )

    fun detect(points: List<DataPoint>): List<WakeLockAbuseResult> {
        if (points.size < 5) return emptyList()

        val sessionDurationMs = if (points.size > 1)
            points.last().timestampMs - points.first().timestampMs else 60_000L

        if (sessionDurationMs <= 0) return emptyList()

        // Track wakelocks by name across all data points
        val wlByName = mutableMapOf<String, WakeLockTracker>()
        for (point in points) {
            for (wl in point.wakeLocks) {
                val tracker = wlByName.getOrPut(wl.name) { WakeLockTracker(wl.name, wl.uid) }
                tracker.count++
                tracker.totalTimeMs = maxOf(tracker.totalTimeMs, wl.totalTimeMs)
                if (wl.isHeld) tracker.heldCount++
            }
        }

        val results = mutableListOf<WakeLockAbuseResult>()
        for ((_, tracker) in wlByName) {
            val heldPercentage = if (points.isNotEmpty())
                (tracker.heldCount.toFloat() / points.size) * 100f else 0f

            // Abuse threshold: held > 25% of session or total time > 3 minutes
            if (tracker.totalTimeMs > 180_000L || heldPercentage > 25f) {
                val severity = when {
                    tracker.totalTimeMs > 600_000L || heldPercentage > 60f -> Severity.CRITICAL
                    tracker.totalTimeMs > 300_000L || heldPercentage > 40f -> Severity.HIGH
                    tracker.totalTimeMs > 180_000L || heldPercentage > 25f -> Severity.MEDIUM
                    else -> Severity.LOW
                }
                results.add(
                    WakeLockAbuseResult(
                        packageName = resolvePackageFromUid(tracker.uid),
                        wakeLockName = tracker.name,
                        uid = tracker.uid,
                        totalHeldDurationMs = tracker.totalTimeMs,
                        heldPercentage = heldPercentage,
                        isActive = tracker.heldCount == points.size,
                        severity = severity
                    )
                )
            }
        }

        return results.sortedByDescending { it.totalHeldDurationMs }
    }

    private fun resolvePackageFromUid(uid: Int): String {
        return try {
            val pkgFile = java.io.File("/data/system/packages.list")
            if (pkgFile.exists()) {
                pkgFile.useLines { lines ->
                    lines.firstOrNull { line ->
                        val parts = line.split(" ")
                        parts.size > 1 && parts[1].toIntOrNull() == uid
                    }?.split(" ")?.firstOrNull() ?: "uid:$uid"
                }
            } else "uid:$uid"
        } catch (_: Exception) { "uid:$uid" }
    }

    private class WakeLockTracker(val name: String, val uid: Int) {
        var count = 0
        var totalTimeMs = 0L
        var heldCount = 0
    }
}
