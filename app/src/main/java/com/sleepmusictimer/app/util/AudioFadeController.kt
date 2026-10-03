package com.sleepmusictimer.app.util

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.sleepmusictimer.app.data.repository.PreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull

class AudioFadeController(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalVolume: Int = -1
    
    private val _isFading = MutableStateFlow(false)
    val isFading: StateFlow<Boolean> = _isFading.asStateFlow()

    /**
     * Gradually reduces the music stream volume to 0 over the given duration.
     * Restores the volume after onFadeCompleted is executed.
     */
    suspend fun startFadeOut(durationSeconds: Int, onFadeCompleted: suspend () -> Unit) {
        if (durationSeconds <= 0) {
            onFadeCompleted()
            return
        }
        
        _isFading.value = true
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val startVolume = originalVolume
        
        Log.d("AudioFadeController", "Starting volume fade out. Initial volume: $startVolume")
        
        if (startVolume <= 0) {
            _isFading.value = false
            onFadeCompleted()
            return
        }

        // Save to preferences for crash recovery
        try {
            preferencesRepository.updateOriginalVolume(startVolume)
        } catch (e: Exception) {
            Log.e("AudioFadeController", "Failed to save original volume to preferences: ${e.message}")
        }

        val totalSteps = startVolume.coerceAtLeast(1)
        val delayPerStepMs = (durationSeconds * 1000L) / totalSteps
        
        try {
            for (step in 1..totalSteps) {
                delay(delayPerStepMs)
                val newVolume = startVolume - step
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC, 
                    newVolume.coerceAtLeast(0), 
                    0
                )
            }
        } catch (e: Exception) {
            Log.e("AudioFadeController", "Fade out interrupted: ${e.message}")
            restoreVolume()
        } finally {
            _isFading.value = false
            // Execute the action (e.g. pause music) while volume is at 0
            onFadeCompleted()
            // Wait slightly for pause to take effect, then restore original volume
            delay(500)
            restoreVolume()
        }
    }
    
    suspend fun restoreVolume() {
        if (originalVolume >= 0) {
            Log.d("AudioFadeController", "Restoring original volume: $originalVolume")
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            originalVolume = -1
            try {
                preferencesRepository.updateOriginalVolume(-1)
            } catch (e: Exception) {
                // Ignore
            }
        } else {
            // Check preferences for recovery
            val savedVolume = preferencesRepository.originalVolumeFlow.firstOrNull() ?: -1
            if (savedVolume >= 0) {
                Log.d("AudioFadeController", "Recovering original volume from Preferences: $savedVolume")
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
                try {
                    preferencesRepository.updateOriginalVolume(-1)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}
