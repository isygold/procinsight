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
import com.isygold.procinsight.data.CpuCoreInfo
import com.isygold.procinsight.data.Resource
import com.isygold.procinsight.data.SystemStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedProcessScreen(stats: Resource<SystemStats>, searchQuery: String) {
    when (stats) {
        is Resource.Success -> {
            val query = searchQuery.lowercase()
            val filtered = stats.data.topCpuProcesses.filter {
                query.isBlank() || it.name.lowercase().contains(query) ||
                it.packageName.lowercase().contains(query) ||
                it.pid.toString() == query
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "All Processes (${stats.data.topCpuProcesses.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                items(filtered) { proc ->
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
                    Text(proc.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                DetailItem("PID", "${proc.pid}", Modifier.weight(1f))
                DetailItem("UID", "${proc.uid}", Modifier.weight(1f))
                DetailItem("Threads", "${proc.threads}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailItem("Priority", "${proc.priority}", Modifier.weight(1f))
                DetailItem("Nice", "${proc.nice}", Modifier.weight(1f))
                DetailItem("State", proc.state, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailItem("RSS", "${proc.memoryRss / 1024}MB", Modifier.weight(1f))
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
