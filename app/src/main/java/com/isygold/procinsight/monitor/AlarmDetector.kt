package com.isygold.procinsight.monitor

import android.app.AlarmManager
import android.app.job.JobScheduler
import android.content.Context
import com.isygold.procinsight.data.AlarmInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects scheduled alarms via the JobScheduler API (no special permissions).
 *
 * Strategies:
 *   1. JobScheduler.getAllPendingJobs() — works on all Android 11+ devices
 *      without any permission. Reveals which apps have scheduled background
 *      work, interval, network requirements, etc.
 *   2. AlarmManager.nextAlarmClock — shows the next user-visible alarm clock.
 *      Very limited but 100% reliable.
 */
class AlarmDetector(private val context: Context) {

    var jobSchedulerAccessible: Boolean = false
        private set
    var pendingJobCount: Int = 0
        private set

    suspend fun getAlarmSuspects(): List<AlarmInfo> = withContext(Dispatchers.IO) {
        val suspects = mutableListOf<AlarmInfo>()

        // Strategy 1: JobScheduler pending jobs
        suspects.addAll(getScheduledJobs())

        // Strategy 2: nextAlarmClock (very limited)
        suspects.addAll(getNextAlarmClock())

        suspects.sortedByDescending { it.count }
    }

    private fun getScheduledJobs(): List<AlarmInfo> {
        return try {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return emptyList()
            val jobs = js.allPendingJobs ?: return emptyList()

            jobSchedulerAccessible = true
            pendingJobCount = jobs.size

            jobs.mapNotNull { job ->
                try {
                    val pkg = job.service?.packageName ?: return@mapNotNull null
                    val cls = job.service?.className ?: "unknown"

                    AlarmInfo(
                        packageName = pkg,
                        uid = -1,
                        type = if (job.isPeriodic) 0 else 1,
                        interval = if (job.isPeriodic) job.intervalMillis else 0L,
                        count = 1,
                        lastTrigger = 0L,
                        nextTrigger = if (job.isPeriodic) {
                            try { job.nextScheduleTimeMillis } catch (_: Exception) { 0L }
                        } else 0L,
                        operation = cls.substringAfterLast('.'),
                        isExact = false,
                        isWhileIdle = job.isRequiresDeviceIdle || job.isRequiresCharging
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun getNextAlarmClock(): List<AlarmInfo> {
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return emptyList()
            val info = am.nextAlarmClock ?: return emptyList()

            listOf(
                AlarmInfo(
                    packageName = info.showIntent?.creatorPackage ?: "unknown",
                    uid = -1,
                    type = 0,
                    interval = 0,
                    count = 1,
                    lastTrigger = 0L,
                    nextTrigger = info.triggerTime,
                    operation = "alarm_clock",
                    isExact = true,
                    isWhileIdle = false
                )
            )
        } catch (_: Exception) { emptyList() }
    }
}
