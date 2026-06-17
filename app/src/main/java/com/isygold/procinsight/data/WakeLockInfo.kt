package com.isygold.procinsight.data

enum class DetectionConfidence {
    HIGH,       // Direct evidence: /proc/wakelocks, logcat events
    MEDIUM,     // Strong inference: CPU active while screen off
    LOW         // Weak inference: scheduled jobs, UsageStats patterns
}

data class WakeLockInfo(
    val name: String,
    val packageName: String,
    val pid: Int,
    val uid: Int,
    val acquired: Long,
    val timeout: Long,
    val isHeld: Boolean,
    val flags: Int,
    val type: String,
    val totalTimeMs: Long,
    val confidence: DetectionConfidence = DetectionConfidence.MEDIUM,
    val detectionMethod: String = "unknown"
)
