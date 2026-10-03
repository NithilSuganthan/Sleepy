package com.sleepmusictimer.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleepmusictimer.app.domain.TimerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var timerManager: TimerManager

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d("BootReceiver", "Boot completed broadcast received. Initiating TimerManager recovery.")
            // Referencing timerManager triggers Hilt initialization of the singleton, 
            // running its init block which restores scheduled alarms/timers.
        }
    }
}
