package com.isygold.procinsight.monitor

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import com.isygold.procinsight.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class SystemMonitor(private val context: Context) {

    private val processMonitor = ProcessMonitor()
    private val cpuMonitor = CpuMonitor()
    private val wakeLockMonitor = WakeLockMonitor()
    private val alarmMonitor = AlarmMonitor()

    private val _stats = MutableStateFlow<Resource<SystemStats>>(Resource.Loading())
    val stats: StateFlow<Resource<SystemStats>> = _stats

    suspend fun refresh() {
        try {
            _stats.value = Resource.Loading()

            val processes = processMonitor.getProcesses()
            val cpuCores = cpuMonitor.getCpuStats()
            val wakeLocks = wakeLockMonitor.getWakeLocks()
            val alarms = alarmMonitor.getAlarms()

            val memInfo = readMemInfo()
            val batteryInfo = getBatteryInfo()
            val upTime = readUptime()
            val allPids = processCount()
            val allThreads = threadCount()

            val totalCpu = cpuCores.map { it.usagePercent }.average().toFloat()
            val topCpu = processes.take(10)
            val topWake = wakeLocks.take(10)
            val topAlarm = alarms.take(10)

            _stats.value = Resource.Success(
                SystemStats(
                    totalCpuPercent = totalCpu,
                    perCore = cpuCores,
                    totalMemoryMb = memInfo.totalMb,
                    usedMemoryMb = memInfo.usedMb,
                    freeMemoryMb = memInfo.freeMb,
                    processes = allPids,
                    threads = allThreads,
                    uptimeMs = upTime,
                    batteryPercent = batteryInfo.first,
                    batteryTemperature = batteryInfo.second,
                    deepSleepTimeMs = 0L,
                    wakeupCount = wakeLocks.size,
                    topCpuProcesses = topCpu,
                    topWakeLockApps = topWake,
                    topAlarmApps = topAlarm
                )
            )
        } catch (e: Exception) {
            _stats.value = Resource.Error(e.message ?: "Unknown error")
        }
    }

    private data class MemInfo(val totalMb: Long, val freeMb: Long, val usedMb: Long)

    private fun readMemInfo(): MemInfo {
        return try {
            val lines = File("/proc/meminfo").readLines()
            val totalKb = lines.firstOrNull { it.startsWith("MemTotal:") }?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 0L
            val freeKb = lines.firstOrNull { it.startsWith("MemFree:") }?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 0L
            val buffersKb = lines.firstOrNull { it.startsWith("Buffers:") }?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 0L
            val cachedKb = lines.firstOrNull { it.startsWith("Cached:") }?.split("\\s+".toRegex())?.getOrNull(1)?.toLongOrNull() ?: 0L

            val usedKb = totalKb - freeKb - buffersKb - cachedKb
            MemInfo(totalKb / 1024, freeKb / 1024, usedKb / 1024)
        } catch (_: Exception) { MemInfo(0, 0, 0) }
    }

    private fun getBatteryInfo(): Pair<Int, Float> {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            Pair(level * 100 / scale, temp / 10f)
        } catch (_: Exception) { Pair(0, 0f) }
    }

    private fun readUptime(): Long {
        return try {
            val uptime = File("/proc/uptime").readText().trim().split(" ").firstOrNull()?.toFloatOrNull()
            (uptime?.times(1000) ?: 0L).toLong()
        } catch (_: Exception) { 0L }
    }

    private fun processCount(): Int {
        return try {
            File("/proc").listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } }?.size ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun threadCount(): Int {
        return try {
            var count = 0
            File("/proc").listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } }?.forEach { dir ->
                try {
                    val status = File(dir, "status").readLines()
                    val threads = status.firstOrNull { it.startsWith("Threads:") }?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull() ?: 0
                    count += threads
                } catch (_: Exception) { }
            }
            count
        } catch (_: Exception) { 0 }
    }
}
