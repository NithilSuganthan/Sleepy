package com.sleepmusictimer.app.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepmusictimer.app.data.repository.PreferencesRepository
import com.sleepmusictimer.app.domain.TimerManager
import com.sleepmusictimer.app.domain.model.TimerState
import com.sleepmusictimer.app.service.MediaNotificationListener
import com.sleepmusictimer.app.service.PlaybackStateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val timerManager: TimerManager,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val timerState: StateFlow<TimerState> = timerManager.timerState

    val playbackStateInfo: StateFlow<PlaybackStateInfo> = MediaNotificationListener.playbackStateInfo
    val isNotificationListenerConnected: StateFlow<Boolean> = MediaNotificationListener.isListenerConnected

    private val _customDurationMinutes = MutableStateFlow(30)
    val customDurationMinutes: StateFlow<Int> = _customDurationMinutes.asStateFlow()

    init {
        viewModelScope.launch {
            // Load last used duration to initialize custom picker
            preferencesRepository.sleepSettingsFlow.collect { settings ->
                _customDurationMinutes.value = settings.lastUsedDurationMinutes
            }
        }
    }

    fun startTimer(minutes: Int) {
        timerManager.startTimer(minutes)
    }

    fun cancelTimer() {
        timerManager.cancelTimer()
    }

    fun pauseTimer() {
        timerManager.pauseTimer()
    }

    fun resumeTimer() {
        timerManager.resumeTimer()
    }

    fun add10Minutes() {
        timerManager.add10Minutes()
    }

    fun setCustomDuration(minutes: Int) {
        _customDurationMinutes.value = minutes.coerceIn(1, 480) // 1 min to 8 hours
    }

    fun togglePlayback(context: android.content.Context) {
        MediaNotificationListener.togglePlayPause(context)
    }

    fun nextTrack(context: android.content.Context) {
        MediaNotificationListener.skipToNext(context)
    }

    fun prevTrack(context: android.content.Context) {
        MediaNotificationListener.skipToPrevious(context)
    }
}
