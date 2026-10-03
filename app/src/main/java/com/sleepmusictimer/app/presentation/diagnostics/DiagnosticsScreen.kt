package com.sleepmusictimer.app.presentation.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val diagnostics by viewModel.systemDiagnostics.collectAsState()
    val playbackInfo by viewModel.playbackStateInfo.collectAsState()
    
    val startedCount by viewModel.timersStarted.collectAsState()
    val completedCount by viewModel.timersCompleted.collectAsState()
    val successCount by viewModel.pauseSuccess.collectAsState()
    val failureCount by viewModel.pauseFailure.collectAsState()
    val hoursSavedVal by viewModel.hoursSaved.collectAsState()

    val lastResult by viewModel.lastPauseResult.collectAsState()
    val lastReason by viewModel.lastFailureReason.collectAsState()

    val successRate = remember(successCount, failureCount) {
        val total = successCount + failureCount
        if (total > 0) {
            (successCount.toFloat() / total.toFloat() * 100).toInt()
        } else {
            100
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Analytics Statistics Card
            Text(
                "Local Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(label = "Timers Started", value = startedCount.toString(), modifier = Modifier.weight(1f))
                        StatItem(label = "Timers Completed", value = completedCount.toString(), modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(label = "Pause Success Rate", value = "$successRate%", modifier = Modifier.weight(1f))
                        StatItem(label = "Hours Saved", value = String.format("%.2f hrs", hoursSavedVal), modifier = Modifier.weight(1f))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Live Diagnostics List
            Text(
                "System Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            // Permissions Status
            DiagnosticRow(
                label = "Notification Access",
                value = if (diagnostics.isNotificationAccessGranted) "Granted" else "Not Granted",
                status = if (diagnostics.isNotificationAccessGranted) StatusLevel.GREEN else StatusLevel.RED
            )

            DiagnosticRow(
                label = "Battery Optimization",
                value = if (diagnostics.isIgnoringBattery) "Unrestricted" else "Restricted",
                status = if (diagnostics.isIgnoringBattery) StatusLevel.GREEN else StatusLevel.YELLOW
            )

            DiagnosticRow(
                label = "Exact Alarms Scheduling",
                value = if (diagnostics.isExactAlarmGranted) "Allowed" else "Denied",
                status = if (diagnostics.isExactAlarmGranted) StatusLevel.GREEN else StatusLevel.RED
            )

            // Service & Active states
            DiagnosticRow(
                label = "Foreground Service Status",
                value = if (diagnostics.foregroundServiceRunning) "Active Running" else "Idle",
                status = if (diagnostics.foregroundServiceRunning) StatusLevel.GREEN else StatusLevel.YELLOW
            )

            DiagnosticRow(
                label = "AlarmManager Backup Status",
                value = if (diagnostics.isAlarmScheduled) "Scheduled" else "Inactive",
                status = if (diagnostics.isAlarmScheduled) StatusLevel.GREEN else StatusLevel.YELLOW
            )

            DiagnosticRow(
                label = "Audio Focus Status",
                value = if (diagnostics.audioFocusGranted) "Gain (Muted/Stopped)" else "Inactive (Music active)",
                status = if (diagnostics.audioFocusGranted) StatusLevel.GREEN else StatusLevel.YELLOW
            )

            // Media session indicators
            DiagnosticRow(
                label = "Active Media Sessions Count",
                value = diagnostics.mediaSessionCount.toString(),
                status = if (diagnostics.mediaSessionCount > 0) StatusLevel.GREEN else StatusLevel.YELLOW
            )

            DiagnosticRow(
                label = "Spotify Presence Check",
                value = if (diagnostics.isSpotifyDetected) "Active Spotify" else "Inactive",
                status = if (diagnostics.isSpotifyDetected) StatusLevel.GREEN else StatusLevel.YELLOW
            )

            // Active player information
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current Player Diagnostics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Source App: ${playbackInfo.packageName ?: "None"}", fontSize = 12.sp)
                    Text("Current Track: ${playbackInfo.title ?: "No track active"}", fontSize = 12.sp)
                    Text("Artist: ${playbackInfo.artist ?: "Unknown"}", fontSize = 12.sp)
                    Text("State: ${if (playbackInfo.isPlaying) "Playing" else "Paused/Stopped"}", fontSize = 12.sp)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Last Timer Execution Results
            Text(
                "Last Timer Execution Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            DiagnosticRow(
                label = "Last Pause Execution Result",
                value = lastResult,
                status = when (lastResult) {
                    "Success" -> StatusLevel.GREEN
                    "Failure" -> StatusLevel.RED
                    else -> StatusLevel.YELLOW
                }
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Execution Log / Error Message", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(
                        text = lastReason,
                        fontSize = 12.sp,
                        color = if (lastResult == "Failure") Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

enum class StatusLevel {
    GREEN, YELLOW, RED
}

@Composable
fun DiagnosticRow(
    label: String,
    value: String,
    status: StatusLevel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (status) {
                    StatusLevel.GREEN -> Color.Green.copy(alpha = 0.15f)
                    StatusLevel.YELLOW -> Color.Yellow.copy(alpha = 0.15f)
                    StatusLevel.RED -> Color.Red.copy(alpha = 0.15f)
                },
                contentColor = when (status) {
                    StatusLevel.GREEN -> Color.Green
                    StatusLevel.YELLOW -> Color.Yellow
                    StatusLevel.RED -> Color.Red
                }
            ) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
