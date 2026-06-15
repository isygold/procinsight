package com.isygold.procinsight.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.isygold.procinsight.monitor.SystemMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class MonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitor: SystemMonitor? = null
    private var job: Job? = null

    companion object {
        const val CHANNEL_ID = "procinsight_monitor"
        const val NOTIFICATION_ID = 1001
        val isRunning = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        monitor = SystemMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning.value = true

        job?.cancel()
        job = scope.launch {
            while (isActive) {
                monitor?.refresh()
                delay(5000)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ProcInsight Background Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors processes, CPU, and wakeups in background"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ProcInsight Monitoring")
            .setContentText("Tracking processes, CPU, and wakeups...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
