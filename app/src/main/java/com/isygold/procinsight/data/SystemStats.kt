package com.isygold.procinsight.data

data class SystemStats(
    val totalCpuPercent: Float,
    val perCore: List<CpuCoreInfo>,
    val totalMemoryMb: Long,
    val usedMemoryMb: Long,
    val freeMemoryMb: Long,
    val processes: Int,
    val threads: Int,
    val uptimeMs: Long,
    val batteryPercent: Int,
    val batteryTemperature: Float,
    val deepSleepTimeMs: Long,
    val wakeupCount: Int,
    val topCpuProcesses: List<ProcessInfo>,
    val topWakeLockApps: List<WakeLockInfo>,
    val topAlarmApps: List<AlarmInfo>
)
