package com.sleepmusictimer.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackStateInfo(
    val title: String? = null,
    val artist: String? = null,
    val packageName: String? = null,
    val isPlaying: Boolean = false,
    val albumArt: android.graphics.Bitmap? = null
)

class MediaNotificationListener : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private val activeCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerConnected.value = true
        Log.d(TAG, "Notification listener connected")
        registerSessionListeners()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isListenerConnected.value = false
        Log.d(TAG, "Notification listener disconnected")
        unregisterSessionListeners()
    }

    private fun registerSessionListeners() {
        try {
            val component = ComponentName(this, MediaNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                { controllers ->
                    updateControllers(controllers)
                },
                component
            )
            // Initial fetch
            updateControllers(mediaSessionManager.getActiveSessions(component))
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to register active sessions listener: ${e.message}")
        }
    }

    private fun unregisterSessionListeners() {
        // Clean up callbacks
        activeCallbacks.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {
                // Ignore
            }
        }
        activeCallbacks.clear()
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        unregisterSessionListeners()
        if (controllers.isNullOrEmpty()) {
            _playbackStateInfo.value = PlaybackStateInfo()
            return
        }

        // We listen to all active sessions, but prioritize the first playing one
        var mainController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

        mainController?.let { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    updateCurrentPlaybackInfo(controller)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    updateCurrentPlaybackInfo(controller)
                }
            }
            try {
                controller.registerCallback(callback)
                activeCallbacks[controller] = callback
            } catch (e: Exception) {
                Log.e(TAG, "Error registering callback: ${e.message}")
            }
            updateCurrentPlaybackInfo(controller)
        }
    }

    private fun updateCurrentPlaybackInfo(controller: MediaController) {
        val metadata = controller.metadata
        val state = controller.playbackState
        
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        
        // Try getting album art
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        _playbackStateInfo.value = PlaybackStateInfo(
            title = title,
            artist = artist,
            packageName = controller.packageName,
            isPlaying = isPlaying,
            albumArt = albumArt
        )
    }

    companion object {
        private const val TAG = "MediaNotificationListener"

        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        private val _playbackStateInfo = MutableStateFlow(PlaybackStateInfo())
        val playbackStateInfo: StateFlow<PlaybackStateInfo> = _playbackStateInfo.asStateFlow()

        /**
         * Fetches all active MediaControllers if Notification Access is granted.
         */
        fun getActiveControllers(context: Context): List<MediaController> {
            if (!_isListenerConnected.value) {
                Log.w(TAG, "Notification listener not connected. Cannot query active sessions.")
                return emptyList()
            }
            return try {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val component = ComponentName(context, MediaNotificationListener::class.java)
                manager.getActiveSessions(component)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException querying active sessions: ${e.message}")
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error querying active sessions: ${e.message}")
                emptyList()
            }
        }

        /**
         * Verifies if any active media controller is playing, or falls back to system audio active check.
         */
        fun isPlaybackActive(context: Context): Boolean {
            val controllers = getActiveControllers(context)
            if (controllers.isNotEmpty()) {
                val active = controllers.any { controller ->
                    try {
                        val state = controller.playbackState?.state
                        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking playback state on ${controller.packageName}: ${e.message}")
                        false
                    }
                }
                Log.d(TAG, "isPlaybackActive (Controllers check): $active")
                return active
            }
            
            // Fallback to system-level check if no controllers are visible
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val isMusicActive = audioManager.isMusicActive
            Log.d(TAG, "isPlaybackActive (System AudioManager.isMusicActive fallback): $isMusicActive")
            return isMusicActive
        }

        /**
         * Pauses active playbacks using standard pause controls, media button key injection,
         * audio focus grabbing, and volume manipulation fallbacks.
         */
        fun pauseActivePlayback(context: Context, attemptNumber: Int = 1) {
            Log.d(TAG, "[Pause Cycle] Attempt #$attemptNumber starting...")
            
            val controllers = getActiveControllers(context)
            Log.d(TAG, "Total active media sessions detected: ${controllers.size}")
            
            var isSpotifyDetected = false
            for (controller in controllers) {
                try {
                    val pkgName = controller.packageName
                    if (pkgName.contains("spotify", ignoreCase = true)) {
                        isSpotifyDetected = true
                    }
                    val state = controller.playbackState
                    val stateName = when (state?.state) {
                        PlaybackState.STATE_PLAYING -> "PLAYING"
                        PlaybackState.STATE_PAUSED -> "PAUSED"
                        PlaybackState.STATE_STOPPED -> "STOPPED"
                        else -> "STATE_${state?.state}"
                    }
                    Log.d(TAG, "Active Player: $pkgName | State: $stateName | Title: ${controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging controller state: ${e.message}")
                }
            }
            Log.d(TAG, "Spotify detected: $isSpotifyDetected")

            // Strategy 1: Standard MediaSession transportControls.pause()
            if (controllers.isNotEmpty()) {
                Log.d(TAG, "[Strategy 1] Sending transportControls.pause() to active players")
                for (controller in controllers) {
                    try {
                        controller.transportControls.pause()
                        Log.d(TAG, "Sent pause to ${controller.packageName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed transportControls.pause() on ${controller.packageName}: ${e.message}")
                    }
                }
            }

            // Strategy 2: Direct Media Button Key Injection (dispatch KEYCODE_MEDIA_PAUSE)
            if (controllers.isNotEmpty()) {
                Log.d(TAG, "[Strategy 2] Injecting KEYCODE_MEDIA_PAUSE buttons directly to players")
                for (controller in controllers) {
                    try {
                        val downEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                        controller.dispatchMediaButtonEvent(downEvent)
                        val upEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                        controller.dispatchMediaButtonEvent(upEvent)
                        Log.d(TAG, "Dispatched media pause key events to ${controller.packageName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed injecting media buttons on ${controller.packageName}: ${e.message}")
                    }
                }
            }

            // Strategy 3: Target Direct Broadcast Intent fallback for known players
            Log.d(TAG, "[Strategy 3] Broadcasting targeted KeyEvents intents")
            val targetPackages = controllers.map { it.packageName }.toMutableList()
            if (isSpotifyDetected && !targetPackages.contains("com.spotify.music")) {
                targetPackages.add("com.spotify.music")
            }
            
            for (pkg in targetPackages) {
                try {
                    val intentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
                        `package` = pkg
                    }
                    context.sendOrderedBroadcast(intentDown, null)
                    
                    val intentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
                        `package` = pkg
                    }
                    context.sendOrderedBroadcast(intentUp, null)
                    Log.d(TAG, "Sent targeted KEYCODE_MEDIA_PAUSE intent broadcast to $pkg")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed targeted key broadcast to $pkg: ${e.message}")
                }
            }

            // Strategy 4: Request Permanent Audio Focus Interruption
            Log.d(TAG, "[Strategy 4] Requesting permanent Audio Focus to intercept playback")
            requestAudioFocusPause(context)

            // Strategy 5: Force volume level to 0 if playback is still active on retries
            if (attemptNumber > 1) {
                Log.w(TAG, "[Strategy 5] Failsafe: Playback active on retry attempt $attemptNumber. Muting stream.")
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting stream volume to 0: ${e.message}")
                }
            }
        }

        /**
         * Fallback mechanism: request permanent audio focus to pause external music players.
         */
        private fun requestAudioFocusPause(context: Context) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "AudioFocus changed: $focusChange")
                    }
                    .build()
                val result = audioManager.requestAudioFocus(focusRequest)
                Log.d(TAG, "Audio Focus request returned result code: $result")
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange -> Log.d(TAG, "AudioFocus change (Legacy): $focusChange") },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                Log.d(TAG, "Legacy Audio Focus request result: $result")
            }
        }

        fun togglePlayPause(context: Context) {
            val controllers = getActiveControllers(context)
            val mainController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull()
            
            mainController?.let {
                if (it.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    it.transportControls.pause()
                } else {
                    it.transportControls.play()
                }
            }
        }

        fun skipToNext(context: Context) {
            val controllers = getActiveControllers(context)
            val mainController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull()
            mainController?.transportControls?.skipToNext()
        }

        fun skipToPrevious(context: Context) {
            val controllers = getActiveControllers(context)
            val mainController = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull()
            mainController?.transportControls?.skipToPrevious()
        }
    }
}
