package com.sleepmusictimer.app.domain.model

enum class ThemePreference {
    SYSTEM, DARK, LIGHT
}

data class SleepSettings(
    val defaultDurationMinutes: Int = 30,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val fadeOutDurationSeconds: Int = 0, // 0 means Off
    val autoStartLastTimer: Boolean = false,
    val enableTileCountdownDisplay: Boolean = true,
    val lastUsedDurationMinutes: Int = 30,
    val headphoneSafetyEnabled: Boolean = true,
    val lowBatteryThreshold: Int = 10, // 0 means Disabled, else 5, 10, 15, 20
    val hasCompletedOnboarding: Boolean = false
)

