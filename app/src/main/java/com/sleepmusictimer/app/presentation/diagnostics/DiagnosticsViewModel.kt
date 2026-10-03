package com.sleepmusictimer.app.presentation.diagnostics

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepmusictimer.app.data.repository.PreferencesRepository
import com.sleepmusictimer.app.domain.TimerManager
import com.sleepmusictimer.app.domain.model.TimerState
import com.sleepmusictimer.app.service.MediaNotificationListener
import com.sleepmusictimer.app.service.PlaybackStateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemDiagnostics(
    val isNotificationAccessGranted: Boolean = false,
    val isIgnoringBattery: Boolean = false,
    val isExactAlarmGranted: Boolean = false,
    val mediaSessionCount: Int = 0,
    val isSpotifyDetected: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
    val isAlarmScheduled: Boolean = false,
    val audioFocusGranted: Boolean = false
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val timerManager: TimerManager,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    // Analytics Flows
    val timersStarted = preferencesRepository.timersStartedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val timersCompleted = preferencesRepository.timersCompletedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val pauseSuccess = preferencesRepository.pauseSuccessFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val pauseFailure = preferencesRepository.pauseFailureFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val hoursSaved = preferencesRepository.hoursSavedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Media & Active session Flows
    val playbackStateInfo: StateFlow<PlaybackStateInfo> = MediaNotificationListener.playbackStateInfo
    val lastPauseResult: StateFlow<String> = timerManager.lastPauseResult
    val lastFailureReason: StateFlow<String> = timerManager.lastFailureReason

    // Live System Checks Flow
    private val _systemDiagnostics = MutableStateFlow(SystemDiagnostics())
    val systemDiagnostics: StateFlow<SystemDiagnostics> = _systemDiagnostics.asStateFlow()

    init {
        // Run periodic system diagnostic checks while active
        viewModelScope.launch {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            while (true) {
                val isAccessGranted = MediaNotificationListener.isListenerConnected.value
                val isBatteryExempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    true
                }
                val isAlarmExempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }

                val controllers = MediaNotificationListener.getActiveControllers(context)
                val count = controllers.size
                val isSpotify = controllers.any { it.packageName.contains("spotify", ignoreCase = true) }
                
                val fgsRunning = timerManager.timerState.value is TimerState.Running
                val alarmScheduled = timerManager.isAlarmScheduled.value

                _systemDiagnostics.value = SystemDiagnostics(
                    isNotificationAccessGranted = isAccessGranted,
                    isIgnoringBattery = isBatteryExempt,
                    isExactAlarmGranted = isAlarmExempt,
                    mediaSessionCount = count,
                    isSpotifyDetected = isSpotify,
                    foregroundServiceRunning = fgsRunning,
                    isAlarmScheduled = alarmScheduled,
                    audioFocusGranted = !MediaNotificationListener.isPlaybackActive(context) // Focus intercept helper status representation
                )
                
                delay(1000)
            }
        }
    }
}
