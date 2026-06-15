package com.isygold.procinsight.data

data class MonitorInfo(
    val monitorMode: String = "basic",
    val processesAvailable: Boolean = true,
    val wakeLocksAvailable: Boolean = false,
    val alarmsAvailable: Boolean = false,
    val cpuAdvanced: Boolean = false,
    val message: String = ""
) {
    companion object {
        val BASIC = MonitorInfo(monitorMode = "basic")
        val ADVANCED = MonitorInfo(
            monitorMode = "advanced",
            processesAvailable = true,
            wakeLocksAvailable = true,
            alarmsAvailable = true,
            cpuAdvanced = true,
            message = "Shizuku connected — full monitoring active"
        )
    }
}

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
    val monitorInfo: MonitorInfo = MonitorInfo.BASIC
)
