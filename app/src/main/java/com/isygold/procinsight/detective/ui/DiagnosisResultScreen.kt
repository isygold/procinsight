package com.isygold.procinsight.detective.ui

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isygold.procinsight.detective.diagnosis.DrainDiagnosis
import com.isygold.procinsight.detective.diagnosis.FixActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DiagnosisResultScreen(
    diagnosis: DrainDiagnosis,
    onRestart: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)
    ) {
        // Header
        Spacer(Modifier.height(8.dp))
        Icon(
            if (diagnosis.hasIssues) Icons.Default.Warning else Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
            tint = if (diagnosis.hasIssues) Color(0xFFFF5252) else Color(0xFF66BB6A)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (diagnosis.hasIssues) "Culprit Found!" else "All Clear!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            diagnosis.summary,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        if (diagnosis.hasIssues && diagnosis.culprit != null) {
            // Culprit card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (diagnosis.culprit.totalScore) {
                        in 0..50 -> Color(0xFF1B5E20)
                        in 51..100 -> Color(0xFFE65100)
                        in 101..200 -> Color(0xFFB71C1C)
                        else -> Color(0xFF4A0000)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFFAB00),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            diagnosis.culprit.primaryOffense,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFFFAB00)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        diagnosis.culprit.displayName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        diagnosis.culprit.packageName,
                        fontSize = 12.sp,
                        color = Color(0xFFBDBDBD)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Drain Score: ${diagnosis.culprit.totalScore} · ${diagnosis.batteryDrainEstimate}",
                        fontSize = 13.sp,
                        color = Color(0xFFBDBDBD)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Fix steps
        if (diagnosis.fixSteps.isNotEmpty()) {
            Text(
                "Recommended Fixes",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))

            diagnosis.fixSteps.forEach { step ->
                FixStepCard(step = step, context = context, culpritPkg = diagnosis.culprit?.packageName)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFF66BB6A)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No abnormal battery drain detected.\nYour device is running efficiently!",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Bottom buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Run Again")
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f)
            ) {
                Text("Done")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FixStepCard(
    step: com.isygold.procinsight.detective.diagnosis.FixStep,
    context: Context,
    culpritPkg: String?
) {
    val icon = when (step.type) {
        FixActionType.FORCE_STOP -> Icons.Default.Stop
        FixActionType.SETTING -> Icons.Default.Settings
        FixActionType.UNINSTALL -> Icons.Default.Delete
        FixActionType.ADVICE -> Icons.Default.Lightbulb
    }
    val color = when (step.type) {
        FixActionType.FORCE_STOP -> Color(0xFFFF5252)
        FixActionType.SETTING -> Color(0xFF42A5F5)
        FixActionType.UNINSTALL -> Color(0xFFEF5350)
        FixActionType.ADVICE -> Color(0xFFFFA726)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Step ${step.order}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                step.action,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                step.detail,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            if (step.type == FixActionType.FORCE_STOP && culpritPkg != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        forceStopPackage(context, culpritPkg)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Force Stop Now", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun forceStopPackage(context: Context, packageName: String) {
    try {
        Runtime.getRuntime().exec(arrayOf("am", "force-stop", packageName))
    } catch (_: Exception) { }
}
