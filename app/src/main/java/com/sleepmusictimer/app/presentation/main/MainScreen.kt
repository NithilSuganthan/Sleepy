package com.sleepmusictimer.app.presentation.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepmusictimer.app.domain.model.TimerState
import com.sleepmusictimer.app.domain.model.progress
import com.sleepmusictimer.app.domain.model.remainingMinutes
import com.sleepmusictimer.app.service.PlaybackStateInfo
import com.sleepmusictimer.app.service.MediaNotificationListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val timerState by viewModel.timerState.collectAsState()
    val playbackInfo by viewModel.playbackStateInfo.collectAsState()
    val isConnected by viewModel.isNotificationListenerConnected.collectAsState()
    val customDuration by viewModel.customDurationMinutes.collectAsState()
    var showCustomDurationDialog by remember { mutableStateOf(false) }

    val isRunning = timerState is TimerState.Running || timerState is TimerState.Paused

    // Dynamic AMOLED dark space dimming background
    val animatedBgColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFF07090F) else Color(0xFF0B0E17),
        animationSpec = tween(1000),
        label = "bgColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sleepy",
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = animatedBgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Permission Alert Banner if Notification Access is not granted
            if (!isConnected && !isRunning) {
                NotificationAccessBanner(context)
            }

            // Circular Countdown Display Card (Scales up when running)
            val ringSize by animateDpAsState(
                targetValue = if (isRunning) 300.dp else 250.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "ringSize"
            )

            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimerDisplay(
                    timerState = timerState,
                    ringSize = ringSize,
                    onCancel = { viewModel.cancelTimer() },
                    onAdd10 = { viewModel.add10Minutes() },
                    onPause = { viewModel.pauseTimer() },
                    onResume = { viewModel.resumeTimer() }
                )
            }

            // Presets and Custom Picker Section (Hidden when timer is running)
            AnimatedVisibility(
                visible = !isRunning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sleep Profiles shortcuts
                    Text(
                        "Sleep Profiles",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SleepProfilesRow(onProfileSelect = { viewModel.startTimer(it) })

                    Spacer(modifier = Modifier.height(4.dp))

                    // Quick Presets Title
                    Text(
                        "Quick Presets",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val presets = listOf(15, 30, 45, 60, 90, 120)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(115.dp)
                    ) {
                        items(presets) { minutes ->
                            PresetChip(
                                minutes = minutes,
                                isSelected = customDuration == minutes,
                                onClick = {
                                    viewModel.setCustomDuration(minutes)
                                    viewModel.startTimer(minutes)
                                }
                            )
                        }
                        item {
                            Card(
                                onClick = { showCustomDurationDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.04f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Custom", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }

                    if (showCustomDurationDialog) {
                        AlertDialog(
                            onDismissRequest = { showCustomDurationDialog = false },
                            title = { Text("Set Custom Duration", color = Color.White) },
                            text = {
                                var textValue by remember { mutableStateOf(customDuration.toString()) }
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) textValue = it },
                                    label = { Text("Minutes (1-480)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )
                                LaunchedEffect(textValue) {
                                    val mins = textValue.toIntOrNull() ?: customDuration
                                    if (mins in 1..480) {
                                        viewModel.setCustomDuration(mins)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showCustomDurationDialog = false }) {
                                    Text("Done", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            containerColor = Color(0xFF1A1D26),
                            shape = RoundedCornerShape(24.dp)
                        )
                    }

                    // Custom Snap Wheel Picker
                    Text(
                        "Duration Wheel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    WheelDurationPicker(
                        selectedValue = customDuration,
                        onValueSelected = { viewModel.setCustomDuration(it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.startTimer(customDuration) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Start Sleep Timer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Current Playback Glass Card
            PlaybackCard(
                playbackInfo = playbackInfo,
                onPlayPause = { viewModel.togglePlayback(context) },
                onNext = { viewModel.nextTrack(context) },
                onPrev = { viewModel.prevTrack(context) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun NotificationAccessBanner(context: Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Notification Access Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    "Required to pause external players. Tapping Setup takes you to system authorization.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )
            }
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Setup", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun TimerDisplay(
    timerState: TimerState,
    ringSize: androidx.compose.ui.unit.Dp,
    onCancel: () -> Unit,
    onAdd10: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val progressColor = MaterialTheme.colorScheme.primary
    val trackColor = Color.White.copy(alpha = 0.04f)

    var remainingMillis by remember { mutableLongStateOf(0L) }
    var initialDuration by remember { mutableLongStateOf(1L) }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(timerState, lifecycleOwner) {
        if (timerState is TimerState.Running) {
            initialDuration = timerState.totalDurationMillis.coerceAtLeast(1L)
            while (true) {
                val state = lifecycleOwner.lifecycle.currentState
                if (state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    remainingMillis = timerState.remainingMillis
                    if (remainingMillis <= 0) break
                    delay(100)
                } else {
                    delay(2000)
                }
            }
        } else if (timerState is TimerState.Paused) {
            remainingMillis = timerState.remainingMillis
            initialDuration = timerState.totalDurationMillis.coerceAtLeast(1L)
        } else {
            remainingMillis = 0L
        }
    }

    val progressFraction = if (timerState is TimerState.Running || timerState is TimerState.Paused) {
        remainingMillis.toFloat() / initialDuration.toFloat()
    } else {
        0f
    }

    // 60FPS Breathing Moonlight Glow Animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        // Outer Breathing Glow Aura Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(breathingScale)
                .alpha(breathingAlpha)
                .blur(20.dp)
        ) {
            drawCircle(
                color = progressColor,
                radius = size.width / 2.1f
            )
        }

        // Timer Progress Ring Canvas
        Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
            // Track base
            drawCircle(
                color = trackColor,
                style = Stroke(width = 10.dp.toPx())
            )
            // Progress Sweep Arc
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(progressColor.copy(alpha = 0.5f), progressColor, progressColor.copy(alpha = 0.5f))
                ),
                startAngle = -90f,
                sweepAngle = 360f * progressFraction,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (timerState) {
                is TimerState.Running, is TimerState.Paused -> {
                    Text(
                        text = formatTime(remainingMillis),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 46.sp,
                            fontWeight = FontWeight.ExtraLight
                        ),
                        color = Color.White
                    )
                    Text(
                        text = if (timerState is TimerState.Paused) "Timer Paused" else "Sleep Timer Active",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Floating glass controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // +10 Min Button
                        GlassActionButton(
                            text = "+10m",
                            onClick = onAdd10
                        )
                        
                        // Play/Pause Action Pill
                        Button(
                            onClick = {
                                if (timerState is TimerState.Paused) onResume() else onPause()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (timerState is TimerState.Paused) "Resume" else "Pause",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // Cancel Button
                        GlassActionButton(
                            text = "Stop",
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.error,
                            onClick = onCancel
                        )
                    }
                }
                else -> {
                    // Idle Crescent drawing in ring center
                    Canvas(modifier = Modifier.size(54.dp)) {
                        val r = size.width / 2
                        drawCircle(
                            color = Color(0xFFC5CAE9),
                            radius = r,
                            center = Offset(r, r)
                        )
                        drawCircle(
                            color = Color(0xFF0B0E17),
                            radius = r * 0.95f,
                            center = Offset(r * 1.35f, r * 0.65f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ready for Sleep",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Light
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun GlassActionButton(
    text: String,
    containerColor: Color = Color.White.copy(alpha = 0.08f),
    contentColor: Color = Color.White.copy(alpha = 0.9f),
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PresetChip(
    minutes: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.04f),
        label = "chipBg"
    )
    val textCol by animateColorAsState(
        targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
        label = "chipText"
    )

    Card(
        onClick = {
            scope.launch {
                scale.animateTo(0.92f, spring())
                scale.animateTo(1f, spring())
            }
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale.value),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = bg
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${minutes}m",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = textCol
            )
        }
    }
}

@Composable
fun SleepProfilesRow(onProfileSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SleepProfileItem(
            title = "Nap",
            duration = "15m",
            emoji = "⚡",
            modifier = Modifier.weight(1f),
            onClick = { onProfileSelect(15) }
        )
        SleepProfileItem(
            title = "Bedtime",
            duration = "45m",
            emoji = "🌙",
            modifier = Modifier.weight(1f),
            onClick = { onProfileSelect(45) }
        )
        SleepProfileItem(
            title = "Deep Sleep",
            duration = "90m",
            emoji = "🌌",
            modifier = Modifier.weight(1f),
            onClick = { onProfileSelect(90) }
        )
    }
}

@Composable
fun SleepProfileItem(
    title: String,
    duration: String,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.04f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(emoji, fontSize = 22.sp)
            Column(verticalArrangement = Arrangement.Center) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                Text(duration, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun WheelDurationPicker(
    selectedValue: Int,
    onValueSelected: (Int) -> Unit
) {
    val options = remember {
        val list = mutableListOf<Int>()
        for (i in 5..60 step 5) list.add(i)
        for (i in 70..120 step 10) list.add(i)
        for (i in 150..480 step 30) list.add(i)
        if (!list.contains(selectedValue)) {
            list.add(selectedValue)
            list.sort()
        }
        list
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = options.indexOf(selectedValue).coerceAtLeast(0))
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val centerIndex = remember { derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        if (visibleItemsInfo.isEmpty()) 0
        else {
            val center = layoutInfo.viewportEndOffset / 2
            visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index ?: 0
        }
    } }

    var lastIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(centerIndex.value) {
        if (centerIndex.value != lastIndex) {
            lastIndex = centerIndex.value
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onValueSelected(options[centerIndex.value.coerceIn(options.indices)])
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        // Selector Highlight Box
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentPadding = PaddingValues(vertical = 31.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(options) { index, minutes ->
                val isSelected = centerIndex.value == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${minutes}m",
                        fontSize = if (isSelected) 18.sp else 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaybackCard(
    playbackInfo: PlaybackStateInfo,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    if (playbackInfo.title == null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.02f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "No Active Media Detected",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                )
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album Art or Floating glow note icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (playbackInfo.albumArt != null) {
                    Image(
                        bitmap = playbackInfo.albumArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Music Playing",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Track details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = playbackInfo.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = Color.White,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = playbackInfo.artist ?: "Unknown Artist",
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = Color.White.copy(alpha = 0.5f),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatPackageName(playbackInfo.packageName),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }

            // Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatPackageName(pkg: String?): String {
    if (pkg == null) return "System Player"
    return when {
        pkg.contains("spotify") -> "Spotify"
        pkg.contains("youtube.music") -> "YouTube Music"
        pkg.contains("apple.music") -> "Apple Music"
        pkg.contains("videolan.vlc") -> "VLC"
        pkg.contains("poweramp") -> "Poweramp"
        pkg.contains("amazon.mp3") -> "Amazon Music"
        pkg.contains("sec.android.app.music") -> "Samsung Music"
        else -> pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
    }
}
