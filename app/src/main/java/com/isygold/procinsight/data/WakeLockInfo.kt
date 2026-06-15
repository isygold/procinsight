package com.isygold.procinsight.data

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
    val totalTimeMs: Long
)
