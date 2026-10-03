package com.sleepmusictimer.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepmusictimer.app.data.repository.PreferencesRepository
import com.sleepmusictimer.app.domain.model.SleepSettings
import com.sleepmusictimer.app.domain.model.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val settingsState: StateFlow<SleepSettings> = preferencesRepository.sleepSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SleepSettings()
        )

    fun updateTheme(theme: ThemePreference) {
        viewModelScope.launch {
            preferencesRepository.updateThemePreference(theme)
        }
    }

    fun updateDefaultDuration(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultDuration(minutes)
        }
    }

    fun updateFadeOutDuration(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.updateFadeOutDuration(seconds)
        }
    }

    fun updateAutoStartLastTimer(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateAutoStartLastTimer(enabled)
        }
    }

    fun updateEnableTileCountdown(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateEnableTileCountdown(enabled)
        }
    }

    fun updateHeadphoneSafety(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHeadphoneSafetyEnabled(enabled)
        }
    }

    fun updateLowBatteryThreshold(threshold: Int) {
        viewModelScope.launch {
            preferencesRepository.updateLowBatteryThreshold(threshold)
        }
    }
}
