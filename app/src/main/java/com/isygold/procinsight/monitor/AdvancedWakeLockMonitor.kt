package com.isygold.procinsight.monitor

import com.isygold.procinsight.data.WakeLockInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdvancedWakeLockMonitor {

    suspend fun getWakeLocks(): List<WakeLockInfo> = withContext(Dispatchers.IO) {
        val wakeLocks = mutableListOf<WakeLockInfo>()

        val dumpsysOutput = ShizukuManager.execute("dumpsys power 2>/dev/null") ?: return@withContext wakeLocks

        val inSection = mutableListOf<String>()
        var inLocks = false
        for (line in dumpsysOutput.lines()) {
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
                val timeout = Regex("timeout=(\\d+)").find(details)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val uid = Regex("uid=(\\d+)").find(details)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                val pid = Regex("pid=(\\d+)").find(details)?.groupValues?.get(1)?.toIntOrNull() ?: -1

                wakeLocks.add(WakeLockInfo(
                    name = name, packageName = "", pid = pid, uid = uid,
                    acquired = 0L, timeout = timeout, isHeld = held,
                    flags = 0, type = name, totalTimeMs = timeout
                ))
            } catch (_: Exception) { }
        }

        val batteryOutput = ShizukuManager.execute("dumpsys batterystats --wakelocks 2>/dev/null") ?: return@withContext wakeLocks.sortedByDescending { it.totalTimeMs }

        var inPower = false
        for (line in batteryOutput.lines()) {
            when {
                line.contains("* Wakelocks:") -> inPower = true
                line.contains("*") && !line.contains("Wakelocks:") -> inPower = false
                inPower && line.trim().startsWith("Wake lock") -> {
                    try {
                        val wlName = line.split(" ").getOrNull(2)?.trim('"') ?: continue
                        val wlUid = Regex("uid=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        val time = Regex("duration=([\\d,]+)").find(line)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L

                        val existing = wakeLocks.indexOfFirst { it.name == wlName && it.uid == wlUid }
                        if (existing >= 0) wakeLocks[existing] = wakeLocks[existing].copy(totalTimeMs = time)
                        else wakeLocks.add(WakeLockInfo(wlName, "", -1, wlUid, 0, 0, false, 0, "PARTIAL_WAKE_LOCK", time))
                    } catch (_: Exception) { }
                }
            }
        }

        wakeLocks.sortedByDescending { it.totalTimeMs }
    }
}
