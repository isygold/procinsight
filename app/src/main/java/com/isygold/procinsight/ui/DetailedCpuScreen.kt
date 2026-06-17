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
import com.isygold.procinsight.data.CpuCoreInfo
import com.isygold.procinsight.data.MonitorDiagnostics
import com.isygold.procinsight.data.Resource
import com.isygold.procinsight.data.SystemStats

@Composable
fun DetailedCpuScreen(stats: Resource<SystemStats>) {
    when (stats) {
        is Resource.Success -> {
            val cores = stats.data.perCore
            val totalAvg = if (cores.isNotEmpty()) cores.map { it.usagePercent }.average().toFloat() else 0f

            if (cores.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("CPU stats unavailable — /proc/stat may be blocked on this device",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(32.dp))
                }
                return
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                item {
                    Spacer(Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total CPU Usage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${"%.1f".format(totalAvg)}%",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    totalAvg > 80 -> Color(0xFFFF5252)
                                    totalAvg > 50 -> Color(0xFFFFA726)
                                    else -> Color(0xFF66BB6A)
                                }
                            )
                            Text(
                                "${stats.data.processes} processes · ${stats.data.threads} threads",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Diagnostics card
                    DiagnosticsCard(stats.data.diagnostics)

                    Spacer(Modifier.height(8.dp))
                    Text("Per-Core Breakdown", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }

                items(cores) { core ->
                    DetailedCpuCoreCard(core)
                    Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(diag: MonitorDiagnostics) {
    var expanded by remember { mutableStateOf(false) }

    val sourceColor = when (diag.procStatSource) {
        "direct" -> Color(0xFF66BB6A)
        "cat_exec", "sh_exec" -> Color(0xFFFFA726)
        "sysfs_only" -> Color(0xFFFF5252)
        else -> Color.Gray
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Diagnostics", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Details")
                }
            }

            // Summary line always visible
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = sourceColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        diag.procStatSource.ifEmpty { "unknown" },
                        fontSize = 10.sp,
                        color = sourceColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${diag.procStatCoreCount} cores · ${diag.processesEnumerated} PIDs · ${diag.processesReadSuccess} read",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                DetailItem("Detection source", diag.procStatSource.ifEmpty { "none" })
                Spacer(Modifier.height(4.dp))
                DetailItem("CPU lines found", "${diag.procStatCoreCount}")
                Spacer(Modifier.height(4.dp))
                DetailItem("PIDs enumerated", "${diag.processesEnumerated}")
                Spacer(Modifier.height(4.dp))
                DetailItem("PIDs read OK", "${diag.processesReadSuccess}")
                Spacer(Modifier.height(4.dp))
                DetailItem("Poll interval", "${diag.pollIntervalMs}ms")
                Spacer(Modifier.height(4.dp))
                DetailItem("/proc/wakelocks", if (diag.procWakelockAccessible) "accessible" else "blocked")
                Spacer(Modifier.height(4.dp))
                DetailItem("logcat events", if (diag.logcatAccessible) "accessible" else "blocked")
                Spacer(Modifier.height(4.dp))
                DetailItem("JobScheduler", if (diag.jobSchedulerAccessible) "accessible" else "blocked")

                if (diag.procStatSample.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Raw /proc/stat (sample):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            diag.procStatSample,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF69F0AE),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun DetailedCpuCoreCard(core: CpuCoreInfo) {
    val barColor = when {
        core.usagePercent > 80 -> Color(0xFFFF5252)
        core.usagePercent > 50 -> Color(0xFFFFA726)
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CPU${core.core}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (!core.online) {
                        Spacer(Modifier.width(8.dp))
                        Text("OFFLINE", fontSize = 10.sp, color = Color(0xFFFF5252))
                    }
                }
                Text(
                    "${"%.1f".format(core.usagePercent)}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = barColor
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(5.dp)
                ) {}
                Surface(
                    modifier = Modifier.fillMaxWidth(core.usagePercent / 100f).fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp)),
                    color = barColor,
                    shape = RoundedCornerShape(5.dp)
                ) {}
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DetailItem("Freq", "${core.frequencyKhz / 1000} MHz", Modifier.weight(1f))
                DetailItem("Min", "${core.minFreq / 1000} MHz", Modifier.weight(1f))
                DetailItem("Max", "${core.maxFreq / 1000} MHz", Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailItem("Governor", core.governor, Modifier.weight(1f))
                DetailItem("Idle", "${core.idle}jiff", Modifier.weight(1f))
                DetailItem("Active", "${core.active}jiff", Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailItem("User", "${core.user}jiff", Modifier.weight(1f))
                DetailItem("System", "${core.system}jiff", Modifier.weight(1f))
                DetailItem("I/O Wait", "${core.iowait}jiff", Modifier.weight(1f))
            }
        }
    }
}
