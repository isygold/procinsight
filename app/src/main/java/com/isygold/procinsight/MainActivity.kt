package com.isygold.procinsight

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isygold.procinsight.monitor.MainViewModel
import com.isygold.procinsight.service.MonitorService
import com.isygold.procinsight.ui.DashboardScreen
import com.isygold.procinsight.ui.DetailedProcessScreen
import com.isygold.procinsight.data.Resource

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF42A5F5),
                    secondary = Color(0xFF66BB6A),
                    tertiary = Color(0xFFAB47BC),
                    surface = Color(0xFF121212),
                    surfaceVariant = Color(0xFF1E1E2E),
                    background = Color(0xFF0D0D0D),
                    onSurface = Color(0xFFE0E0E0),
                    onSurfaceVariant = Color(0xFF9E9E9E)
                )
            ) {
                val viewModel: MainViewModel = viewModel()
                val stats by viewModel.stats.collectAsState()
                val isMonitoring by viewModel.isMonitoring.collectAsState()
                var selectedTab by remember { mutableStateOf(0) }
                var searchQuery by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    viewModel.startMonitoring(2000)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermission(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                }

                DisposableEffect(Unit) {
                    onDispose { viewModel.stopMonitoring() }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row {
                                    Text("ProcInsight", fontWeight = FontWeight.Bold)
                                }
                            },
                            actions = {
                                if (isMonitoring) {
                                    Icon(
                                        Icons.Default.FiberManualRecord,
                                        contentDescription = "Recording",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(onClick = {
                                    if (isMonitoring) {
                                        viewModel.stopMonitoring()
                                        stopService(Intent(this@MainActivity, MonitorService::class.java))
                                    } else {
                                        viewModel.startMonitoring()
                                        ContextCompat.startForegroundService(
                                            this@MainActivity,
                                            Intent(this@MainActivity, MonitorService::class.java)
                                        )
                                    }
                                }) {
                                    Icon(
                                        if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (isMonitoring) "Stop" else "Start"
                                    )
                                }

                                IconButton(onClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    startActivity(intent)
                                }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                label = { Text("Dashboard", fontSize = 11.sp) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                                label = { Text("CPU", fontSize = 11.sp) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                                label = { Text("Processes", fontSize = 11.sp) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Bedtime, contentDescription = null) },
                                label = { Text("Wakeups", fontSize = 11.sp) }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (selectedTab) {
                            0 -> DashboardScreen(stats)
                            1 -> DetailedCpuScreen(stats)
                            2 -> DetailedProcessScreen(stats, searchQuery)
                            3 -> DetailedWakeupScreen(stats)
                        }
                    }
                }
            }
        }
    }
}
