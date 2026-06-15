package com.isygold.procinsight.monitor

import com.isygold.procinsight.data.AlarmInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdvancedAlarmMonitor {

    suspend fun getAlarms(): List<AlarmInfo> = withContext(Dispatchers.IO) {
        val alarms = mutableListOf<AlarmInfo>()

        val output = ShizukuManager.execute("dumpsys alarm 2>/dev/null") ?: return@withContext alarms

        val lines = output.lines()
        var currentPkg = ""
        var currentUid = -1

        for (line in lines) {
            when {
                line.startsWith("  Pending alarm") -> { }
                line.contains("Alarm Stats:") -> {
                    val pkgMatch = Regex("Alarm Stats: ([^( ]+)").find(line)
                    val uidMatch = Regex("\\((\\d+)\\)").find(line)
                    currentPkg = pkgMatch?.groupValues?.getOrNull(1) ?: ""
                    currentUid = uidMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
                }
                (line.trim().startsWith("RTC") || line.trim().startsWith("ELAPSED")) && currentPkg.isNotEmpty() -> {
                    try {
                        val parts = line.trim().split(" ")
                        val count = Regex("count=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        val interval = Regex("repeatInterval(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        val whenElapsed = Regex("when=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        val isExact = line.contains("EXACT")
                        val isIdle = line.contains("WHILE_IDLE")
                        val type = if (line.contains("RTC_WAKEUP")) 0 else if (line.contains("RTC")) 1 else 2

                        alarms.add(AlarmInfo(
                            packageName = currentPkg, uid = currentUid,
                            type = type, interval = interval, count = count,
                            lastTrigger = 0L, nextTrigger = whenElapsed * 1000,
                            operation = parts.getOrNull(parts.size - 1)?.trim('{', '}') ?: "unknown",
                            isExact = isExact, isWhileIdle = isIdle
                        ))
                    } catch (_: Exception) { }
                }
            }
        }

        alarms.sortedByDescending { it.count }
    }
}
