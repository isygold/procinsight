package com.isygold.procinsight.detective.session

import android.content.Context
import com.isygold.procinsight.monitor.SystemMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates a profiling session.
 *
 * Takes snapshots every [intervalMs] for [durationMs] and stores them for analysis.
 */
class ProfilingSession(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope? = null
) {

    companion object {
        const val DEFAULT_DURATION_MS = 15 * 60 * 1000L   // 15 minutes
        const val DEFAULT_INTERVAL_MS = 5_000L             // every 5 seconds
    }

    private val monitor = SystemMonitor(context)

    private val _state = MutableStateFlow(ProfilingState.IDLE)
    val state: StateFlow<ProfilingState> = _state.asStateFlow()

    private val _dataPoints = MutableStateFlow<List<DataPoint>>(emptyList())
    val dataPoints: StateFlow<List<DataPoint>> = _dataPoints.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var sessionJob: Job? = null

    fun start(
        durationMs: Long = DEFAULT_DURATION_MS,
        intervalMs: Long = DEFAULT_INTERVAL_MS
    ) {
        sessionJob?.cancel()
        _dataPoints.value = emptyList()
        _progress.value = 0f
        _state.value = ProfilingState.RUNNING

        val startTime = System.currentTimeMillis()

        val targetScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
        sessionJob = targetScope.launch {
            while (isActive && (System.currentTimeMillis() - startTime) < durationMs) {
                monitor.refresh()
                val stats = monitor.stats.value
                if (stats is com.isygold.procinsight.data.Resource.Success) {
                    val d = stats.data
                    val point = DataPoint(
                        timestampMs = System.currentTimeMillis(),
                        processes = d.topCpuProcesses,
                        cpuCores = d.perCore,
                        wakeLocks = d.topWakeLockApps,
                        alarms = d.topAlarmApps,
                        totalCpuPercent = d.totalCpuPercent,
                        batteryPercent = d.batteryPercent,
                        memoryUsedMb = d.usedMemoryMb
                    )
                    _dataPoints.value = _dataPoints.value + point
                }
                val elapsed = System.currentTimeMillis() - startTime
                _progress.value = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                delay(intervalMs)
            }
            _progress.value = 1f
            _state.value = ProfilingState.COMPLETE
        }
    }

    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        _state.value = if (_dataPoints.value.size > 5) ProfilingState.COMPLETE else ProfilingState.IDLE
    }

    fun reset() {
        sessionJob?.cancel()
        sessionJob = null
        _dataPoints.value = emptyList()
        _progress.value = 0f
        _state.value = ProfilingState.IDLE
    }
}

enum class ProfilingState {
    IDLE,
    RUNNING,
    COMPLETE
}
