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
        is Resource.Success -> {
            val processes = stats.data.topCpuProcesses
            val mi = stats.data.monitorInfo
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "All Processes (${processes.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))

                    // Show hint when limited visibility
                    if (processes.size <= 2) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA726).copy(alpha = 0.12f))
                        ) {
                            Text(
                                "Only your own process is visible via /proc. Android 11+ restricts cross-process /proc reads on many devices.",
                                fontSize = 11.sp,
                                color = Color(0xFFFFA726),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))

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
                    }

                    Spacer(Modifier.height(4.dp))
                }

                if (processes.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("No process data available", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(processes) { proc ->
                    DetailedProcessRow(proc)
                    Spacer(Modifier.height(4.dp))
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
private fun DetailedProcessRow(proc: com.isygold.procinsight.data.ProcessInfo) {
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
                    Text(proc.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    if (proc.packageName.isNotBlank()) {
                        Text(proc.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (proc.pid < 0) {
                        Text("(from Usage Stats — limited data)", fontSize = 10.sp, color = Color(0xFFFFA726))
                    }
                }

                Text(
                    "${"%.1f".format(proc.cpuPercent)}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = cpuColor
                )
            }

            Spacer(Modifier.height(8.dp))

            // Detail grid
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
