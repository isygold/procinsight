package com.isygold.procinsight.detective.diagnosis

import com.isygold.procinsight.detective.analysis.Correlator

/**
 * Final diagnosis for the user — one culprit, one action plan.
 */
data class DrainDiagnosis(
    val culprit: Correlator.CorrelatedCulprit?,
    val hasIssues: Boolean,
    val summary: String,
    val fixSteps: List<FixStep>,
    val sessionDurationMs: Long,
    val dataPointsCollected: Int,
    val batteryDrainEstimate: String
)

data class FixStep(
    val order: Int,
    val action: String,
    val detail: String,
    val type: FixActionType
)

enum class FixActionType(val label: String) {
    FORCE_STOP("Force Stop"),
    SETTING("Open Setting"),
    UNINSTALL("Uninstall"),
    ADVICE("Advice")
}
