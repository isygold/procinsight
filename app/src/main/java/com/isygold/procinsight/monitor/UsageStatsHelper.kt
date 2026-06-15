package com.isygold.procinsight.monitor

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.isygold.procinsight.data.ProcessInfo

class UsageStatsHelper(private val context: Context) {

    fun isGranted(): Boolean {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
            val currentTime = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentTime - 1000, currentTime)
            true // no exception = permission granted
        } catch (_: SecurityException) { false }
    }

    fun getRecentProcesses(): List<ProcessInfo> {
        if (!isGranted()) return emptyList()

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTime = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                currentTime - 24 * 60 * 60 * 1000,
                currentTime
            ) ?: return emptyList()

            stats.filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.lastTimeUsed }
                .take(50)
                .map { stat ->
                    ProcessInfo(
                        pid = -1,
                        name = stat.packageName.substringAfterLast("."),
                        packageName = stat.packageName,
                        cpuPercent = 0f,
                        userCpu = stat.totalTimeInForeground,
                        kernelCpu = 0,
                        totalCpu = stat.totalTimeInForeground,
                        threads = 0,
                        memoryRss = 0,
                        memoryPss = null,
                        priority = 0,
                        nice = 0,
                        state = if (System.currentTimeMillis() - stat.lastTimeUsed < 60_000) "Active" else "Background",
                        foreground = System.currentTimeMillis() - stat.lastTimeUsed < 30_000,
                        wakeups = 0,
                        uid = -1
                    )
                }
        } catch (_: Exception) { emptyList() }
    }

    fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    companion object {
        const val PERMISSION_HINT = "Grant Usage Access in Settings to see recently active apps, or install Shizuku and switch to ADV mode for full process monitoring."
    }
}
