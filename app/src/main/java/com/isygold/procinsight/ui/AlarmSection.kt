package com.isygold.procinsight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                    "Top: ${alarms.first().packageName} x${alarms.first().count}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No alarm data available without system-level permissions.",
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(alarm.packageName, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text(
                if (alarm.isExact) "EXACT" else "INEXACT",
                fontSize = 10.sp, color = if (alarm.isExact) Color(0xFFFF5252) else Color(0xFF69F0AE)
            )
        }
        Text("x${alarm.count}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
