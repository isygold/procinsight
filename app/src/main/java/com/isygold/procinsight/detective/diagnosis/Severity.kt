package com.isygold.procinsight.detective.diagnosis

enum class Severity(val level: Int, val label: String) {
    LOW(1, "Low"),
    MEDIUM(2, "Medium"),
    HIGH(3, "High"),
    CRITICAL(4, "Critical")
}

enum class DrainOffense(val label: String) {
    CPU_HOG("CPU Hog"),
    WAKE_LOCK_ABUSE("Wake Lock Abuse"),
    ALARM_SPAM("Alarm Spam"),
    MEMORY_LEAK("Memory Leak"),
    KERNEL_WAKEUP("Kernel Wakeup"),
    OVERHEATING("Overheating")
}
