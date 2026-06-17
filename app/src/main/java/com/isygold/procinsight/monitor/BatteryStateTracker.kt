package com.isygold.procinsight.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

/**
 * Tracks device screen state and battery-related signals.
 * Provides shared state for WakeLockDetector and AlarmDetector.
 *
 * Usage:
 *   val tracker = BatteryStateTracker(context)
 *   tracker.start()   // registers receivers
 *   // ... use tracker.isScreenOff, screenOffSinceMs ...
 *   tracker.stop()    // unregisters receivers (e.g. in onPause)
 */
class BatteryStateTracker(private val context: Context) {

    // ── Public state ──
    @Volatile
    var isScreenOff: Boolean = false
        private set

    @Volatile
    var screenOffSinceMs: Long = 0L
        private set

    @Volatile
    var isDeviceIdle: Boolean = false
        private set

    @Volatile
    var lastScreenOnMs: Long = System.currentTimeMillis()
        private set

    // ── Internal ──
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    screenOffSinceMs = System.currentTimeMillis()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOff = false
                    screenOffSinceMs = 0L
                    lastScreenOnMs = System.currentTimeMillis()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Device unlocked — screen is definitely on and interactive
                    isScreenOff = false
                    screenOffSinceMs = 0L
                    lastScreenOnMs = System.currentTimeMillis()
                }
            }
        }
    }

    fun start() {
        if (registered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(receiver, filter)
            registered = true

            // Initialize idle state
            updateDeviceIdle()
        } catch (_: Exception) { }
    }

    fun stop() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) { }
        registered = false
    }

    /** Call periodically (e.g. every poll) to refresh PowerManager idle state */
    fun updateDeviceIdle() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            isDeviceIdle = pm?.isDeviceIdleMode ?: false
        } catch (_: Exception) {
            isDeviceIdle = false
        }
    }

    /** Duration screen has been off, in milliseconds. 0 if screen is on. */
    fun screenOffDurationMs(): Long {
        if (!isScreenOff) return 0L
        return System.currentTimeMillis() - screenOffSinceMs
    }
}
