package com.isygold.procinsight.monitor

import android.content.Context
import com.isygold.procinsight.data.DetectionConfidence
import com.isygold.procinsight.data.ProcessInfo
import com.isygold.procinsight.data.WakeLockInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi‑strategy wake lock detector.
 *
 * Strategies (ordered by reliability):
 *   1. /proc/wakelocks        — kernel wakelocks (HIGH confidence)
 *   2. Screen‑off inference   — processes using CPU while screen is off (MEDIUM)
 *   3. logcat events          — userspace wakelock acquire/release (HIGH if accessible)
 *   4. Device idle            — PowerManager.isDeviceIdleMode signal (LOW)
 *   5. Wakeup count delta     — /sys/power/wakeup_count tracking (LOW)
 */
class WakeLockDetector(
    private val context: Context,
    private val batteryState: BatteryStateTracker
) {
    // ── Screen‑off process tracking ──
    private data class TrackedProcess(
        var accumulatedCpuTicks: Long = 0L,
        var lastPollCpuTicks: Long = 0L,
        var firstSeenMs: Long = System.currentTimeMillis(),
        var name: String = "",
        var packageName: String = "",
        var uid: Int = -1,
        var pid: Int = 0
    )

    private val tracked = mutableMapOf<Int, TrackedProcess>()     // pid → tracking
    private var screenWasOff = false
    private var lastWakeupCount = -1L

    // ── Diagnostics ──
    var procWakelockAccessible: Boolean = false
        private set
    var logcatAccessible: Boolean = false
        private set
    var lastScreenOffDurationMs: Long = 0L
        private set

    // ── Public API ──

    /**
     * Must be called every poll cycle with the current process list.
     * Internally decides when to accumulate vs. flush based on screen state.
     */
    suspend fun getWakeLockSuspects(processes: List<ProcessInfo>): List<WakeLockInfo> =
        withContext(Dispatchers.IO) {
            val suspects = mutableListOf<WakeLockInfo>()

            // Strategy 1: /proc/wakelocks
            suspects.addAll(readProcWakelocks())

            // Strategy 2: screen‑off inference
            updateScreenOffTracking(processes)
            suspects.addAll(getScreenOffSuspects())

            // Strategy 3: logcat events
            suspects.addAll(readLogcatEvents())

            // Strategy 4: device idle signal
            suspects.addAll(checkDeviceIdle())

            // Strategy 5: wakeup count delta
            suspects.addAll(checkWakeupCount())

            // Deduplicate by (name, uid) — keep highest confidence
            suspects.groupBy { it.name to it.uid }
                .map { (_, group) ->
                    group.maxByOrNull { it.confidence.ordinal } ?: group.first()
                }
                .sortedByDescending { it.totalTimeMs }
        }

    // ── Strategy 1: /proc/wakelocks ──

    private fun readProcWakelocks(): List<WakeLockInfo> {
        val lines = readFileWithFallback("/proc/wakelocks") ?: return emptyList()
        procWakelockAccessible = true

        return lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Wake-lock") && !it.startsWith("name") }
            .mapNotNull { line ->
                try {
                    val parts = line.split("\\s+".toRegex())
                    val name = parts.getOrNull(0) ?: return@mapNotNull null
                    val refCount = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    val timeout = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                    val timeHeld = parts.getOrNull(3)?.toLongOrNull() ?: 0L

                    WakeLockInfo(
                        name = name,
                        packageName = "",
                        pid = -1,
                        uid = -1,
                        acquired = 0,
                        timeout = timeout,
                        isHeld = refCount > 0,
                        flags = 0,
                        type = "KERNEL",
                        totalTimeMs = timeHeld,
                        confidence = DetectionConfidence.HIGH,
                        detectionMethod = "kernel_wakelock"
                    )
                } catch (_: Exception) { null }
            }
            .toList()
    }

    // ── Strategy 2: Screen‑off process inference ──

    private fun updateScreenOffTracking(processes: List<ProcessInfo>) {
        val screenOffNow = batteryState.isScreenOff

        // Screen just turned ON → flush accumulated tracking (keep for query)
        if (screenWasOff && !screenOffNow) {
            lastScreenOffDurationMs = batteryState.screenOffDurationMs()
        }

        // Screen just turned OFF → start fresh tracking
        if (!screenWasOff && screenOffNow) {
            tracked.clear()
        }

        screenWasOff = screenOffNow

        // Only accumulate when screen is off
        if (!screenOffNow) return

        for (proc in processes) {
            if (proc.cpuPercent <= 0f && proc.totalCpu <= 0) continue

            val entry = tracked.getOrPut(proc.pid) {
                TrackedProcess(
                    name = proc.name,
                    packageName = proc.packageName,
                    uid = proc.uid,
                    pid = proc.pid,
                    firstSeenMs = System.currentTimeMillis()
                )
            }

            // Update metadata
            entry.name = proc.name
            entry.packageName = proc.packageName
            entry.uid = proc.uid

            // Accumulate delta ticks since last poll
            if (entry.lastPollCpuTicks > 0 && proc.totalCpu > entry.lastPollCpuTicks) {
                entry.accumulatedCpuTicks += (proc.totalCpu - entry.lastPollCpuTicks)
            }
            entry.lastPollCpuTicks = proc.totalCpu
        }
    }

    private fun getScreenOffSuspects(): List<WakeLockInfo> {
        if (tracked.isEmpty()) return emptyList()

        val totalScreenOffMs = lastScreenOffDurationMs.coerceAtLeast(1000L)

        return tracked.entries
            .filter { it.value.accumulatedCpuTicks > 0 }
            .sortedByDescending { it.value.accumulatedCpuTicks }
            .take(15)
            .map { (pid, t) ->
                // Convert accumulated jiffies to estimated ms (100 jiffies/sec)
                val estimatedTimeMs = (t.accumulatedCpuTicks * 10)
                WakeLockInfo(
                    name = t.name,
                    packageName = t.packageName,
                    pid = pid,
                    uid = t.uid,
                    acquired = t.firstSeenMs,
                    timeout = 0,
                    isHeld = true,
                    flags = 0,
                    type = "PARTIAL_WAKE_LOCK (inferred)",
                    totalTimeMs = estimatedTimeMs.coerceAtLeast(1),
                    confidence = DetectionConfidence.MEDIUM,
                    detectionMethod = "screen_off_inference"
                )
            }
    }

    // ── Strategy 3: logcat events ──

    private fun readLogcatEvents(): List<WakeLockInfo> {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-b", "events", "-d", "-s", "wakelock"
            ))
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            if (text.isBlank()) return emptyList()
            logcatAccessible = true

            text.lines()
                .filter { it.contains("acquired") || it.contains("released") }
                .mapNotNull { line ->
                    try {
                        val nameMatch = Regex("WakeLock (acquired|released): (.+?)(?: \\(|$)").find(line)
                        val name = nameMatch?.groupValues?.getOrNull(2) ?: return@mapNotNull null
                        val uid = Regex("uid=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        val pid = Regex("pid=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        val isHeld = !line.contains("released")

                        WakeLockInfo(
                            name = name.trim('"'),
                            packageName = "",
                            pid = pid,
                            uid = uid,
                            acquired = 0,
                            timeout = 0,
                            isHeld = isHeld,
                            flags = 0,
                            type = "logcat",
                            totalTimeMs = 0,
                            confidence = DetectionConfidence.HIGH,
                            detectionMethod = "logcat_wakelock"
                        )
                    } catch (_: Exception) { null }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Strategy 4: Device idle signal ──

    private fun checkDeviceIdle(): List<WakeLockInfo> {
        if (batteryState.isDeviceIdle) return emptyList()  // device is dozing normally

        val screenOffMs = batteryState.screenOffDurationMs()
        if (screenOffMs < 120_000) return emptyList()  // less than 2 min → normal

        // Screen off > 2 min but device NOT in doze → something is holding wake lock
        return listOf(
            WakeLockInfo(
                name = "Device not dozing",
                packageName = "",
                pid = -1,
                uid = -1,
                acquired = batteryState.screenOffSinceMs,
                timeout = 0,
                isHeld = true,
                flags = 0,
                type = "IDLE_INHIBITOR",
                totalTimeMs = screenOffMs,
                confidence = DetectionConfidence.LOW,
                detectionMethod = "device_idle_check"
            )
        )
    }

    // ── Strategy 5: Wakeup count tracking ──

    private fun checkWakeupCount(): List<WakeLockInfo> {
        val count = try {
            File("/sys/power/wakeup_count").readText().trim().toLongOrNull()
        } catch (_: Exception) { null } ?: return emptyList()

        if (lastWakeupCount >= 0) {
            val delta = count - lastWakeupCount
            if (delta > 0) {
                lastWakeupCount = count
                return listOf(
                    WakeLockInfo(
                        name = "Total wakeups: +$delta",
                        packageName = "",
                        pid = -1,
                        uid = -1,
                        acquired = 0,
                        timeout = 0,
                        isHeld = true,
                        flags = 0,
                        type = "WAKEUP_COUNT",
                        totalTimeMs = delta,
                        confidence = DetectionConfidence.LOW,
                        detectionMethod = "wakeup_count"
                    )
                )
            }
        }
        lastWakeupCount = count
        return emptyList()
    }

    // ── Utilities ──

    /** Try direct file read, then cat exec, then sh -c cat exec. */
    private fun readFileWithFallback(path: String): String? {
        // Direct
        try {
            val text = File(path).readText().trim()
            if (text.isNotEmpty()) return text
        } catch (_: Exception) { }

        // cat
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("cat", path))
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (text.isNotEmpty()) return text
        } catch (_: Exception) { }

        // sh -c cat
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat $path"))
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (text.isNotEmpty()) return text
        } catch (_: Exception) { }

        return null
    }
}
