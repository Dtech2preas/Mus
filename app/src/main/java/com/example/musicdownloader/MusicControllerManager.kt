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
import com.example.musicdownloader.utils.SmartShuffleManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Singleton to manage MediaController
object MusicControllerManager {
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var applicationContext: Context? = null

    // Scope for background operations (prefetching, smart shuffle)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    // Smart Shuffle State
    private val _isSmartShuffleEnabled = MutableStateFlow(false)
    val isSmartShuffleEnabled: StateFlow<Boolean> = _isSmartShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(androidx.media3.common.Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

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
                fetchAudioSessionId()
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
                    else -> "UNKNOWN ($playbackState)"
                }
                AppLogger.log("[Player] onPlaybackStateChanged: $stateName")

                // If ended and smart shuffle is on, we might need to trigger something?
                // Usually onMediaItemTransition handles the "next song" logic better.
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                AppLogger.log("[Player] Media Item Transition. ID: ${mediaItem?.mediaId}, Title: ${mediaItem?.mediaMetadata?.title} (Reason: $reason)")
                _currentMediaItem.value = mediaItem

                // Trigger Smart Logic
                checkQueueAndPrefetch()
            }

            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                _duration.value = player.duration
                // Update shuffle/repeat states
                _shuffleModeEnabled.value = player.shuffleModeEnabled
                _repeatMode.value = player.repeatMode
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.log("[Player] ERROR OCCURRED!")
                AppLogger.log("  -> Code: ${error.errorCode} (${error.errorCodeName})")
                AppLogger.log("  -> Message: ${error.message}")
                AppLogger.log("  -> Cause: ${error.cause?.message}")
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

    private fun checkQueueAndPrefetch() {
        val controller = mediaController ?: return
        val context = applicationContext ?: return

        scope.launch {
            try {
                // 1. Smart Shuffle Logic
                if (_isSmartShuffleEnabled.value) {
                    val currentIndex = controller.currentMediaItemIndex
                    val itemCount = controller.mediaItemCount
                    val remaining = itemCount - currentIndex - 1

                    if (remaining < 4) { // Increased threshold to 4 to ensure queue is always populated
                        AppLogger.log("[Controller] Smart Shuffle: Queue running low ($remaining left). Fetching recommendation...")
                        val recommendation = SmartShuffleManager.getNextRecommendation(context)
                        if (recommendation != null) {
                            addVideoItemToQueue(recommendation)
                            AppLogger.log("[Controller] Smart Shuffle: Added ${recommendation.title}")
                        }
                    }
                }

                // 2. Prefetching Logic
                val isWifi = NetworkUtils.isWifiConnected(context)
                val lookAhead = if (isWifi) 3 else 2

                val currentIndex = controller.currentMediaItemIndex
                val itemCount = controller.mediaItemCount

                for (i in 1..lookAhead) {
                    val targetIndex = currentIndex + i
                    if (targetIndex < itemCount) {
                        val item = controller.getMediaItemAt(targetIndex)
                        val videoId = item.mediaId
                        if (videoId.isNotBlank()) {
                            AppLogger.log("[Controller] Prefetching upcoming song: $videoId (Index: $targetIndex)")
                            MusicRepository.prefetchStream(context, videoId)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.log("[Controller] Error in checkQueueAndPrefetch: ${e.message}")
            }
        }
    }

    private fun addVideoItemToQueue(video: VideoItem) {
        val metadata = MediaMetadata.Builder()
            .setTitle(video.title)
            .setArtist(video.uploader)
            .setArtworkUri(Uri.parse(video.thumbnailUrl))
            .build()

        // For queue items, we don't have the stream URL yet usually.
        // We set the URI to a "lazy" placeholder or the web URL?
        // Actually, if we use playStream logic, we construct MediaItem with stream URL.
        // But here we are adding to queue for FUTURE playback.
        // If we add webUrl as URI, the Player needs to know how to handle it when it prepares.
        // Our Service/ExoPlayer setup likely expects a direct URI or we need to intercept `prepare`.
        // However, `MusicRepository.getStreamUrl` returns a `StreamInfo`.

        // BETTER APPROACH:
        // We add it with the webUrl (or custom scheme) as the URI.
        // BUT, since we don't have a ResolvingDataSource installed in ExoPlayer easily here (it's in Service),
        // we can PRE-RESOLVE it now since we are adding it?
        // OR, we rely on the fact that we just prefetched it in `checkQueueAndPrefetch` if logic allows.
        // Let's resolve it now.

        scope.launch {
            try {
                 val context = applicationContext ?: return@launch
                 // Resolving
                 val streamInfo = MusicRepository.getStreamUrlWithCache(context, video.id, video.webUrl)

                 if (streamInfo.url.isNotBlank()) {
                     val mediaItem = MediaItem.Builder()
                        .setUri(streamInfo.url)
                        .setMediaId(video.id)
                        .setMediaMetadata(metadata)
                        .build()

                     withContext(Dispatchers.Main) {
                         mediaController?.addMediaItem(mediaItem)
                     }
                 }
            } catch (e: Exception) {
                AppLogger.log("[Controller] Failed to resolve URL for queue item: ${video.title}")
            }
        }
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

            AppLogger.log("[Controller] Sending PLAY_STREAM command with args:")
            args.keySet().forEach { key ->
                AppLogger.log("  -> $key: ${args.get(key)}")
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

            val uri = if (song.filePath.startsWith("stream://")) {
                val id = song.filePath.removePrefix("stream://")
                Uri.parse("dtech://stream/$id")
            } else {
                Uri.fromFile(File(song.filePath))
            }

            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(song.id)
                .setMediaMetadata(metadata)
                .build()
        }

        AppLogger.log("[Controller] Prepared ${mediaItems.size} MediaItems.")
        if (mediaItems.isNotEmpty()) {
             AppLogger.log("[Controller] First Item: ${mediaItems[0].mediaMetadata.title}")
        }

        mediaController?.let { controller ->
            controller.setMediaItems(mediaItems, startIndex, 0)
            controller.prepare()
            controller.play()
            AppLogger.log("[Controller] Playlist playback started.")
        }
    }

    suspend fun addToQueue(song: Song) {
        AppLogger.log("[Controller] addToQueue: ${song.title}")

        if (mediaController == null) {
            AppLogger.log("[Controller] MediaController is null, cannot add to queue")
            throw IllegalStateException("Player not initialized")
        }

        if (song.filePath.startsWith("stream://")) {
            val id = song.filePath.removePrefix("stream://")
            val uri = Uri.parse("dtech://stream/$id")

            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(Uri.parse(song.thumbnailUrl))
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(song.id)
                .setMediaMetadata(metadata)
                .build()

            mediaController!!.addMediaItem(mediaItem)
            AppLogger.log("[Controller] Successfully added stream to queue")
            return
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

    fun toggleSmartShuffle() {
        _isSmartShuffleEnabled.value = !_isSmartShuffleEnabled.value
        // If enabled, trigger a check immediately
        if (_isSmartShuffleEnabled.value) {
            checkQueueAndPrefetch()
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

    private fun fetchAudioSessionId() {
        mediaController?.let { controller ->
            val command = SessionCommand("GET_SESSION_ID", Bundle.EMPTY)
            val future = controller.sendCustomCommand(command, Bundle.EMPTY)
            future.addListener({
                try {
                    val result = future.get()
                    if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                        val id = result.extras.getInt("AUDIO_SESSION_ID")
                        AppLogger.log("[Controller] Received Audio Session ID: $id")
                        _audioSessionId.value = id
                    }
                } catch (e: Exception) {
                    AppLogger.log("[Controller] Failed to get session ID: ${e.message}")
                }
            }, MoreExecutors.directExecutor())
        }
    }

    fun launchEqualizer(context: Context) {
        try {
            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)

            // We now have the correct session ID!
            val sessionId = _audioSessionId.value
            AppLogger.log("[Controller] Launching Equalizer with Session ID: $sessionId")

            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
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

}
