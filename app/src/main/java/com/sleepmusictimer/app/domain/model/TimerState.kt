package com.sleepmusictimer.app.domain.model

sealed interface TimerState {
    object Idle : TimerState
    
    data class Running(
        val expiryTimeMillis: Long,
        val totalDurationMillis: Long
    ) : TimerState {
        val remainingMillis: Long
            get() = (expiryTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
    }
    
    data class Paused(
        val remainingMillis: Long,
        val totalDurationMillis: Long
    ) : TimerState
}

/**
 * Extension properties for ease of access to remaining progress
 */
val TimerState.remainingMinutes: Int
    get() = when (this) {
        is TimerState.Idle -> 0
        is TimerState.Running -> (this.remainingMillis / 60000).toInt()
        is TimerState.Paused -> (this.remainingMillis / 60000).toInt()
    }

val TimerState.progress: Float
    get() = when (this) {
        is TimerState.Idle -> 0f
        is TimerState.Running -> if (totalDurationMillis > 0) this.remainingMillis.toFloat() / totalDurationMillis else 0f
        is TimerState.Paused -> if (totalDurationMillis > 0) this.remainingMillis.toFloat() / totalDurationMillis else 0f
    }

