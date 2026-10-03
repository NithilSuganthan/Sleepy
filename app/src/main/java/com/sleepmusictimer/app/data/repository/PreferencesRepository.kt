package com.sleepmusictimer.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sleepmusictimer.app.domain.model.SleepSettings
import com.sleepmusictimer.app.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleep_settings_pref")

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_DURATION_MINUTES = intPreferencesKey("default_duration_minutes")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val FADE_OUT_DURATION_SECONDS = intPreferencesKey("fade_out_duration_seconds")
        val AUTO_START_LAST_TIMER = booleanPreferencesKey("auto_start_last_timer")
        val ENABLE_TILE_COUNTDOWN = booleanPreferencesKey("enable_tile_countdown")
        val LAST_USED_DURATION_MINUTES = intPreferencesKey("last_used_duration_minutes")
        
        // New Settings
        val HEADPHONE_SAFETY_ENABLED = booleanPreferencesKey("headphone_safety_enabled")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        
        // Recovery
        val EXPIRY_TIME_MILLIS = longPreferencesKey("expiry_time_millis")
        val ORIGINAL_VOLUME = intPreferencesKey("original_volume")
        
        // Analytics
        val TIMERS_STARTED = intPreferencesKey("timers_started")
        val TIMERS_COMPLETED = intPreferencesKey("timers_completed")
        val PAUSE_SUCCESS_COUNT = intPreferencesKey("pause_success_count")
        val PAUSE_FAILURE_COUNT = intPreferencesKey("pause_failure_count")
        val HOURS_SAVED = doublePreferencesKey("hours_saved")
    }

    val sleepSettingsFlow: Flow<SleepSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeString = preferences[PreferencesKeys.THEME_PREFERENCE] ?: ThemePreference.SYSTEM.name
            val theme = try {
                ThemePreference.valueOf(themeString)
            } catch (e: IllegalArgumentException) {
                ThemePreference.SYSTEM
            }

            SleepSettings(
                defaultDurationMinutes = preferences[PreferencesKeys.DEFAULT_DURATION_MINUTES] ?: 30,
                themePreference = theme,
                fadeOutDurationSeconds = preferences[PreferencesKeys.FADE_OUT_DURATION_SECONDS] ?: 0,
                autoStartLastTimer = preferences[PreferencesKeys.AUTO_START_LAST_TIMER] ?: false,
                enableTileCountdownDisplay = preferences[PreferencesKeys.ENABLE_TILE_COUNTDOWN] ?: true,
                lastUsedDurationMinutes = preferences[PreferencesKeys.LAST_USED_DURATION_MINUTES] ?: 30,
                headphoneSafetyEnabled = preferences[PreferencesKeys.HEADPHONE_SAFETY_ENABLED] ?: true,
                lowBatteryThreshold = preferences[PreferencesKeys.LOW_BATTERY_THRESHOLD] ?: 10,
                hasCompletedOnboarding = preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false
            )
        }

    // Recovery Flows and Getters
    val expiryTimeFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EXPIRY_TIME_MILLIS] ?: 0L
    }

    val originalVolumeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ORIGINAL_VOLUME] ?: -1
    }

    // Analytics Flows
    val timersStartedFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TIMERS_STARTED] ?: 0
    }
    val timersCompletedFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TIMERS_COMPLETED] ?: 0
    }
    val pauseSuccessFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PAUSE_SUCCESS_COUNT] ?: 0
    }
    val pauseFailureFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PAUSE_FAILURE_COUNT] ?: 0
    }
    val hoursSavedFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HOURS_SAVED] ?: 0.0
    }

    suspend fun updateDefaultDuration(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_DURATION_MINUTES] = minutes
        }
    }

    suspend fun updateThemePreference(theme: ThemePreference) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_PREFERENCE] = theme.name
        }
    }

    suspend fun updateFadeOutDuration(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FADE_OUT_DURATION_SECONDS] = seconds
        }
    }

    suspend fun updateAutoStartLastTimer(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_START_LAST_TIMER] = enabled
        }
    }

    suspend fun updateEnableTileCountdown(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_TILE_COUNTDOWN] = enabled
        }
    }

    suspend fun updateLastUsedDuration(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_USED_DURATION_MINUTES] = minutes
        }
    }

    // New Update Functions
    suspend fun updateHeadphoneSafetyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HEADPHONE_SAFETY_ENABLED] = enabled
        }
    }

    suspend fun updateLowBatteryThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOW_BATTERY_THRESHOLD] = threshold
        }
    }

    suspend fun updateHasCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    suspend fun updateExpiryTime(timeMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXPIRY_TIME_MILLIS] = timeMillis
        }
    }

    suspend fun updateOriginalVolume(volume: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ORIGINAL_VOLUME] = volume
        }
    }

    // Analytics Updates
    suspend fun incrementTimersStarted() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.TIMERS_STARTED] ?: 0
            preferences[PreferencesKeys.TIMERS_STARTED] = current + 1
        }
    }

    suspend fun incrementTimersCompleted() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.TIMERS_COMPLETED] ?: 0
            preferences[PreferencesKeys.TIMERS_COMPLETED] = current + 1
        }
    }

    suspend fun incrementPauseSuccess() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.PAUSE_SUCCESS_COUNT] ?: 0
            preferences[PreferencesKeys.PAUSE_SUCCESS_COUNT] = current + 1
        }
    }

    suspend fun incrementPauseFailure() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.PAUSE_FAILURE_COUNT] ?: 0
            preferences[PreferencesKeys.PAUSE_FAILURE_COUNT] = current + 1
        }
    }

    suspend fun addHoursSaved(hours: Double) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.HOURS_SAVED] ?: 0.0
            preferences[PreferencesKeys.HOURS_SAVED] = current + hours
        }
    }
}
