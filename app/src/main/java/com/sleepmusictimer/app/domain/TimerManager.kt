package com.sleepmusictimer.app.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.sleepmusictimer.app.data.repository.PreferencesRepository
import com.sleepmusictimer.app.domain.model.SleepSettings
import com.sleepmusictimer.app.domain.model.TimerState
import com.sleepmusictimer.app.receiver.AlarmReceiver
import com.sleepmusictimer.app.service.MediaNotificationListener
import com.sleepmusictimer.app.service.SleepTimerService
import com.sleepmusictimer.app.util.AudioFadeController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val fadeController = AudioFadeController(context, preferencesRepository)
    
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    // Diagnostics Flows
    private val _lastPauseResult = MutableStateFlow("N/A")
    val lastPauseResult: StateFlow<String> = _lastPauseResult.asStateFlow()

    private val _lastFailureReason = MutableStateFlow("None")
    val lastFailureReason: StateFlow<String> = _lastFailureReason.asStateFlow()

    private val _isAlarmScheduled = MutableStateFlow(false)
    val isAlarmScheduled: StateFlow<Boolean> = _isAlarmScheduled.asStateFlow()

    private var currentSettings = SleepSettings()

    init {
        scope.launch {
            preferencesRepository.sleepSettingsFlow.collect { settings ->
                currentSettings = settings
            }
        }
        scope.launch {
            val expiry = preferencesRepository.expiryTimeFlow.first()
            val now = System.currentTimeMillis()
            if (expiry > now) {
                val remaining = expiry - now
                Log.d(TAG, "Restoring active timer on startup. Expiry: $expiry, Remaining: $remaining ms")
                _timerState.value = TimerState.Running(
                    expiryTimeMillis = expiry,
                    totalDurationMillis = remaining
                )
                // Reschedule backup alarm
                scheduleBackupAlarm(expiry)
                
                // Start active countdown job
                startCountdownJob(expiry)

                // Attempt to start FGS, gracefully catching Android 12+ FGS background start exceptions
                try {
                    startTimerService()
                    Log.d(TAG, "FGS started successfully during boot recovery.")
                } catch (e: Exception) {
                    Log.w(TAG, "FGS background launch restricted during boot recovery: ${e.message}. Falling back to backup alarm.")
                }
            } else if (expiry > 0L) {
                Log.w(TAG, "Restored timer has already expired on boot. Triggering expiration logic immediately.")
                onTimerExpired()
            }
        }
    }


    private var timerJob: kotlinx.coroutines.Job? = null

    fun startTimer(durationMinutes: Int) {
        val durationMillis = durationMinutes * 60 * 1000L
        val expiryTime = System.currentTimeMillis() + durationMillis
        
        _timerState.value = TimerState.Running(
            expiryTimeMillis = expiryTime,
            totalDurationMillis = durationMillis
        )
        
        scope.launch {
            preferencesRepository.updateLastUsedDuration(durationMinutes)
            preferencesRepository.updateExpiryTime(expiryTime)
            preferencesRepository.incrementTimersStarted()
        }

        // Schedule exact backup alarm
        scheduleBackupAlarm(expiryTime)

        // Start Foreground Service
        startTimerService()
        
        // Start active countdown job
        startCountdownJob(expiryTime)
        
        Log.d(TAG, "Timer started for $durationMinutes minutes. Expiry: $expiryTime")
    }

    private fun startCountdownJob(expiryTime: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (System.currentTimeMillis() < expiryTime) {
                val delayTime = (expiryTime - System.currentTimeMillis()).coerceAtMost(1000L)
                if (delayTime <= 0) break
                delay(delayTime)
            }
            if (_timerState.value is TimerState.Running) {
                Log.d(TAG, "Countdown job reached zero. Triggering expiration.")
                onTimerExpired()
            }
        }
    }

    fun cancelTimer() {
        Log.d(TAG, "Timer cancelled")
        timerJob?.cancel()
        cancelBackupAlarm()
        stopTimerService()
        _timerState.value = TimerState.Idle
        scope.launch {
            preferencesRepository.updateExpiryTime(0L)
            fadeController.restoreVolume()
        }
    }

    fun pauseTimer() {
        val currentState = _timerState.value
        if (currentState is TimerState.Running) {
            val remaining = currentState.remainingMillis
            timerJob?.cancel()
            cancelBackupAlarm()
            stopTimerService()
            _timerState.value = TimerState.Paused(
                remainingMillis = remaining,
                totalDurationMillis = currentState.totalDurationMillis
            )
            scope.launch {
                preferencesRepository.updateExpiryTime(0L)
            }
            Log.d(TAG, "Timer paused with $remaining ms remaining.")
        }
    }

    fun resumeTimer() {
        val currentState = _timerState.value
        if (currentState is TimerState.Paused) {
            val remaining = currentState.remainingMillis
            val newExpiryTime = System.currentTimeMillis() + remaining
            _timerState.value = TimerState.Running(
                expiryTimeMillis = newExpiryTime,
                totalDurationMillis = currentState.totalDurationMillis
            )
            scope.launch {
                preferencesRepository.updateExpiryTime(newExpiryTime)
            }
            scheduleBackupAlarm(newExpiryTime)
            startTimerService()
            startCountdownJob(newExpiryTime)
            Log.d(TAG, "Timer resumed. New expiry: $newExpiryTime")
        }
    }


    fun add10Minutes() {
        val currentState = _timerState.value
        if (currentState is TimerState.Running) {
            val extensionMillis = 10 * 60 * 1000L
            val newExpiryTime = currentState.expiryTimeMillis + extensionMillis
            val newTotalDuration = currentState.totalDurationMillis + extensionMillis
            
            _timerState.value = TimerState.Running(
                expiryTimeMillis = newExpiryTime,
                totalDurationMillis = newTotalDuration
            )
            
            scope.launch {
                preferencesRepository.updateExpiryTime(newExpiryTime)
            }
            
            // Reschedule backup alarm
            scheduleBackupAlarm(newExpiryTime)
            
            // Restart countdown job
            startCountdownJob(newExpiryTime)
            
            // Start service again to update notification immediately
            startTimerService()
            Log.d(TAG, "Added 10 minutes. New expiry: $newExpiryTime")
        }
    }

    fun onTimerExpired() {
        if (_timerState.value is TimerState.Idle) return
        Log.d(TAG, "Timer expired! Starting pause procedure.")
        
        val currentState = _timerState.value
        val totalDurationMinutes = if (currentState is TimerState.Running) {
            (currentState.totalDurationMillis / 60000.0)
        } else {
            0.0
        }

        // Stop service immediately to remove notification
        // stopTimerService() -> Moved to after verification to ensure process priority
        cancelBackupAlarm()

        val fadeDuration = currentSettings.fadeOutDurationSeconds
        
        _timerState.value = TimerState.Idle

        scope.launch(Dispatchers.Main) {
            preferencesRepository.updateExpiryTime(0L)
            preferencesRepository.incrementTimersCompleted()
            preferencesRepository.addHoursSaved(totalDurationMinutes / 60.0)

            // Update service notification to expiration state
            val expireIntent = Intent(context, SleepTimerService::class.java).apply {
                action = SleepTimerService.ACTION_EXPIRE_TIMER
            }
            context.startService(expireIntent)

            if (fadeDuration > 0) {
                Log.d(TAG, "Beginning audio fade out of $fadeDuration seconds")
                fadeController.startFadeOut(fadeDuration) {
                    performPauseAndVerification()
                }
            } else {
                Log.d(TAG, "Pausing playback immediately (no fade out)")
                performPauseAndVerification()
            }
        }
    }

    private suspend fun performPauseAndVerification() {
        var success = false
        for (attempt in 1..3) {
            Log.d(TAG, "Executing pause attempt #$attempt")
            MediaNotificationListener.pauseActivePlayback(context, attemptNumber = attempt)
            
            // Initial short delay to see if it stopped immediately
            delay(500)
            if (!MediaNotificationListener.isPlaybackActive(context)) {
                Log.d(TAG, "Playback successfully verified as paused on attempt #$attempt")
                success = true
                break
            }

            // Longer wait for players with higher latency
            delay(1000)
            
            val isPlaying = MediaNotificationListener.isPlaybackActive(context)
            Log.d(TAG, "Verification check on attempt #$attempt: isPlaying = $isPlaying")
            if (!isPlaying) {
                Log.d(TAG, "Playback successfully verified as paused on attempt #$attempt")
                success = true
                break
            }
            Log.w(TAG, "Playback is still active after attempt #$attempt. Retrying...")
        }
        
        if (success) {
            _lastPauseResult.value = "Success"
            _lastFailureReason.value = "None"
            preferencesRepository.incrementPauseSuccess()
        } else {
            _lastPauseResult.value = "Failure"
            _lastFailureReason.value = "Playback did not stop after 3 attempts. Audio was muted."
            Log.e(TAG, "Failsafe error: Music did not stop after all retries and system mute.")
            preferencesRepository.incrementPauseFailure()
        }
        
        // Stop service now that we are done!
        stopTimerService()
    }

    private fun startTimerService() {
        val intent = Intent(context, SleepTimerService::class.java).apply {
            action = SleepTimerService.ACTION_START_TIMER
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopTimerService() {
        val intent = Intent(context, SleepTimerService::class.java).apply {
            action = SleepTimerService.ACTION_STOP_TIMER
        }
        context.startService(intent)
    }

    private fun scheduleBackupAlarm(triggerAtMillis: Long) {
        _isAlarmScheduled.value = true
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_EXPIRED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback to non-exact alarm if permission not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarm permission not granted. Falling back to non-exact alarm.")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Wake up device even in Doze / idle mode
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling alarm: ${e.message}")
            // Fallback
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun cancelBackupAlarm() {
        _isAlarmScheduled.value = false
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_EXPIRED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    companion object {
        private const val TAG = "TimerManager"
        private const val ALARM_REQ_CODE = 4444
    }
}
