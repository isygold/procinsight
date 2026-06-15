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
import com.isygold.procinsight.data.MonitorInfo
import com.isygold.procinsight.data.ProcessInfo

@Composable
fun ProcessSection(processes: List<ProcessInfo>, monitorInfo: MonitorInfo = MonitorInfo()) {
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
                    Icon(Icons.Default.SensorOccupied, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("CPU Hogs (${processes.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Less" else "More")
                }
            }

            if (processes.isEmpty()) {
                Text(
                    "No process data available",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val display = if (expanded) processes else processes.take(5)
            display.forEach { proc ->
                ProcessRow(proc)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ProcessRow(proc: ProcessInfo) {
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
            Text(
                proc.name,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Row {
                Text("PID ${proc.pid}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" · ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${proc.threads}t", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (proc.memoryRss > 0) {
                    Text(" · ${proc.memoryRss / 1024}MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

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

    // Progress bar
    Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(1.5.dp)),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(1.5.dp)
        ) {}
        Surface(
            modifier = Modifier.fillMaxWidth(proc.cpuPercent / 100f).fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp)),
            color = cpuColor,
            shape = RoundedCornerShape(1.5.dp)
        ) {}
    }
}
