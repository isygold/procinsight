package com.isygold.procinsight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SensorOccupied
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
import com.isygold.procinsight.data.ProcessInfo

@Composable
fun ProcessSection(processes: List<ProcessInfo>) {
    var expanded by remember { mutableStateOf(false) }

    // Split into tiers
    val tier1 = processes.filter { it.cpuPercent > 0f || it.totalCpu > 0 }      // full CPU data
    val tier2 = processes.filter { it.cpuPercent <= 0f && it.pid > 0 }           // name only, real PID
    val tier3 = processes.filter { it.pid < 0 }                                   // UsageStats (no PID)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SensorOccupied, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("CPU Hogs (${processes.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                if (processes.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Less" else "More")
                    }
                }
            }

            if (processes.isEmpty()) {
                Text(
                    "No process data available",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                return@Column
            }

            // Tier 1: Full CPU data (always shown, limited)
            val displayAll = expanded
            val showTier1 = if (displayAll) tier1 else tier1.take(3)

            showTier1.forEach { proc ->
                ProcessRow(proc, hasCpuData = true)
                Spacer(Modifier.height(4.dp))
            }

            // Tier 2: Name only (shown when expanded)
            if (displayAll && tier2.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Additional processes (limited data):",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                tier2.take(10).forEach { proc ->
                    ProcessRow(proc, hasCpuData = false)
                    Spacer(Modifier.height(2.dp))
                }
                if (tier2.size > 10) {
                    Text("+ ${tier2.size - 10} more", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Tier 3: UsageStats (shown when expanded)
            if (displayAll && tier3.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Recently used apps (UsageStats):",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                tier3.take(10).forEach { proc ->
                    UsageStatsRow(proc)
                    Spacer(Modifier.height(2.dp))
                }
                if (tier3.size > 10) {
                    Text("+ ${tier3.size - 10} more", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Summary line when collapsed
            if (!displayAll && (tier2.isNotEmpty() || tier3.isNotEmpty())) {
                Spacer(Modifier.height(4.dp))
                val extra = tier2.size + tier3.size
                Text(
                    "+ $extra processes with limited data — tap More to see all",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProcessRow(proc: ProcessInfo, hasCpuData: Boolean) {
    val cpuColor = when {
        proc.cpuPercent > 20 -> Color(0xFFFF5252)
        proc.cpuPercent > 10 -> Color(0xFFFFA726)
        else -> Color(0xFF66BB6A)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    proc.name,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (!hasCpuData) {
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color(0xFFFFA726).copy(alpha = 0.2f)
                    ) {
                        Text(
                            "limited",
                            fontSize = 8.sp,
                            color = Color(0xFFFFA726),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Row {
                if (hasCpuData) {
                    Text("PID ${proc.pid}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" · ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${proc.threads}t", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (proc.memoryRss > 0) {
                        Text(" · ${proc.memoryRss / 1024}MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("PID ${proc.pid}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (proc.uid > 0) {
                        Text(" · UID ${proc.uid}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (hasCpuData) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${"%.1f".format(proc.cpuPercent)}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = cpuColor
                )
                Text(
                    proc.state,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    // Progress bar (only for processes with CPU data)
    if (hasCpuData) {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            Surface(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(1.5.dp)),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(1.5.dp)
            ) {}
            Surface(
                modifier = Modifier.fillMaxWidth((proc.cpuPercent / 100f).coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp)),
                color = cpuColor,
                shape = RoundedCornerShape(1.5.dp)
            ) {}
        }
    }
}

@Composable
private fun UsageStatsRow(proc: ProcessInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    proc.packageName.ifEmpty { proc.name },
                    fontSize = 11.sp,
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
            Text(
                proc.state,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
