package com.isygold.procinsight.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.isygold.procinsight.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class SystemMonitor(private val context: Context) {

    private val processMonitor = ProcessMonitor(context)
    private val cpuMonitor = CpuMonitor()
    val usageStatsHelper = UsageStatsHelper(context)
    val batteryState = BatteryStateTracker(context)
    private val wakeLockDetector = WakeLockDetector(context, batteryState)
    private val alarmDetector = AlarmDetector(context)

    private val _stats = MutableStateFlow<Resource<SystemStats>>(Resource.Loading())
    val stats: StateFlow<Resource<SystemStats>> = _stats

    init {
        batteryState.start()
    }

    suspend fun refresh() {
        try {
            _stats.value = Resource.Loading()
            batteryState.updateDeviceIdle()

            // Processes: /proc/[pid]/stat enumeration + UsageStats fallback
            val processes = processMonitor.getProcesses()
            val augmentedProcs = if (processes.size <= 3 && usageStatsHelper.isGranted()) {
                val recentApps = usageStatsHelper.getRecentProcesses()
                (processes + recentApps).distinctBy { it.packageName.ifEmpty { it.name } }
                    .sortedByDescending { it.cpuPercent }
            } else processes

            // CPU cores from /proc/stat
            val cpuCores = cpuMonitor.getCpuStats()

            // Wake lock suspects (multi‑strategy)
            val wakeLockSuspects = wakeLockDetector.getWakeLockSuspects(augmentedProcs)

            // Alarms via JobScheduler (no permissions needed)
            val alarms = alarmDetector.getAlarmSuspects()

            val memInfo = readMemInfo()
            val batteryInfo = getBatteryInfo()
            val upTime = readUptime()
            val allPids = processCount()
            val allThreads = augmentedProcs.sumOf { it.threads }

            val totalCpu = if (cpuCores.isNotEmpty()) cpuCores.map { it.usagePercent }.average().toFloat() else 0f
            val topCpu = augmentedProcs.take(10)
            val topWake = wakeLockSuspects.take(10)
            val topAlarm = alarms.take(10)

            val message = buildString {
                if (topCpu.size <= 3 && !usageStatsHelper.isGranted()) {
                    append("Grant Usage Access in Settings for more app visibility")
                }
                if (wakeLockSuspects.isEmpty() && alarms.isEmpty()) {
                    if (isNotEmpty()) append(". ")
                    append("Wake lock data inferred from screen-off CPU activity")
                }
            }

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
                    wakeupCount = wakeLockSuspects.size,
                    topCpuProcesses = topCpu,
                    topWakeLockApps = topWake,
                    topAlarmApps = topAlarm,
                    monitorInfo = MonitorInfo(
                        monitorMode = "basic",
                        processesAvailable = topCpu.isNotEmpty(),
                        wakeLocksAvailable = topWake.isNotEmpty(),
                        alarmsAvailable = topAlarm.isNotEmpty(),
                        message = message,
                        usageStatsGranted = usageStatsHelper.isGranted(),
                        usageStatsMessage = if (usageStatsHelper.isGranted()) ""
                            else "Grant Usage Access in Settings for app activity list"
                    ),
                    diagnostics = MonitorDiagnostics(
                        procStatSource = cpuMonitor.detectionSource,
                        procStatCoreCount = cpuMonitor.rawCpuLineCount,
                        procStatSample = cpuMonitor.rawProcStatSample,
                        processesEnumerated = processMonitor.lastEnumeratedCount,
                        processesReadSuccess = processMonitor.lastReadSuccessCount,
                        procWakelockAccessible = wakeLockDetector.procWakelockAccessible,
                        logcatAccessible = wakeLockDetector.logcatAccessible,
                        pollIntervalMs = 2000,
                        jobSchedulerAccessible = alarmDetector.jobSchedulerAccessible
                    )
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
}
