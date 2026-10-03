package com.sleepmusictimer.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleepmusictimer.app.domain.TimerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var timerManager: TimerManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TIMER_EXPIRED) {
            Log.d("AlarmReceiver", "Received timer expiry broadcast alarm")
            timerManager.onTimerExpired()
        }
    }

    companion object {
        const val ACTION_TIMER_EXPIRED = "com.sleepmusictimer.app.ACTION_TIMER_EXPIRED"
    }
}
