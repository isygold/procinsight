package com.isygold.procinsight.detective.analysis

import com.isygold.procinsight.detective.session.DataPoint

/**
 * Main orchestrator: runs all detectors then correlates results.
 */
class DrainAnalyzer {

    private val cpuHogDetector = CpuHogDetector()
    private val wakeLockDetector = WakeLockDetector()
    private val alarmSpamDetector = AlarmSpamDetector()
    private val correlator = Correlator()

    data class AnalysisReport(
        val cpuHogs: List<CpuHogDetector.CpuHogResult>,
        val wakeLocks: List<WakeLockDetector.WakeLockAbuseResult>,
        val alarmSpams: List<AlarmSpamDetector.AlarmSpamResult>,
        val culprits: List<Correlator.CorrelatedCulprit>,
        val dataPointCount: Int,
        val sessionDurationMs: Long,
        val hasIssues: Boolean
    )

    fun analyze(points: List<DataPoint>): AnalysisReport {
        val cpuHogs = cpuHogDetector.detect(points)
        val wakeLocks = wakeLockDetector.detect(points)
        val alarmSpams = alarmSpamDetector.detect(points)
        val culprits = correlator.correlate(cpuHogs, wakeLocks, alarmSpams)

        val sessionDurationMs = if (points.size > 1)
            points.last().timestampMs - points.first().timestampMs else 0L

        return AnalysisReport(
            cpuHogs = cpuHogs,
            wakeLocks = wakeLocks,
            alarmSpams = alarmSpams,
            culprits = culprits,
            dataPointCount = points.size,
            sessionDurationMs = sessionDurationMs,
            hasIssues = culprits.isNotEmpty()
        )
    }
}
