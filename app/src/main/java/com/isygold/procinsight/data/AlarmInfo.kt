package com.isygold.procinsight.data

data class AlarmInfo(
    val packageName: String,
    val uid: Int,
    val type: Int,
    val interval: Long,
    val count: Int,
    val lastTrigger: Long,
    val nextTrigger: Long,
    val operation: String,
    val isExact: Boolean,
    val isWhileIdle: Boolean
)
