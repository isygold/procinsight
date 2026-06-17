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
import com.isygold.procinsight.data.AlarmInfo
import com.isygold.procinsight.data.Resource
import com.isygold.procinsight.data.SystemStats
import com.isygold.procinsight.data.WakeLockInfo

@Composable
fun DetailedWakeupScreen(stats: Resource<SystemStats>) {
    when (stats) {
        is Resource.Success -> {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                item {
                    Spacer(Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = Color(0xFFAB47BC)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Wakeup Sources", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "${stats.data.wakeupCount} suspects · screen ${if (stats.data.diagnostics.procWakelockAccessible) "/proc/wakelocks" else "inference"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Wake Lock Suspects (${stats.data.topWakeLockApps.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Detection: screen-off CPU tracking + kernel wakelocks + logcat",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (stats.data.topWakeLockApps.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color(0xFFFFA726))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No wake lock suspects yet.\n\n" +
                                            "Turn the screen off and wait — processes that use CPU while the screen is off are likely holding wake locks. Data appears here automatically.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    items(stats.data.topWakeLockApps) { wl ->
                        DetailedWakeLockCard(wl)
                        Spacer(Modifier.height(4.dp))
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Alarms (${stats.data.topAlarmApps.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }

                if (stats.data.topAlarmApps.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color(0xFFFFA726))
                                Spacer(Modifier.height(8.dp))
                                Text("Alarm data will be available in a future update (via JobScheduler detection).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    items(stats.data.topAlarmApps) { alarm ->
                        DetailedAlarmCard(alarm)
                        Spacer(Modifier.height(4.dp))
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
private fun DetailedWakeLockCard(wl: WakeLockInfo) {
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
                        Text(wl.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(6.dp))
                        ConfidenceBadge(wl.confidence)
                    }
                    Text(
                        buildString {
                            append(wl.detectionMethod)
                            if (wl.uid > 0) append(" · UID ${wl.uid}")
                            if (wl.pid > 0) append(" · PID ${wl.pid}")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${wl.totalTimeMs / 1000}s",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (wl.isHeld) {
                        Text("HELD", fontSize = 10.sp, color = Color(0xFFFFA726))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailItem("Type", wl.type, Modifier.weight(1f))
                DetailItem("Package", wl.packageName.ifEmpty { "—" }, Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun DetailedAlarmCard(alarm: AlarmInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(alarm.packageName, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    Row {
                        Text("UID ${alarm.uid}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" · ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (alarm.isExact) "EXACT" else "inexact",
                            fontSize = 11.sp,
                            color = if (alarm.isExact) Color(0xFFFF5252) else Color(0xFF69F0AE)
                        )
                        if (alarm.isWhileIdle) {
                            Text(" · IDLE", fontSize = 11.sp, color = Color(0xFFFFA726))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("x${alarm.count}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (alarm.interval > 0) {
                        Text("every ${alarm.interval / 1000}s", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
