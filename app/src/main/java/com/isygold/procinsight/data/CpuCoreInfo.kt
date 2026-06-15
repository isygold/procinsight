package com.isygold.procinsight.data

data class CpuCoreInfo(
    val core: Int,
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long,
    val steal: Long,
    val total: Long,
    val active: Long,
    val usagePercent: Float,
    val frequencyKhz: Int,
    val governor: String,
    val online: Boolean,
    val minFreq: Int,
    val maxFreq: Int
)
