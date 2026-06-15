package com.isygold.procinsight.detective.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isygold.procinsight.detective.analysis.DrainAnalyzer
import com.isygold.procinsight.detective.diagnosis.DrainDiagnosis
import com.isygold.procinsight.detective.diagnosis.FixSuggester
import com.isygold.procinsight.detective.session.ProfilingSession
import com.isygold.procinsight.detective.session.ProfilingState

@Composable
fun DetectiveScreen(
    profilingSession: ProfilingSession,
    onDiagnosisReady: (DrainDiagnosis) -> Unit
) {
    val state by profilingSession.state.collectAsState()
    val progress by profilingSession.progress.collectAsState()
    val dataPoints by profilingSession.dataPoints.collectAsState()

    when (state) {
        ProfilingState.IDLE -> IdleContent(onStart = { profilingSession.start() })
        ProfilingState.RUNNING -> RunningContent(
            progress = progress,
            dataPointCount = dataPoints.size,
            onStop = { profilingSession.stop() }
        )
        ProfilingState.COMPLETE -> {
            // Auto-analyze
            LaunchedEffect(dataPoints.size) {
                if (dataPoints.isNotEmpty()) {
                    val analyzer = DrainAnalyzer()
                    val report = analyzer.analyze(dataPoints)
                    val fixSuggester = FixSuggester()
                    val diagnosis = if (report.hasIssues && report.culprits.isNotEmpty()) {
                        fixSuggester.generate(report.culprits.first())
                    } else {
                        fixSuggester.noIssueDiagnosis()
                    }
                    onDiagnosisReady(diagnosis)
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun IdleContent(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Drain Detective",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "I'll monitor your device for 15 minutes,\n" +
                    "identify the battery drain culprit,\n" +
                    "and tell you exactly what to do.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start 15-Min Diagnosis", fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("What I check:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("• CPU-intensive background processes", fontSize = 11.sp)
                Text("• Apps that prevent deep sleep (wake locks)", fontSize = 11.sp)
                Text("• Excessive alarm scheduling", fontSize = 11.sp)
                Text("• Battery temperature anomalies", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RunningContent(
    progress: Float,
    dataPointCount: Int,
    onStop: () -> Unit
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        animatedProgress.animateTo(progress, animationSpec = tween(500))
    }

    val elapsedSec = (progress * 15 * 60).toInt()
    val elapsedMin = elapsedSec / 60
    val elapsedSecRem = elapsedSec % 60
    val remainingSec = ((1f - progress) * 15 * 60).toInt()
    val remainingMin = remainingSec / 60

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Monitoring...",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${elapsedMin}m ${elapsedSecRem}s elapsed · ~${remainingMin}m remaining",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier.size(200.dp),
                strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(progress * 100).toInt()}%",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${dataPointCount} samples",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Keep the app open and device awake", fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Plug in if battery is low", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onStop,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Stop Early")
        }
    }
}
