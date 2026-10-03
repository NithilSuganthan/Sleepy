package com.sleepmusictimer.app.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.sleepmusictimer.app.presentation.MainActivity
import com.sleepmusictimer.app.data.repository.PreferencesRepository

import com.sleepmusictimer.app.domain.TimerManager
import com.sleepmusictimer.app.domain.model.TimerState
import com.sleepmusictimer.app.domain.model.remainingMinutes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SleepTimerTileService : TileService() {

    @Inject
    lateinit var timerManager: TimerManager

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private var tileScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile onStartListening")
        tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        
        tileScope?.launch {
            timerManager.timerState.collect { state ->
                updateTileState(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Tile onStopListening")
        tileScope?.cancel()
        tileScope = null
    }

    private fun updateTileState(state: TimerState) {
        val tile = qsTile ?: return
        
        when (state) {
            is TimerState.Running -> {
                tile.state = Tile.STATE_ACTIVE
                val minutesLeft = state.remainingMinutes + 1
                tile.label = "${minutesLeft}m left"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Running"
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Sleep Music"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Tap to start"
                }
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile onClick")
        
        tileScope?.launch {
            val currentState = timerManager.timerState.value
            if (currentState is TimerState.Running) {
                // If running, cancel timer
                timerManager.cancelTimer()
            } else {
                // If not running, start with last used duration
                val settings = preferencesRepository.sleepSettingsFlow.first()
                val duration = settings.lastUsedDurationMinutes
                timerManager.startTimer(duration)
            }
        }
    }

    companion object {
        private const val TAG = "SleepTimerTileService"
    }
}
