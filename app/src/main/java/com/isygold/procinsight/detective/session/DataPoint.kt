package com.isygold.procinsight.detective.session

import com.isygold.procinsight.data.AlarmInfo
import com.isygold.procinsight.data.CpuCoreInfo
import com.isygold.procinsight.data.ProcessInfo
import com.isygold.procinsight.data.WakeLockInfo

/**
 * A single snapshot of system state at a point in time during profiling.
 */
data class DataPoint(
    val timestampMs: Long,
    val processes: List<ProcessInfo>,
    val cpuCores: List<CpuCoreInfo>,
    val wakeLocks: List<WakeLockInfo>,
    val alarms: List<AlarmInfo>,
    val totalCpuPercent: Float,
    val batteryPercent: Int,
    val memoryUsedMb: Long
)
