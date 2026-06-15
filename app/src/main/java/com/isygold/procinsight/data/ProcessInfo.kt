package com.isygold.procinsight.data

data class ProcessInfo(
    val pid: Int,
    val name: String,
    val packageName: String,
    val cpuPercent: Float,
    val userCpu: Long,
    val kernelCpu: Long,
    val totalCpu: Long,
    val threads: Int,
    val memoryRss: Long,
    val memoryPss: Long?,
    val priority: Int,
    val nice: Int,
    val state: String,
    val foreground: Boolean,
    val lastActivityTime: Long,
    val wakeups: Int,
    val uid: Int
)
