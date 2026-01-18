package com.example.musicdownloader

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.example.musicdownloader.data.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// Singleton to manage MediaController
object MusicControllerManager {
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var applicationContext: Context? = null

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

    // Shuffle & Repeat State
    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(androidx.media3.common.Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    fun initialize(context: Context) {
        AppLogger.log("[Controller] initialize called")
        this.applicationContext = context.applicationContext

        if (mediaController != null) {
            AppLogger.log("[Controller] Already initialized")
            return
        }

        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                AppLogger.log("[Controller] Connected to session")
                setupListeners()
            } catch (e: Exception) {
                AppLogger.log("[Controller] Connection failed: ${e.message}")
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupListeners() {
        mediaController?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AppLogger.log("[Player] onIsPlayingChanged: $isPlaying")
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
                AppLogger.log("[Player] State: $stateName")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AppLogger.log("[Player] Media Item Transition: ${mediaItem?.mediaId} (Reason: $reason)")
                _currentMediaItem.value = mediaItem
            }

            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                _duration.value = player.duration
                // Update shuffle/repeat states
                _shuffleModeEnabled.value = player.shuffleModeEnabled
                _repeatMode.value = player.repeatMode
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.log("[Player] Error: ${error.errorCodeName} - ${error.message}")
                error.printStackTrace()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleModeEnabled.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })
    }

    fun playMedia(mediaItem: MediaItem) {
        AppLogger.log("[Controller] playMedia: ${mediaItem.mediaId} via Custom Command")
        if (mediaController == null) {
            AppLogger.log("[Controller] ERROR: mediaController is null!")
            return
        }
        mediaController?.let { controller ->
            val command = SessionCommand("PLAY_STREAM", Bundle.EMPTY)
            val args = Bundle().apply {
                putString("url", mediaItem.localConfiguration?.uri.toString())
                putString("MEDIA_ID", mediaItem.mediaId)
                putString("TITLE", mediaItem.mediaMetadata.title?.toString())
                putString("ARTIST", mediaItem.mediaMetadata.artist?.toString())
                putString("ARTWORK_URI", mediaItem.mediaMetadata.artworkUri?.toString())
                putString("MIME_TYPE", mediaItem.localConfiguration?.mimeType)
            }

            controller.sendCustomCommand(command, args)
            AppLogger.log("[Controller] Custom Command sent: PLAY_STREAM")
        }
    }

    fun playPlaylist(songs: List<Song>, startIndex: Int) {
        AppLogger.log("[Controller] playPlaylist with ${songs.size} songs, starting at $startIndex")
        if (mediaController == null) {
            AppLogger.log("[Controller] ERROR: mediaController is null!")
            return
        }

        val mediaItems = songs.map { song ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(Uri.parse(song.thumbnailUrl))
                .build()

            MediaItem.Builder()
                .setUri(Uri.fromFile(File(song.filePath)))
                .setMediaId(song.id)
                .setMediaMetadata(metadata)
                .build()
        }

        mediaController?.let { controller ->
            controller.setMediaItems(mediaItems, startIndex, 0)
            controller.prepare()
            controller.play()
        }
    }

    suspend fun addToQueue(song: Song) {
        AppLogger.log("[Controller] addToQueue: ${song.title}")

        if (mediaController == null) {
            AppLogger.log("[Controller] MediaController is null, cannot add to queue")
            throw IllegalStateException("Player not initialized")
        }

        // Validate File with Smart Discovery
        val file = File(song.filePath)

        var targetFile = file
        if (!targetFile.exists()) {
            AppLogger.log("[Controller] File not found at path: ${song.filePath}, attempting smart discovery...")
            // We need to guess the parent directory. Usually it's the parent of the saved path.
            val parentDir = file.parentFile
            if (parentDir != null && parentDir.exists()) {
                 val found = parentDir.listFiles { _, name -> name.startsWith(song.id) }?.firstOrNull()
                 if (found != null) {
                     AppLogger.log("[Controller] Smart discovery found file: ${found.absolutePath}")
                     targetFile = found
                 } else {
                     AppLogger.log("[Controller] Smart discovery failed.")
                     throw java.io.FileNotFoundException("File not found for ${song.title}")
                 }
            } else {
                 // Try hardcoded path as fallback if parent is totally wrong
                 val context = applicationContext
                 if (context != null) {
                      val downloadsDir = File(context.filesDir, "music_downloads")
                      val found = downloadsDir.listFiles { _, name -> name.startsWith(song.id) }?.firstOrNull()
                      if (found != null) {
                           AppLogger.log("[Controller] Smart discovery (fallback) found file: ${found.absolutePath}")
                           targetFile = found
                      } else {
                           throw java.io.FileNotFoundException("File path invalid and cannot be recovered: ${song.filePath}")
                      }
                 } else {
                      throw java.io.FileNotFoundException("File path invalid and cannot be recovered: ${song.filePath}")
                 }
            }
        }

        try {
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(Uri.parse(song.thumbnailUrl))
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(targetFile))
                .setMediaId(song.id)
                .setMediaMetadata(metadata)
                .build()

            // Just add the item. MediaController implementation of Player returns void/Unit.
            // Operations are asynchronous but we assume command is sent.
            mediaController!!.addMediaItem(mediaItem)

            AppLogger.log("[Controller] Successfully added to queue")
        } catch (e: Exception) {
            AppLogger.log("[Controller] Exception adding to queue: ${e.message}")
            throw e
        }
    }

    fun play() {
        AppLogger.log("[Controller] play()")
        mediaController?.play()
    }

    fun pause() {
        AppLogger.log("[Controller] pause()")
        mediaController?.pause()
    }

    fun skipToNext() {
        mediaController?.seekToNext()
    }

    fun skipToPrevious() {
        mediaController?.seekToPrevious()
    }

    fun toggleShuffleMode() {
        mediaController?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun toggleRepeatMode() {
        mediaController?.let {
            val currentMode = it.repeatMode
            val newMode = when (currentMode) {
                androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ONE
                androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_ALL
                androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_OFF
                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
            it.repeatMode = newMode
        }
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

    fun launchEqualizer(context: Context) {
        try {
            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)

            // We need to pass the AudioSessionId if possible, but MediaController doesn't easily expose it safely across process.
            // Sending 0 (Global Mix) is often rejected by modern Android.
            // We try passing 0 for now as a fallback.
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, 0)
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                android.widget.Toast.makeText(context, "No Equalizer found on this device.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            AppLogger.log("[Controller] Failed to launch equalizer: ${e.message}")
            android.widget.Toast.makeText(context, "Error opening Equalizer", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // --- Audio Effects & Features ---

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.sendCustomCommand(
            SessionCommand("SET_SPEED", Bundle.EMPTY),
            Bundle().apply { putFloat("SPEED", speed) }
        )
    }

    fun setBassBoostStrength(strength: Int) {
        mediaController?.sendCustomCommand(
            SessionCommand("SET_BASS", Bundle.EMPTY),
            Bundle().apply { putInt("STRENGTH", strength) }
        )
    }

    fun setVirtualizerStrength(strength: Int) {
        mediaController?.sendCustomCommand(
            SessionCommand("SET_VIRT", Bundle.EMPTY),
            Bundle().apply { putInt("STRENGTH", strength) }
        )
    }

    fun setABLoop(aPoint: Long, bPoint: Long) {
        mediaController?.sendCustomCommand(
            SessionCommand("SET_AB", Bundle.EMPTY),
            Bundle().apply {
                putLong("A_POINT", aPoint)
                putLong("B_POINT", bPoint)
            }
        )
    }

    fun setMonoAudio(enabled: Boolean) {
        mediaController?.sendCustomCommand(
            SessionCommand("SET_MONO", Bundle.EMPTY),
            Bundle().apply { putBoolean("ENABLED", enabled) }
        )
    }
}
