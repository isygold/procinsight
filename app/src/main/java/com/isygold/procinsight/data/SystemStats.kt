package com.isygold.procinsight.data

data class MonitorInfo(
    val monitorMode: String = "basic",
    val processesAvailable: Boolean = true,
    val wakeLocksAvailable: Boolean = false,
    val alarmsAvailable: Boolean = false,
    val cpuAdvanced: Boolean = false,
    val message: String = "",
    val usageStatsGranted: Boolean = false,
    val usageStatsMessage: String = ""
)

data class MonitorDiagnostics(
    /** Which layer succeeded for /proc/stat: "direct", "cat_exec", "sh_exec", "sysfs_only" */
    val procStatSource: String = "",
    /** How many cpuN lines were found in /proc/stat */
    val procStatCoreCount: Int = 0,
    /** Sample of raw /proc/stat output (first 4 cpuN lines) */
    val procStatSample: String = "",
    /** Total PIDs listed in /proc/ */
    val processesEnumerated: Int = 0,
    /** PIDs whose /proc/[pid]/stat was successfully read */
    val processesReadSuccess: Int = 0,
    /** Whether /proc/wakelocks is accessible */
    val procWakelockAccessible: Boolean = false,
    /** Whether logcat -b events is accessible */
    val logcatAccessible: Boolean = false,
    /** Poll interval in ms */
    val pollIntervalMs: Long = 2000
)

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
    val topAlarmApps: List<AlarmInfo>,
    val monitorInfo: MonitorInfo = MonitorInfo(),
    val diagnostics: MonitorDiagnostics = MonitorDiagnostics()
)
