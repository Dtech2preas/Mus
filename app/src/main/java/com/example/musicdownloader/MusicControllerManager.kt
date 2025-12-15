package com.example.musicdownloader

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Singleton to manage MediaController
object MusicControllerManager {
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    // Position/Duration monitoring (simple version)
    // Real implementation would need a ticker or polling
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    fun initialize(context: Context) {
        if (mediaController != null) return

        AppLogger.log("MusicController", "Initializing MediaController...")
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                AppLogger.log("MusicController", "MediaController connected")
                setupListeners()
            } catch (e: Exception) {
                AppLogger.log("MusicController", "MediaController connection failed: ${e.message}")
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupListeners() {
        mediaController?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AppLogger.log("MusicController", "IsPlaying changed: $isPlaying")
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when(playbackState) {
                    androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                    androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                    androidx.media3.common.Player.STATE_READY -> "READY"
                    androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                AppLogger.log("MusicController", "PlaybackState changed: $stateName")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.log("MusicController", "Player Error: ${error.message}")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AppLogger.log("MusicController", "MediaItem transition. ID: ${mediaItem?.mediaId}")
                _currentMediaItem.value = mediaItem
            }

            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                _duration.value = player.duration
                // Position updates are not event-driven in the same way, usually polled
            }
        })
    }

    fun playMedia(mediaItem: MediaItem) {
        AppLogger.log("MusicController", "Sending MediaItem to player: ${mediaItem.mediaId}")
        mediaController?.let { controller ->
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        } ?: AppLogger.log("MusicController", "MediaController is null!")
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun skipToNext() {
        mediaController?.seekToNext()
    }

    fun skipToPrevious() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun release() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    // Helper to update position periodically (called from UI effect usually)
    fun updatePosition() {
        mediaController?.let {
            _currentPosition.value = it.currentPosition
            _duration.value = it.duration.coerceAtLeast(0L)
        }
    }
}
