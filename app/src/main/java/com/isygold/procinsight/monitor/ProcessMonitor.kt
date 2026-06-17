package com.isygold.procinsight.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.isygold.procinsight.data.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi-layer process monitor that works around Android 11+ SELinux /proc
 * restrictions on MIUI.
 *
 * === Strategy layers (tried in order per PID) ===
 *
 *   Layer 1 – Direct /proc/[pid]/stat read (full CPU data)
 *   Layer 2 – Runtime.exec("cat /proc/[pid]/stat") fallback
 *   Layer 3 – /proc/[pid]/cmdline (different SELinux label "proc_pid_cmdline",
 *             often allowed on MIUI when "proc_pid_stat" is blocked)
 *   Layer 4 – /proc/[pid]/status for Uid + Name (proc_pid_status)
 *   Layer 5 – /proc/[pid]/comm minimal name fallback
 *   Layer 6 – `ps -A` via Runtime.exec (single-shot listing, works on some
 *             MIUI builds even in untrusted_app context)
 *   Layer 7 – /proc/net/tcp + /proc/net/tcp6 UID extraction (ALWAYS
 *             world-readable; UIDs mapped to packages via PackageManager)
 *   Layer 8 – UsageStats (always merged, not just when count ≤ 3)
 *   Layer 9 – ActivityManager.runningAppProcesses (own process only)
 */
class ProcessMonitor(private val context: Context? = null) {

    private data class StatFields(
        val pid: Int,
        val comm: String,
        val state: String,
        val utime: Long,
        val stime: Long,
        val priority: Int,
        val nice: Int,
        val rssPages: Long
    )

    // Per-PID previous CPU ticks for delta computation
    private val prevCpuTicks = mutableMapOf<Int, Long>()
    private var lastPollTime = 0L

    // Diagnostics counters
    var lastEnumeratedCount = 0
        private set
    var lastReadSuccessCount = 0
        private set
    /** How many PIDs had at minimum a name resolved (even without CPU data) */
    var lastNamedCount = 0
        private set
    /** Whether ps -A exec fallback succeeded */
    var psAccessible: Boolean = false
        private set
    /** Whether /proc/net/tcp parsing succeeded */
    var netTcpAccessible: Boolean = false
        private set

