package com.isygold.procinsight.monitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isygold.procinsight.data.Resource
import com.isygold.procinsight.data.SystemStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val systemMonitor = SystemMonitor(application)

    private val _stats = MutableStateFlow<Resource<SystemStats>>(Resource.Loading())
    val stats: StateFlow<Resource<SystemStats>> = _stats.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _mode = MutableStateFlow(MonitorMode.BASIC)
    val mode: StateFlow<MonitorMode> = _mode.asStateFlow()

    private val _shizukuAvailable = MutableStateFlow(false)
    val shizukuAvailable: StateFlow<Boolean> = _shizukuAvailable.asStateFlow()

    private val _refreshInterval = MutableStateFlow(2000L)
    val refreshInterval: StateFlow<Long> = _refreshInterval.asStateFlow()

    private var monitorJob: Job? = null

    fun startMonitoring(intervalMs: Long = 2000) {
        _refreshInterval.value = intervalMs
        _isMonitoring.value = true
        _shizukuAvailable.value = systemMonitor.shizukuManager.checkAvailability()
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                systemMonitor.refresh()
                _stats.value = systemMonitor.stats.value
                delay(_refreshInterval.value)
            }
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        monitorJob?.cancel()
        monitorJob = null
    }

    fun toggleMode() {
        val newMode = if (_mode.value == MonitorMode.BASIC) MonitorMode.ADVANCED else MonitorMode.BASIC
        _mode.value = newMode
        systemMonitor.setMode(newMode)
        if (_isMonitoring.value) {
            refreshOnce()
        }
    }

    fun refreshOnce() {
        viewModelScope.launch {
            systemMonitor.refresh()
            _stats.value = systemMonitor.stats.value
        }
    }

    fun checkShizuku(): Boolean {
        val avail = systemMonitor.shizukuManager.checkAvailability()
        _shizukuAvailable.value = avail
        return avail
    }

    fun setInterval(ms: Long) {
        _refreshInterval.value = ms
        if (_isMonitoring.value) {
            startMonitoring(ms)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
