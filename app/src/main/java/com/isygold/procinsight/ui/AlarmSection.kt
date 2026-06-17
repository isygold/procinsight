package com.isygold.procinsight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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

@Composable
fun AlarmSection(alarms: List<AlarmInfo>) {
    var expanded by remember { mutableStateOf(false) }

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
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Alarms (${alarms.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show all")
                }
            }

            if (alarms.isNotEmpty()) {
                Text(
                    "Top: ${alarms.first().packageName} · ${alarms.first().operation}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No scheduled jobs. Apps with pending JobScheduler work appear here.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                alarms.forEach { alarm ->
                    AlarmRow(alarm)
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(alarm: AlarmInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(alarm.packageName, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (alarm.isExact) "EXACT" else "inexact",
                    fontSize = 10.sp,
                    color = if (alarm.isExact) Color(0xFFFF5252) else Color(0xFF69F0AE)
                )
                if (alarm.isWhileIdle) {
                    Text(" · IDLE", fontSize = 10.sp, color = Color(0xFFFFA726))
                }
                Text(" · ${alarm.operation}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("x${alarm.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (alarm.interval > 0) {
                Text("every ${alarm.interval / 1000}s", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
