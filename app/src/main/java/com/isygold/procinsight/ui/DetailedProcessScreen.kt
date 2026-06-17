package com.isygold.procinsight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isygold.procinsight.data.Resource
import com.isygold.procinsight.data.SystemStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedProcessScreen(stats: Resource<SystemStats>) {
    when (stats) {
        is Resource.Success -> ProcessListContent(stats.data)
        is Resource.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error loading processes",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp)
            }
        }
        is Resource.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ProcessListContent(data: SystemStats) {
    val processes = data.topCpuProcesses
    val mi = data.monitorInfo

    // Split into tiers
    val tier1 = processes.filter { it.cpuPercent > 0f || it.totalCpu > 0 }      // full CPU data
    val tier2 = processes.filter { it.cpuPercent <= 0f && it.pid > 0 }           // name only, real PID
    val tier3 = processes.filter { it.pid < 0 }                                   // UsageStats (no PID)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "All Processes (${processes.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))

            // Detection method badges
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tier1.isNotEmpty()) {
                    SourceBadge("/proc/stat", Color(0xFF66BB6A))
                    Spacer(Modifier.width(4.dp))
                }
                if (tier2.isNotEmpty()) {
                    SourceBadge("cmdline", Color(0xFFFFA726))
                    Spacer(Modifier.width(4.dp))
                }
                if (tier3.isNotEmpty()) {
                    SourceBadge("UsageStats", Color(0xFF42A5F5))
                    Spacer(Modifier.width(4.dp))
                }
            }

            val diag = data.diagnostics
            if (diag.psAccessible || diag.netTcpAccessible) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (diag.psAccessible) {
                        SourceBadge("ps -A", Color(0xFFAB47BC))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (diag.netTcpAccessible) {
                        SourceBadge("net/tcp", Color(0xFF26A69A))
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Show hints when limited visibility
            if (processes.size <= 2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA726).copy(alpha = 0.12f))
                ) {
                    Text(
                        "Only your own process is visible with full CPU data. " +
                                "Android 11+ restricts cross-process /proc/[pid]/stat on many devices.\n" +
                                "Process names shown below come from alternative sources (cmdline, ps).",
                        fontSize = 11.sp,
                        color = Color(0xFFFFA726),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (!mi.usageStatsGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF42A5F5).copy(alpha = 0.12f))
                ) {
                    Text(
                        "Grant Usage Access in Settings → App Usage Access → ProcInsight to see recently active apps (without PID/CPU data).",
                        fontSize = 11.sp,
                        color = Color(0xFF42A5F5),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (processes.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No process data available", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@item
            }

            // Tier count summary
            Text(
                buildString {
                    append("${tier1.size} with CPU data")
                    if (tier2.isNotEmpty()) append(" · ${tier2.size} names only")
                    if (tier3.isNotEmpty()) append(" · ${tier3.size} from UsageStats")
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Spacer(Modifier.height(4.dp))
        }

        // Tier 1: Full CPU data
        items(tier1) { proc ->
            DetailedProcessRow(proc, hasCpuData = true)
            Spacer(Modifier.height(4.dp))
        }

        // Tier 2: Name only (with PID)
        if (tier2.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Processes with limited data (name from cmdline/ps):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            items(tier2) { proc ->
                DetailedProcessRow(proc, hasCpuData = false)
                Spacer(Modifier.height(4.dp))
            }
        }

        // Tier 3: UsageStats
        if (tier3.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recently active apps (from UsageStats):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            items(tier3) { proc ->
                DetailedUsageStatsRow(proc)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SourceBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            fontSize = 9.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DetailedProcessRow(proc: com.isygold.procinsight.data.ProcessInfo, hasCpuData: Boolean) {
    val cpuColor = when {
        proc.cpuPercent > 20 -> Color(0xFFFF5252)
        proc.cpuPercent > 10 -> Color(0xFFFFA726)
        else -> Color(0xFF66BB6A)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(proc.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        if (!hasCpuData) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFFFFA726).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "no CPU data",
                                    fontSize = 8.sp,
                                    color = Color(0xFFFFA726),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    if (proc.packageName.isNotBlank()) {
                        Text(proc.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (proc.pid < 0) {
                        Text("(from Usage Stats — limited data)", fontSize = 10.sp, color = Color(0xFFFFA726))
                    }
                }

                if (hasCpuData) {
                    Text(
                        "${"%.1f".format(proc.cpuPercent)}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = cpuColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (hasCpuData) {
                // Detail grid for full CPU data
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailItem("PID", if (proc.pid < 0) "—" else "${proc.pid}", Modifier.weight(1f))
                    DetailItem("UID", if (proc.uid < 0) "—" else "${proc.uid}", Modifier.weight(1f))
                    DetailItem("Threads", if (proc.threads > 0) "${proc.threads}" else "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailItem("Priority", "${proc.priority}", Modifier.weight(1f))
                    DetailItem("Nice", "${proc.nice}", Modifier.weight(1f))
                    DetailItem("State", proc.state, Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailItem("RSS", if (proc.memoryRss > 0) "${proc.memoryRss / 1024}MB" else "—", Modifier.weight(1f))
                    DetailItem("User CPU", "${proc.userCpu}jiff", Modifier.weight(1f))
                    DetailItem("Kernel CPU", "${proc.kernelCpu}jiff", Modifier.weight(1f))
                }
            } else {
                // Minimal info for limited-data processes
                Row(modifier = Modifier.fillMaxWidth()) {
                    DetailItem("PID", "${proc.pid}", Modifier.weight(1f))
                    DetailItem("UID", if (proc.uid < 0) "—" else "${proc.uid}", Modifier.weight(1f))
                    DetailItem("State", proc.state, Modifier.weight(1f))
                }
                if (proc.packageName.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        DetailItem("Package", proc.packageName, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailedUsageStatsRow(proc: com.isygold.procinsight.data.ProcessInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            proc.packageName.ifEmpty { proc.name },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF42A5F5).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "UsageStats",
                                fontSize = 8.sp,
                                color = Color(0xFF42A5F5),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (proc.packageName.isNotBlank() && proc.name != proc.packageName.substringAfterLast(".")) {
                        Text(proc.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Foreground: ${"%.1f".format(proc.totalCpu / 1000f)}s",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    proc.state,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
