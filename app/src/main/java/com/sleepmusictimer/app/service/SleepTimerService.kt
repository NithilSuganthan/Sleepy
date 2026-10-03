package com.sleepmusictimer.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sleepmusictimer.app.presentation.MainActivity
import com.sleepmusictimer.app.R
import com.sleepmusictimer.app.SleepTimerApp
import com.sleepmusictimer.app.data.repository.PreferencesRepository
import com.sleepmusictimer.app.domain.TimerManager
import com.sleepmusictimer.app.domain.model.SleepSettings
import com.sleepmusictimer.app.domain.model.TimerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SleepTimerService : Service() {

    @Inject
    lateinit var timerManager: TimerManager

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isServiceRunning = false
    private var currentSettings = SleepSettings()
    private var isReceiversRegistered = false
    private var isExpiring = false

    // Headphone Unplugged Broadcast Receiver
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                Log.d(TAG, "Headphones disconnected! Invoking timer expiration logic.")
                timerManager.onTimerExpired()
            }
        }
    }

    // Battery State Broadcast Receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_BATTERY_CHANGED == intent.action) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                 status == BatteryManager.BATTERY_STATUS_FULL
                
                if (isCharging) {
                    // Do not trigger low battery protection if plugged in/charging
                    return
                }

                if (level >= 0 && scale > 0) {
                    val batteryPercent = (level * 100 / scale.toFloat()).toInt()
                    checkBatteryThreshold(batteryPercent)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        // Listen to preferences flow for dynamic receiver settings updates
        serviceScope.launch {
            preferencesRepository.sleepSettingsFlow.collect { settings ->
                currentSettings = settings
                if (isServiceRunning) {
                    updateDynamicReceivers()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "Service onStartCommand action: $action")

        when (action) {
            ACTION_START_TIMER -> {
                isExpiring = false
                if (!isServiceRunning) {
                    isServiceRunning = true
                    startForegroundWithNotification()
                    observeTimerState()
                    updateDynamicReceivers()
                }
            }
            ACTION_STOP_TIMER -> {
                stopServiceInternal()
            }
            ACTION_CANCEL_SERVICE_TIMER -> {
                isExpiring = false
                timerManager.cancelTimer()
            }
            ACTION_ADD_10_MIN_SERVICE -> {
                isExpiring = false
                timerManager.add10Minutes()
            }
            ACTION_PAUSE_NOW_SERVICE -> {
                timerManager.onTimerExpired()
            }
            ACTION_EXPIRE_TIMER -> {
                isExpiring = true
                isServiceRunning = true
                showExpirationNotification()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val initialNotification = buildNotification("Sleepy", "Calculating remaining time...")
        startForegroundCompat(initialNotification)
    }

    private fun showExpirationNotification() {
        Log.d(TAG, "Showing expiration notification. Service remains in foreground during verification.")
        val notification = NotificationCompat.Builder(this, SleepTimerApp.CHANNEL_ID)
            .setContentTitle("Sleepy")
            .setContentText("Pausing music playback...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        startForegroundCompat(notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeTimerState() {
        serviceScope.launch {
            timerManager.timerState.collectLatest { state ->
                when (state) {
                    is TimerState.Running -> {
                        isExpiring = false
                        val minutesLeft = (state.remainingMillis / 60000).toInt() + 1
                        val progressText = if (minutesLeft > 1) {
                            "$minutesLeft minutes remaining"
                        } else {
                            "Less than a minute remaining"
                        }
                        
                        val notification = buildNotification(
                            title = "Sleepy",
                            content = progressText
                        )
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, notification)
                    }
                    else -> {
                        // Let TimerManager manage service teardown on expiration (it will invoke STOP after verification completes)
                        if (timerManager.timerState.value !is TimerState.Running && isServiceRunning && !isExpiring) {
                            Log.d(TAG, "TimerState is Idle/Paused. Stopping service.")
                            stopServiceInternal()
                        }
                    }
                }
            }
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val cancelIntent = Intent(this, SleepTimerService::class.java).apply {
            action = ACTION_CANCEL_SERVICE_TIMER
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val add10Intent = Intent(this, SleepTimerService::class.java).apply {
            action = ACTION_ADD_10_MIN_SERVICE
        }
        val add10PendingIntent = PendingIntent.getService(
            this,
            2,
            add10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val pauseNowIntent = Intent(this, SleepTimerService::class.java).apply {
            action = ACTION_PAUSE_NOW_SERVICE
        }
        val pauseNowPendingIntent = PendingIntent.getService(
            this,
            3,
            pauseNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, SleepTimerApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Secure lock screen compliance
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .addAction(android.R.drawable.ic_input_add, "+10 Min", add10PendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause Now", pauseNowPendingIntent)
            .build()
    }

    private fun checkBatteryThreshold(percent: Int) {
        val threshold = currentSettings.lowBatteryThreshold
        if (threshold > 0 && percent <= threshold) {
            Log.w(TAG, "Battery limit reached: $percent% <= $threshold%. Expirating timer.")
            timerManager.cancelTimer()
            showLowBatteryNotification(percent)
        }
    }

    private fun showLowBatteryNotification(percent: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, SleepTimerApp.CHANNEL_ID)
            .setContentTitle("Sleepy - Low Battery Protection")
            .setContentText("Timer stopped because battery level reached $percent%.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        manager.notify(LOW_BATTERY_NOTIFICATION_ID, notification)
    }

    private fun updateDynamicReceivers() {
        unregisterDynamicReceivers()
        registerDynamicReceivers()
    }

    private fun registerDynamicReceivers() {
        if (!isReceiversRegistered) {
            if (currentSettings.headphoneSafetyEnabled) {
                registerReceiver(noisyAudioReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                Log.d(TAG, "Registered ACTION_AUDIO_BECOMING_NOISY dynamically.")
            }
            if (currentSettings.lowBatteryThreshold > 0) {
                registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                Log.d(TAG, "Registered ACTION_BATTERY_CHANGED dynamically.")
            }
            isReceiversRegistered = true
        }
    }

    private fun unregisterDynamicReceivers() {
        if (isReceiversRegistered) {
            try {
                unregisterReceiver(noisyAudioReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            try {
                unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            isReceiversRegistered = false
            Log.d(TAG, "Unregistered dynamic receivers.")
        }
    }

    private fun stopServiceInternal() {
        Log.d(TAG, "Stopping service")
        isServiceRunning = false
        unregisterDynamicReceivers()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterDynamicReceivers()
        Log.d(TAG, "Service onDestroy")
    }

    companion object {
        private const val TAG = "SleepTimerService"
        private const val NOTIFICATION_ID = 9999
        private const val LOW_BATTERY_NOTIFICATION_ID = 9998

        const val ACTION_START_TIMER = "com.sleepmusictimer.app.action.START_TIMER"
        const val ACTION_STOP_TIMER = "com.sleepmusictimer.app.action.STOP_TIMER"
        
        const val ACTION_CANCEL_SERVICE_TIMER = "com.sleepmusictimer.app.action.CANCEL_SERVICE_TIMER"
        const val ACTION_ADD_10_MIN_SERVICE = "com.sleepmusictimer.app.action.ADD_10_MIN_SERVICE"
        const val ACTION_PAUSE_NOW_SERVICE = "com.sleepmusictimer.app.action.PAUSE_NOW_SERVICE"
        const val ACTION_EXPIRE_TIMER = "com.sleepmusictimer.app.action.EXPIRE_TIMER"
    }
}
