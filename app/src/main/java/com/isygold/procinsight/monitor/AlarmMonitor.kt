package com.isygold.procinsight.monitor

import com.isygold.procinsight.data.AlarmInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmMonitor {

    suspend fun getAlarms(): List<AlarmInfo> = withContext(Dispatchers.IO) {
        val alarms = mutableListOf<AlarmInfo>()

        try {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "alarm"))
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            process.waitFor()

            val lines = output.lines()
            var currentPkg = ""
            var currentUid = -1
            var inActivities = false

            for (line in lines) {
                when {
                    line.startsWith("  Pending alarm") -> inActivities = true
                    line.startsWith("  ") && line.contains("Alarm Stats:") -> {
                        val pkgMatch = Regex("Alarm Stats: ([^( ]+)").find(line)
                        val uidMatch = Regex("\\((\\d+)\\)").find(line)
                        currentPkg = pkgMatch?.groupValues?.getOrNull(1) ?: ""
                        currentUid = uidMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
                        inActivities = false
                    }
                    inActivities && line.trim().startsWith("RTC") || line.trim().startsWith("ELAPSED") -> {
                        try {
                            val parts = line.trim().split(" ")
                            val type = if (line.contains("RTC_WAKEUP")) 0 else if (line.contains("RTC")) 1 else 2
                            val interval = Regex("repeatInterval(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                            val count = Regex("count=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                            val whenElapsed = Regex("when=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                            val operation = parts.getOrNull(parts.size - 1)?.trim('{', '}') ?: "unknown"
                            val isExact = line.contains("EXACT")
                            val isIdle = line.contains("WHILE_IDLE")

                            alarms.add(
                                AlarmInfo(
                                    packageName = currentPkg,
                                    uid = currentUid,
                                    type = type,
                                    interval = interval,
                                    count = count,
                                    lastTrigger = 0L,
                                    nextTrigger = whenElapsed * 1000,
                                    operation = operation,
                                    isExact = isExact,
                                    isWhileIdle = isIdle
                                )
                            )
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) { }

        alarms.sortedByDescending { it.count }
    }
}