    // ─────────────────────────────────────────────────────────────────────────
    //  Stat parsing
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseStat(text: String): StatFields? {
        return try {
            val firstParen = text.indexOf('(')
            val lastParen = text.lastIndexOf(')')
            if (firstParen == -1 || lastParen == -1 || lastParen <= firstParen) return null

            val pid = text.substring(0, firstParen).trim().toIntOrNull() ?: return null
            val comm = text.substring(firstParen + 1, lastParen)
            val rest = text.substring(lastParen + 1).trim().split("\\s+".toRegex())

            StatFields(
                pid = pid,
                comm = comm,
                state = rest.getOrNull(0)?.firstOrNull()?.toString() ?: "?",
                utime = rest.getOrNull(11)?.toLongOrNull() ?: 0L,
                stime = rest.getOrNull(12)?.toLongOrNull() ?: 0L,
                priority = rest.getOrNull(15)?.toIntOrNull() ?: 0,
                nice = rest.getOrNull(16)?.toIntOrNull() ?: 0,
                rssPages = rest.getOrNull(21)?.toLongOrNull() ?: 0L
            )
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Layer 1 & 2 — Stat read (direct + cat exec)
    // ─────────────────────────────────────────────────────────────────────────

    private fun readProcStat(pid: Int): String? {
        try {
            return File("/proc/$pid/stat").readText().trim()
        } catch (_: Exception) { }

        try {
            val proc = Runtime.getRuntime().exec(arrayOf("cat", "/proc/$pid/stat"))
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (text.isNotEmpty() && text[0].isDigit()) return text
        } catch (_: Exception) { }

        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Layer 3 — cmdline read (different SELinux label, often allowed on MIUI)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Read /proc/[pid]/cmdline. Returns the first null-terminated entry which
     * for Android app processes is the package name, and for native processes
     * is the executable path.
     */
    private fun readCmdline(pid: Int): String? {
        return try {
            val bytes = File("/proc/$pid/cmdline").readBytes()
            // Find first null byte; everything before it is the cmdline
            val end = bytes.indexOf(0)
            val text = if (end >= 0) String(bytes, 0, end, Charsets.UTF_8) else String(bytes, Charsets.UTF_8)
            text.trim().ifEmpty { null }
        } catch (_: Exception) { null }
    }

    /** Extract a user-friendly name from a cmdline string. */
    private fun friendlyNameFromCmdline(cmdline: String): String {
        if (cmdline.startsWith("/")) {
            // Native binary — take basename
            val name = cmdline.substringAfterLast('/')
            // Strip common prefixes like "app_process", "linker", "sh"
            return when {
                name.startsWith("app_process") -> {
                    // Zygote process — extract extra arg if available
                    "zygote"
                }
                name == "sh" || name == "bash" || name == "ksh" -> "shell"
                else -> name
            }
        }
        // Java process — likely a package name or class
        return cmdline.substringAfterLast('.').ifEmpty { cmdline }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Layer 4 — /proc/[pid]/status for Uid + Name
    // ─────────────────────────────────────────────────────────────────────────

    private data class StatusInfo(
        val name: String?,
        val uid: Int?,
        val threads: Int?
    )

    private fun readStatus(pid: Int): StatusInfo? {
        return try {
            val lines = File("/proc/$pid/status").readLines()
            val name = lines.firstOrNull { it.startsWith("Name:") }
                ?.substringAfter(":")?.trim()
            val uidStr = lines.firstOrNull { it.startsWith("Uid:") }
                ?.substringAfter(":")?.trim()?.split("\\s+".toRegex())?.firstOrNull()
            val threads = lines.firstOrNull { it.startsWith("Threads:") }
                ?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull()
            StatusInfo(
                name = name?.ifEmpty { null },
                uid = uidStr?.toIntOrNull(),
                threads = threads?.takeIf { it > 0 }
            )
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Layer 5 — /proc/[pid]/comm minimal name
    // ─────────────────────────────────────────────────────────────────────────

    private fun readComm(pid: Int): String? {
        return try {
            File("/proc/$pid/comm").readText().trim().ifEmpty { null }
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Layer 6 — `ps -A` via Runtime.exec
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs `ps -A -o PID,UID,COMM` and parses the output.
     * The toybox `ps` on modern Android (9+) supports `-A` to list all
     * processes and `-o` for custom columns.
     *
     * Returns a map of PID → (uid, comm).
     */
    private fun runPsAll(): Map<Int, PsEntry> {
        val result = mutableMapOf<Int, PsEntry>()
        // Try multiple command variants across different Android versions
        val commands = listOf(
            arrayOf("ps", "-A", "-o", "PID,UID,COMM"),
            arrayOf("ps", "-A", "-o", "pid,uid,comm"),
            arrayOf("ps", "-o", "PID,UID,COMM", "-A"),
            arrayOf("ps"),
            arrayOf("top", "-n", "1", "-b", "-o", "PID,UID,COMM"),
            arrayOf("toolbox", "ps", "-A", "-o", "PID,UID,COMM")
        )

        for (cmd in commands) {
            try {
                val proc = Runtime.getRuntime().exec(cmd)
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                if (output.isBlank()) continue

                val lines = output.lines().filter { it.isNotBlank() }
                var parsedCount = 0
                for (line in lines) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size < 2) continue
                    val pid = parts[0].toIntOrNull() ?: continue
                    if (pid <= 0) continue
                    val uid = parts.getOrNull(1)?.toIntOrNull() ?: -1
                    val comm = parts.getOrNull(2)?.ifEmpty { null }
                    if (pid !in result) {
                        result[pid] = PsEntry(uid, comm)
                        parsedCount++
                    }
                }
                if (parsedCount > 5) {
                    psAccessible = true
                    break // Good result, stop trying variants
                }
            } catch (_: Exception) { }
        }
        return result
    }

    private data class PsEntry(val uid: Int, val comm: String?)

    // ─────────────────────────────────────────────────────────────────────────
    //  Layer 7 — /proc/net/tcp UID extraction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse /proc/net/tcp and /proc/net/tcp6 to extract UIDs of processes
     * that have network connections. These files are ALWAYS world-readable on
     * every Android version.
     *
     * Format of each line (after header):
     *   sl  local_address  rem_address  st  ...  uid  ...
     * The UID is in column 7 (0-indexed).
     */
    private fun readNetworkUids(): Set<Int> {
        val uids = mutableSetOf<Int>()
        val files = listOf("/proc/net/tcp", "/proc/net/tcp6")
        for (path in files) {
            try {
                val lines = File(path).readLines()
                for (line in lines) {
                    // Skip header
                    if (line.startsWith("  sl") || line.isBlank()) continue
                    val parts = line.trim().split("\\s+".toRegex())
                    // UID is at index 7 after splitting
                    val uid = parts.getOrNull(7)?.toIntOrNull() ?: continue
                    if (uid > 0) uids.add(uid)
                }
                if (uids.isNotEmpty()) netTcpAccessible = true
            } catch (_: Exception) { }
        }
        return uids
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Map a PID to a package name via ActivityManager. */
    private fun pidToPackage(pid: Int): String {
        try {
            val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return ""
            val procs = am.runningAppProcesses ?: return ""
            return procs.firstOrNull { it.pid == pid }?.processName ?: ""
        } catch (_: Exception) { return "" }
    }

    /** Map a UID to package names via PackageManager. */
    private fun uidToPackages(uid: Int): List<String> {
        return try {
            context?.packageManager?.getPackagesForUid(uid)?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /** Determine if a process name looks like a real Android app (not a kernel thread). */
    private fun isAppProcess(name: String): Boolean {
        return name.contains('.') || name.startsWith("com.") || name.startsWith("android.") ||
               name.startsWith("system_server") || name.startsWith("surfaceflinger") ||
               name.startsWith("mediaserver") || name.startsWith("audioserver") ||
               name.startsWith("netd") || name.startsWith("logd") || name.startsWith("adbd") ||
               name.startsWith("servicemanager") || name.startsWith("hikari") ||
               name.startsWith("zygote") || name.startsWith("webview") ||
               name.startsWith("com.") || name.startsWith("miui.") || name.startsWith("android.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main entry point
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableListOf<ProcessInfo>()
        val now = System.currentTimeMillis()
        val elapsedSec = if (lastPollTime > 0) (now - lastPollTime) / 1000f else 1f

        val ourPid = Process.myPid()
        val ourUid = Process.myUid()
        val ourPkg = context?.packageName ?: ""

        // ── Step 1: List all PIDs from /proc/ ──
        val allPids = try {
            File("/proc").listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } }
                ?.map { it.name.toInt() }?.sorted() ?: emptyList()
        } catch (_: Exception) { emptyList() }
        lastEnumeratedCount = allPids.size

        // ── Step 2: Try ps -A (fast, single exec for all processes) ──
        val psEntries = runPsAll()

        // ── Step 3: Parse /proc/net/tcp for UID info ──
        val networkUids = readNetworkUids()

        // ── Step 4: Build UID → package mapping for network UIDs ──
        val uidPkgCache = mutableMapOf<Int, String>()
        for (uid in networkUids) {
            val pkgs = uidToPackages(uid)
            if (pkgs.isNotEmpty()) {
                uidPkgCache[uid] = pkgs.first()
            }
        }

        // ── Step 5: Read own process (always works) ──
        readOwnProcess(ourPid, ourUid, ourPkg, elapsedSec)?.let { processes.add(it) }

        // ── Step 6: Read other processes ──
        val targetPids = allPids.filter { it != ourPid && it > 0 }

        // Process in batches: try stat first for CPU data, fall back to name-only
        val seenPids = mutableSetOf(ourPid)
        for (pid in targetPids) {
            if (seenPids.size >= 150) break // Safety limit

            val result = readOtherProcess(pid, seenPids, ourUid, elapsedSec, psEntries)
            if (result != null) {
                processes.add(result)
                seenPids.add(pid)
            }
        }

        // ── Step 7: Add processes from ps that we missed ──
        // (ps might list processes whose /proc files we couldn't read)
        for ((pid, entry) in psEntries) {
            if (pid in seenPids) continue
            if (pid == ourPid) continue
            if (seenPids.size >= 200) break

            val name = entry.comm ?: "pid_$pid"
            val pkg = uidPkgCache[entry.uid] ?: pidToPackage(pid)
            val uid = entry.uid

            processes.add(ProcessInfo(
                pid = pid,
                name = name,
                packageName = pkg,
                cpuPercent = 0f,
                userCpu = 0, kernelCpu = 0, totalCpu = 0,
                threads = 0, memoryRss = 0, memoryPss = null,
                priority = 0, nice = 0, state = "?",
                foreground = false, wakeups = 0, uid = uid
            ))
            seenPids.add(pid)
        }

        // ── Step 8: Add processes from /proc/net/tcp with known packages ──
        // These are apps that have network connections (definitely running)
        for ((uid, pkg) in uidPkgCache) {
            if (seenPids.size >= 250) break
            // Check if this UID's package is already in our list
            if (processes.any { it.uid == uid || it.packageName == pkg }) continue
            val pid = psEntries.entries.firstOrNull { it.value.uid == uid }?.key ?: -1
            if (pid > 0 && pid in seenPids) continue

            processes.add(ProcessInfo(
                pid = pid,
                name = pkg.substringAfterLast('.'),
                packageName = pkg,
                cpuPercent = 0f,
                userCpu = 0, kernelCpu = 0, totalCpu = 0,
                threads = 0, memoryRss = 0, memoryPss = null,
                priority = 0, nice = 0, state = "Net",
                foreground = false, wakeups = 0, uid = uid
            ))
            if (pid > 0) seenPids.add(pid)
        }

        lastPollTime = now
        lastReadSuccessCount = processes.count { it.cpuPercent > 0 || it.totalCpu > 0 }
        lastNamedCount = processes.size
        processes.sortedByDescending { it.cpuPercent }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Own process reader
    // ─────────────────────────────────────────────────────────────────────────

    private fun readOwnProcess(
        pid: Int, uid: Int, pkg: String, elapsedSec: Float
    ): ProcessInfo? {
        return try {
            val statText = File("/proc/self/stat").readText().trim()
            val fields = parseStat(statText) ?: return null
            val statusLines = try { File("/proc/self/status").readLines() } catch (_: Exception) { emptyList() }
            val threads = statusLines.firstOrNull { it.startsWith("Threads:") }
                ?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull() ?: 1
            val name = statusLines.firstOrNull { it.startsWith("Name:") }
                ?.substringAfter(":")?.trim() ?: fields.comm

            val totalCpu = (fields.utime) + (fields.stime)
            val prevTicks = prevCpuTicks[pid] ?: 0L
            val cpuPercent = if (prevTicks > 0) {
                ((totalCpu - prevTicks).toFloat() / elapsedSec).coerceIn(0f, 100f)
            } else 0f
            prevCpuTicks[pid] = totalCpu

            ProcessInfo(
                pid = pid, name = name, packageName = pkg,
                cpuPercent = cpuPercent,
                userCpu = fields.utime, kernelCpu = fields.stime, totalCpu = totalCpu,
                threads = threads,
                memoryRss = (fields.rssPages) * 4L,
                memoryPss = null,
                priority = fields.priority, nice = fields.nice,
                state = fields.state,
                foreground = true, wakeups = 0, uid = uid
            )
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Other process reader (multi-layer fallback)
    // ─────────────────────────────────────────────────────────────────────────

    private data class ResolvedProcess(
        val pid: Int,
        val name: String,
        val uid: Int,
        val pkg: String,
        val state: String,
        val threads: Int,
        val cpuPercent: Float,
        val totalCpu: Long,
        val userCpu: Long,
        val kernelCpu: Long,
        val rss: Long,
        val priority: Int,
        val nice: Int,
        val foreground: Boolean,
        val fromPs: Boolean
    )

    private fun readOtherProcess(
        pid: Int,
        seenPids: MutableSet<Int>,
        ourUid: Int,
        elapsedSec: Float,
        psEntries: Map<Int, PsEntry>
    ): ProcessInfo? {
        // ── Try Layer 1 & 2: stat (CPU data) ──
        val statText = readProcStat(pid)
        val fields = statText?.let { parseStat(it) }

        if (fields != null) {
            // Full CPU data available
            val totalCpu = fields.utime + fields.stime
            val prevTicks = prevCpuTicks[pid] ?: 0L
            val cpuPercent = if (prevTicks > 0) {
                ((totalCpu - prevTicks).toFloat() / elapsedSec).coerceIn(0f, 100f)
            } else 0f
            prevCpuTicks[pid] = totalCpu

            val comm = readComm(pid) ?: fields.comm
            val status = readStatus(pid)
            val threads = status?.threads ?: 0
            val name = status?.name ?: comm
            val uid = status?.uid ?: psEntries[pid]?.uid ?: -1
            val cmdline = readCmdline(pid)
            val friendlyName = if (cmdline != null) friendlyNameFromCmdline(cmdline) else name
            val pkg = pidToPackage(pid)

            return ProcessInfo(
                pid = pid, name = friendlyName, packageName = pkg,
                cpuPercent = cpuPercent,
                userCpu = fields.utime, kernelCpu = fields.stime, totalCpu = totalCpu,
                threads = threads,
                memoryRss = fields.rssPages * 4L,
                memoryPss = null,
                priority = fields.priority, nice = fields.nice,
                state = fields.state,
                foreground = uid == ourUid,
                wakeups = 0, uid = uid
            )
        }

        // ── Stat failed — try Layer 3: cmdline ──
        val cmdline = readCmdline(pid)
        if (cmdline != null) {
            val name = friendlyNameFromCmdline(cmdline)
            val status = readStatus(pid)
            val uid = status?.uid ?: psEntries[pid]?.uid ?: -1
            val threads = status?.threads ?: 0
            val commName = readComm(pid)
            val pkg = pidToPackage(pid)

            return ProcessInfo(
                pid = pid, name = name, packageName = pkg,
                cpuPercent = 0f, userCpu = 0, kernelCpu = 0, totalCpu = 0,
                threads = threads, memoryRss = 0, memoryPss = null,
                priority = 0, nice = 0, state = "?",
                foreground = false, wakeups = 0, uid = uid
            )
        }

        // ── Cmdline failed — try Layer 4: status only ──
        val status = readStatus(pid)
        if (status != null && status.name != null) {
            val uid = status.uid ?: psEntries[pid]?.uid ?: -1
            val threads = status.threads ?: 0
            val pkg = pidToPackage(pid)

            return ProcessInfo(
                pid = pid, name = status.name, packageName = pkg,
                cpuPercent = 0f, userCpu = 0, kernelCpu = 0, totalCpu = 0,
                threads = threads, memoryRss = 0, memoryPss = null,
                priority = 0, nice = 0, state = "?",
                foreground = false, wakeups = 0, uid = uid
            )
        }

        // ── Status failed — try Layer 5: comm only ──
        val comm = readComm(pid)
        if (comm != null && isAppProcess(comm)) {
            val uid = psEntries[pid]?.uid ?: -1
            val pkg = pidToPackage(pid)
            return ProcessInfo(
                pid = pid, name = comm, packageName = pkg,
                cpuPercent = 0f, userCpu = 0, kernelCpu = 0, totalCpu = 0,
                threads = 0, memoryRss = 0, memoryPss = null,
                priority = 0, nice = 0, state = "?",
                foreground = false, wakeups = 0, uid = uid
            )
        }

        return null
    }
}
