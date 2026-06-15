package com.isygold.procinsight.detective.analysis

import com.isygold.procinsight.detective.diagnosis.Severity
import com.isygold.procinsight.detective.session.DataPoint

/**
 * Detects apps that fire excessive alarms.
 */
class AlarmSpamDetector {

    data class AlarmSpamResult(
        val packageName: String,
        val uid: Int,
        val totalAlarms: Int,
        val exactCount: Int,
        val idleCount: Int,
        val interval: Long,
        val severity: Severity
    )

    fun detect(points: List<DataPoint>): List<AlarmSpamResult> {
        if (points.isEmpty()) return emptyList()

        val alarmCounts = mutableMapOf<String, AlarmTracker>()

        for (point in points) {
            for (alarm in point.alarms) {
                val tracker = alarmCounts.getOrPut(alarm.packageName) {
                    AlarmTracker(alarm.packageName, alarm.uid)
                }
                tracker.totalCount += alarm.count
                if (alarm.isExact) tracker.exactCount += alarm.count
                if (alarm.isWhileIdle) tracker.idleCount += alarm.count
                tracker.interval = maxOf(tracker.interval, alarm.interval)
            }
        }

        val results = mutableListOf<AlarmSpamResult>()
        val sessionMinutes = if (points.size > 1) {
            (points.last().timestampMs - points.first().timestampMs) / 60_000f
        } else 15f

        for ((_, tracker) in alarmCounts) {
            val alarmsPerMinute = if (sessionMinutes > 0) tracker.totalCount / sessionMinutes else 0f

            // Spam threshold: >5 alarms/minute (75+ in 15 min)
            if (tracker.totalCount > 75 && alarmsPerMinute > 5f) {
                val severity = when {
                    tracker.totalCount > 500 || alarmsPerMinute > 50f -> Severity.CRITICAL
                    tracker.totalCount > 200 || alarmsPerMinute > 20f -> Severity.HIGH
                    tracker.totalCount > 75 || alarmsPerMinute > 5f -> Severity.MEDIUM
                    else -> Severity.LOW
                }
                results.add(
                    AlarmSpamResult(
                        packageName = tracker.pkg,
                        uid = tracker.uid,
                        totalAlarms = tracker.totalCount,
                        exactCount = tracker.exactCount,
                        idleCount = tracker.idleCount,
                        interval = tracker.interval,
                        severity = severity
                    )
                )
            }
        }

        return results.sortedByDescending { it.totalAlarms }
    }

    private class AlarmTracker(val pkg: String, val uid: Int) {
        var totalCount = 0
        var exactCount = 0
        var idleCount = 0
        var interval = 0L
    }
}
