package com.isygold.procinsight.monitor

import com.isygold.procinsight.data.WakeLockInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WakeLockMonitor {

    suspend fun getWakeLocks(): List<WakeLockInfo> = withContext(Dispatchers.IO) {
        val wakeLocks = mutableListOf<WakeLockInfo>()

        try {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "power"))
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            process.waitFor()

            val inSection = mutableListOf<String>()
            var inLocks = false
            for (line in output.lines()) {
                when {
                    line.contains("Wake Locks:") -> inLocks = true
                    line.contains("Display Power:") -> inLocks = false
                    inLocks && line.trim().startsWith("WAKE_LOCK_") -> inSection.add(line.trim())
                }
            }

            for (lock in inSection) {
                try {
                    val parts = lock.split(":")
                    if (parts.size < 2) continue
                    val name = parts[0].trim()
                    val details = parts.drop(1).joinToString(":").trim()

                    val held = details.contains("held")
                    val timeoutMatch = Regex("timeout=(\\d+)").find(details)
                    val acquiredMatch = Regex("acquired=(\\d+)").find(details)
                    val uidMatch = Regex("uid=(\\d+)").find(details)
                    val pidMatch = Regex("pid=(\\d+)").find(details)

                    wakeLocks.add(
                        WakeLockInfo(
                            name = name,
                            packageName = "",
                            pid = pidMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1,
                            uid = uidMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1,
                            acquired = acquiredMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
                            timeout = timeoutMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
                            isHeld = held,
                            flags = 0,
                            type = name,
                            totalTimeMs = timeoutMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        )
                    )
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }

        try {
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "batterystats", "--wakelocks"))
            val reader = proc.inputStream.bufferedReader()
            val output = reader.readText()
            reader.close()
            proc.waitFor()

            val lines = output.lines()
            var inPower = false
            for (line in lines) {
                when {
                    line.contains("* Wakelocks:") -> inPower = true
                    line.contains("*") && !line.contains("Wakelocks:") -> inPower = false
                    inPower && line.trim().startsWith("Wake lock") -> {
                        val parts = line.split(" ")
                        val name = parts.getOrNull(2)?.trim('"') ?: continue
                        val uid = Regex("uid=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        val time = Regex("duration=([\\d,]+)").find(line)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L

                        val existing = wakeLocks.indexOfFirst { it.name == name && it.uid == uid }
                        if (existing >= 0) {
                            val old = wakeLocks[existing]
                            wakeLocks[existing] = old.copy(totalTimeMs = time)
                        } else {
                            wakeLocks.add(
                                WakeLockInfo(name, "", -1, uid, 0, 0, false, 0, "PARTIAL_WAKE_LOCK", time)
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        wakeLocks.sortedByDescending { it.totalTimeMs }
    }
}
