package com.isygold.procinsight.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isygold.procinsight.data.Resource
import com.isygold.procinsight.data.SystemStats

@Composable
fun DashboardScreen(stats: Resource<SystemStats>) {
    when (stats) {
        is Resource.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is Resource.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFFF5252))
                    Spacer(Modifier.height(8.dp))
                    Text("Error: ${stats.message}", fontSize = 14.sp)
                }
            }
        }
        is Resource.Success -> {
            val data = stats.data
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 80.dp)
            ) {
                // Header stats
                OverviewCards(data)

                Spacer(Modifier.height(8.dp))

                // CPU section
                CpuSection(data.perCore)

                Spacer(Modifier.height(4.dp))

                // Process section
                ProcessSection(data.topCpuProcesses)

                Spacer(Modifier.height(4.dp))

                // Wake lock section
                WakeLockSection(data.topWakeLockApps)

                Spacer(Modifier.height(4.dp))

                // Alarm section
                AlarmSection(data.topAlarmApps)
            }
        }
    }
}

@Composable
private fun OverviewCards(data: SystemStats) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Memory,
            label = "CPU",
            value = "${"%.1f".format(data.totalCpuPercent)}%",
            color = Color(0xFF42A5F5)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Storage,
            label = "RAM",
            value = "${data.usedMemoryMb}/${data.totalMemoryMb}MB",
            color = Color(0xFF66BB6A)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Bolt,
            label = "Battery",
            value = "${data.batteryPercent}%",
            color = if (data.batteryPercent > 20) Color(0xFF66BB6A) else Color(0xFFFF5252)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Apps,
            label = "Processes",
            value = "${data.processes}",
            color = MaterialTheme.colorScheme.primary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Schedule,
            label = "Uptime",
            value = formatUptime(data.uptimeMs),
            color = MaterialTheme.colorScheme.tertiary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Bedtime,
            label = "Wake Locks",
            value = "${data.wakeupCount}",
            color = Color(0xFFAB47BC)
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatUptime(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    val hr = min / 60
    return if (hr > 0) "${hr}h ${min % 60}m" else "${min}m ${sec % 60}s"
}
