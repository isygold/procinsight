package com.isygold.procinsight.detective.diagnosis

import com.isygold.procinsight.detective.analysis.Correlator

/**
 * Generates human-readable fix suggestions based on analysis results.
 */
class FixSuggester {

    fun generate(culprit: Correlator.CorrelatedCulprit): DrainDiagnosis {
        val steps = mutableListOf<FixStep>()
        var stepOrder = 0
        val issues = mutableListOf<String>()

        // CPU Hog fixes
        culprit.cpuHog?.let { hog ->
            issues.add("Used ${"%.1f".format(hog.avgCpuPercent)}% CPU for ${hog.sustainedDurationMs / 1000}s")
            steps.add(
                FixStep(
                    order = ++stepOrder,
                    action = "Force stop ${culprit.displayName}",
                    detail = "This app consumed ${"%.1f".format(hog.avgCpuPercent)}% CPU on average. " +
                            "Force stopping will immediately free CPU resources.",
                    type = FixActionType.FORCE_STOP
                )
            )
            steps.add(
                FixStep(
                    order = ++stepOrder,
                    action = "Restrict background activity",
                    detail = "Settings → Apps → ${culprit.displayName} → Battery → Restricted. " +
                            "This prevents background CPU usage.",
                    type = FixActionType.SETTING
                )
            )
            if (hog.avgCpuPercent > 50) {
                steps.add(
                    FixStep(
                        order = ++stepOrder,
                        action = "Consider uninstalling or updating",
                        detail = "CPU usage above 50% suggests a bug or runaway process. " +
                                "Check for updates or consider alternatives.",
                        type = FixActionType.UNINSTALL
                    )
                )
            }
        }

        // Wake lock fixes
        culprit.wakeLockAbuse?.let { wl ->
            issues.add("Held wake lock for ${wl.totalHeldDurationMs / 1000}s (${"%.0f".format(wl.heldPercentage)}% of session)")
            steps.add(
                FixStep(
                    order = ++stepOrder,
                    action = "Revoke 'Ignore Battery Optimizations'",
                    detail = "Settings → Apps → ${culprit.displayName} → Battery → " +
                            "Disable 'Ignore battery optimizations'. This lets Android manage wake locks.",
                    type = FixActionType.SETTING
                )
            )
            steps.add(
                FixStep(
                    order = ++stepOrder,
                    action = "Disable background wakelock permission",
                    detail = "Settings → Apps → ${culprit.displayName} → Permissions → " +
                            "Revoke 'Wake lock' or 'Keep awake' if listed.",
                    type = FixActionType.SETTING
                )
            )
        }

        // Alarm spam fixes
        culprit.alarmSpam?.let { alarm ->
            issues.add("Fired ${alarm.totalAlarms} alarms in the session (${"%.0f".format(alarm.totalAlarms / (culprit.cpuHog?.let { it.sustainedDurationMs / 60000f } ?: 15f))}/min)")
            steps.add(
                FixStep(
                    order = ++stepOrder,
                    action = "Reduce refresh interval in app settings",
                    detail = "Open ${culprit.displayName} → Settings → Sync/Refresh → " +
                            "Set to longer interval (e.g., 30min instead of 5min).",
                    type = FixActionType.ADVICE
                )
            )
            if (alarm.exactCount > 50) {
                steps.add(
                    FixStep(
                        order = ++stepOrder,
                        action = "Disable exact alarms",
                        detail = "This app uses ${
                            alarm.exactCount
                        } exact (wake-up) alarms which prevent deep sleep. " +
                                "Restrict background activity to convert to inexact alarms.",
                        type = FixActionType.SETTING
                    )
                )
            }
        }

        // Generate summary
        val summary = if (culprit.cpuHog != null && culprit.wakeLockAbuse != null) {
            "${culprit.displayName} is both CPU-hungry and keeping the device awake"
        } else if (culprit.cpuHog != null) {
            "${culprit.displayName} is consuming excessive CPU in the background"
        } else if (culprit.wakeLockAbuse != null) {
            "${culprit.displayName} is preventing the device from sleeping"
        } else if (culprit.alarmSpam != null) {
            "${culprit.displayName} is waking the device excessively with alarms"
        } else {
            "No clear culprit identified"
        }

        // Battery drain estimate
        val drainEstimate = when {
            culprit.totalScore > 200 -> "Very High — significantly impacts battery life"
            culprit.totalScore > 100 -> "High — noticeably drains battery"
            culprit.totalScore > 50 -> "Moderate — some battery impact"
            else -> "Low — minimal battery impact"
        }

        return DrainDiagnosis(
            culprit = culprit,
            hasIssues = steps.isNotEmpty(),
            summary = summary,
            fixSteps = steps,
            sessionDurationMs = 0L,
            dataPointsCollected = 0,
            batteryDrainEstimate = drainEstimate
        )
    }

    fun noIssueDiagnosis(): DrainDiagnosis {
        return DrainDiagnosis(
            culprit = null,
            hasIssues = false,
            summary = "No abnormal drain detected. Your device looks healthy!",
            fixSteps = emptyList(),
            sessionDurationMs = 0L,
            dataPointsCollected = 0,
            batteryDrainEstimate = "Normal"
        )
    }
}
