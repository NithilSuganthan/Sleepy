package com.sleepmusictimer.app.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepmusictimer.app.domain.model.ThemePreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsState()
    val scrollState = rememberScrollState()

    var isIgnoringBattery by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Light, letterSpacing = 1.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color(0xFF0B0E17) // Starry obsidian background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Section 1: Timer Preferences
            SettingsGroupCard(title = "Timer Configuration") {
                SettingsDropdown(
                    label = "Theme Mode",
                    value = formatTheme(settings.themePreference),
                    options = listOf("System Default", "Dark Theme", "Light Theme"),
                    onSelect = { option ->
                        val theme = when (option) {
                            "Dark Theme" -> ThemePreference.DARK
                            "Light Theme" -> ThemePreference.LIGHT
                            else -> ThemePreference.SYSTEM
                        }
                        viewModel.updateTheme(theme)
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                SettingsDropdown(
                    label = "Audio Fade-out Duration",
                    value = formatFadeOut(settings.fadeOutDurationSeconds),
                    options = listOf("Off", "30 Seconds", "1 Minute", "2 Minutes", "5 Minutes"),
                    onSelect = { option ->
                        val seconds = when (option) {
                            "30 Seconds" -> 30
                            "1 Minute" -> 60
                            "2 Minutes" -> 120
                            "5 Minutes" -> 300
                            else -> 0
                        }
                        viewModel.updateFadeOutDuration(seconds)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsDropdown(
                    label = "Default Countdown duration",
                    value = "${settings.defaultDurationMinutes} minutes",
                    options = listOf("15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes", "120 minutes"),
                    onSelect = { option ->
                        val minutes = option.substringBefore(" ").toInt()
                        viewModel.updateDefaultDuration(minutes)
                    }
                )
            }

            // Section 2: Safety & Protection
            SettingsGroupCard(title = "Safety & Protection") {
                SettingsToggle(
                    label = "Headphone Safety (Pause on unplug)",
                    checked = settings.headphoneSafetyEnabled,
                    onCheckedChange = { viewModel.updateHeadphoneSafety(it) }
                )

                SettingsDropdown(
                    label = "Low Battery Stop Threshold",
                    value = if (settings.lowBatteryThreshold > 0) "${settings.lowBatteryThreshold}% Battery" else "Off",
                    options = listOf("Off", "5% Battery", "10% Battery", "15% Battery", "20% Battery"),
                    onSelect = { option ->
                        val threshold = when (option) {
                            "5% Battery" -> 5
                            "10% Battery" -> 10
                            "15% Battery" -> 15
                            "20% Battery" -> 20
                            else -> 0
                        }
                        viewModel.updateLowBatteryThreshold(threshold)
                    }
                )
            }

            // Section 3: General Options
            SettingsGroupCard(title = "General Options") {
                SettingsToggle(
                    label = "Auto-start last used timer",
                    checked = settings.autoStartLastTimer,
                    onCheckedChange = { viewModel.updateAutoStartLastTimer(it) }
                )

                SettingsToggle(
                    label = "Show progress in Quick Settings",
                    checked = settings.enableTileCountdownDisplay,
                    onCheckedChange = { viewModel.updateEnableTileCountdown(it) }
                )
            }

            // Section 4: Utilities & Diagnostics
            SettingsGroupCard(title = "Diagnostics & Tools") {
                SettingsSystemRedirect(
                    title = "Permissions Center",
                    description = "Verify exact alarms, lockscreen notifications, and whitelist optimizers.",
                    actionText = "Open",
                    onClick = onNavigateToPermissions
                )

                SettingsSystemRedirect(
                    title = "Diagnostics & Analytics Dashboard",
                    description = "Monitor system playbacks, volume levels, and check pause retry outputs.",
                    actionText = "Open",
                    onClick = onNavigateToDiagnostics
                )
            }

            // Section 5: Android System Exemptions
            SettingsGroupCard(title = "System Configuration Bypass") {
                SettingsSystemRedirect(
                    title = "Notification Access Settings",
                    description = "Binds active MediaSessions to authorize pausing Spotify, YT Music, etc.",
                    actionText = "Manage",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                )

                SettingsSystemRedirect(
                    title = "Battery Optimization Settings",
                    description = if (isIgnoringBattery) "Unrestricted (Safe from daemon kills)" else "Restricted (OS may kill the countdown)",
                    actionText = "Configure",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.4f)
        )
        
        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.04f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

@Composable
fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label, 
            fontSize = 13.sp, 
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Normal)
                Text("▼", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .background(Color(0xFF141A29))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White, fontSize = 14.sp) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Color.White)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun SettingsSystemRedirect(
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), lineHeight = 15.sp)
        }
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(actionText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTheme(theme: ThemePreference): String {
    return when (theme) {
        ThemePreference.DARK -> "Dark Theme"
        ThemePreference.LIGHT -> "Light Theme"
        ThemePreference.SYSTEM -> "System Default"
    }
}

private fun formatFadeOut(seconds: Int): String {
    return when (seconds) {
        0 -> "Off"
        30 -> "30 Seconds"
        60 -> "1 Minute"
        120 -> "2 Minutes"
        300 -> "5 Minutes"
        else -> "$seconds Seconds"
    }
}
