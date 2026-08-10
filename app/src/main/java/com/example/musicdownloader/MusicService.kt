package com.example.musicdownloader

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import androidx.media3.session.CommandButton
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.CacheControl
import okhttp3.OkHttpClient

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Define the custom command constant
    companion object {
        val PLAY_STREAM_COMMAND = SessionCommand("PLAY_STREAM", Bundle())
        val PLAY_CUSTOM_MIX_COMMAND = SessionCommand("PLAY_CUSTOM_MIX", Bundle())
        val GET_SESSION_ID_COMMAND = SessionCommand("GET_SESSION_ID", Bundle())
        val ADD_TO_LIBRARY_COMMAND = SessionCommand("ADD_TO_LIBRARY", Bundle())
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AppLogger.log("[Service] onCreate")

        // DTECH Notification Icon
        setMediaNotificationProvider(CustomNotificationProvider())

        CookieManager.checkAndLogCookies(this)

        // 1. Base DataSource Factory (Global)
        // We now primarily play local files, but keep network capabilities for robustness.
        val userAgent = NetworkUtils.USER_AGENT

        // CRITICAL: Use DefaultHttpDataSource with Cookies and Cross-Protocol Redirects
        // This matches the robust configuration used in onCustomCommand for direct streams.
        val cookie = CookieManager.getCookie(this)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Cookie" to cookie))

        // Wrap in DefaultDataSource.Factory to support File URIs
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // ResolvingDataSource to handle dtech://stream/{id}
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(defaultDataSourceFactory, object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                // Determine if we need to resolve this URI
                var videoId: String? = null

                if (dataSpec.uri.scheme == "dtech") {
                    // Case 1: dtech://stream/VIDEO_ID (Host = stream)
                    if (dataSpec.uri.host == "stream") {
                        videoId = dataSpec.uri.lastPathSegment
                    }
                    // Case 2: dtech:///stream/VIDEO_ID (Host = null, Path starts with stream)
                    else if (dataSpec.uri.pathSegments.firstOrNull() == "stream") {
                        videoId = dataSpec.uri.lastPathSegment
                    }
                }

                if (!videoId.isNullOrBlank() && videoId != "stream") {
                    try {
                        AppLogger.log("[Service] Resolving dtech URI for videoId: $videoId")
                        // Resolve URL synchronously (blocking is allowed here)
                        val streamInfo = runBlocking {
                            MusicRepository.getStreamUrlWithCache(this@MusicService, videoId!!, "https://www.youtube.com/watch?v=$videoId")
                        }
                        if (streamInfo.url.isNotBlank()) {
                            AppLogger.log("[Service] Resolved dtech URI to: ${streamInfo.url}")
                            return dataSpec.buildUpon().setUri(Uri.parse(streamInfo.url)).build()
                        } else {
                            AppLogger.log("[Service] Failed to resolve dtech URI: Stream URL is blank")
                        }
                    } catch (e: Exception) {
                        AppLogger.log("[Service] Failed to resolve dtech URI: ${e.message}")
                    }
                }
                return dataSpec
            }
        })

        // 2. Load Control (Buffering Optimization)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // minBufferMs
                30_000, // maxBufferMs
                500,    // bufferForPlaybackMs
                1000    // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 4. Player Build
        // Removed HlsMediaSource.Factory enforcement since we are playing local files which might not be HLS.
        // ExoPlayer's default MediaSourceFactory handles local files better.
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(resolvingDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()

        // 5. Session Activity (Notification Click)
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 6. MediaSession Build
        val addToLibraryButton = CommandButton.Builder()
            .setDisplayName("Add to Library")
            .setIconResId(R.drawable.ic_add_to_library)
            .setSessionCommand(ADD_TO_LIBRARY_COMMAND)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(listOf(addToLibraryButton))
            .setCallback(CustomMediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        AppLogger.log("[Service] onGetSession for package: ${controllerInfo.packageName}")
        return mediaSession
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private inner class CustomNotificationProvider : androidx.media3.session.DefaultMediaNotificationProvider(this) {
        init {
            setSmallIcon(R.drawable.dtech_logo)
        }
    }

    override fun onDestroy() {
        AppLogger.log("[Service] onDestroy")
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {

        // --- CRITICAL FIX: Whitelist the Custom Command ---
        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            
            // Add our custom PLAY_STREAM command to the allowed list
            val sessionCommands = SessionCommands.Builder()
                .add(PLAY_STREAM_COMMAND)
                .add(PLAY_CUSTOM_MIX_COMMAND)
                .add(GET_SESSION_ID_COMMAND)
                .add(ADD_TO_LIBRARY_COMMAND)
                .build()

            // Allow all standard player commands (Play, Pause, etc.) + Custom Commands
            val playerCommands = Player.Commands.Builder().addAllCommands().build()

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == PLAY_CUSTOM_MIX_COMMAND.customAction) {
                // Handled implicitly by controller.setMediaItems, keeping this open for potential specific handling later.
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == PLAY_STREAM_COMMAND.customAction) {
                val url = args.getString("url")

                // --- CRITICAL FIX: Extract Metadata from Bundle ---
                val title = args.getString("TITLE")
                val artist = args.getString("ARTIST")
                val artworkUri = args.getString("ARTWORK_URI")
                val mediaId = args.getString("MEDIA_ID") ?: ""
                val startPosMs = args.getLong("START_POSITION_MS", 0L)
                val endPosMs = args.getLong("END_POSITION_MS", Long.MIN_VALUE)

                if (url != null) {
                    serviceScope.launch(Dispatchers.Main) {
                        try {
                            // 1. Configure the Network Client (Cookies + UserAgent)
                            val cookie = CookieManager.getCookie(this@MusicService)
                            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                                .setUserAgent(NetworkUtils.USER_AGENT)
                                .setAllowCrossProtocolRedirects(true)
                                .setDefaultRequestProperties(mapOf("Cookie" to cookie))

                            // DefaultDataSource automatically switches between ContentDataSource, FileDataSource, and HttpDataSource
                            val defaultDataSourceFactory = DefaultDataSource.Factory(this@MusicService, httpDataSourceFactory)

                            // 2. Use Universal Factory (Handles both Local MP4s and Network HLS)
                            // CRITICAL: We use DefaultMediaSourceFactory, NOT HlsMediaSource.Factory
                            val mediaSourceFactory = DefaultMediaSourceFactory(this@MusicService)
                                .setDataSourceFactory(defaultDataSourceFactory)

                            // 3. Construct Metadata
                            val metadataBuilder = MediaMetadata.Builder()
                            if (title != null) metadataBuilder.setTitle(title)
                            if (artist != null) metadataBuilder.setArtist(artist)
                            if (artworkUri != null) metadataBuilder.setArtworkUri(Uri.parse(artworkUri))
                            metadataBuilder.setAlbumTitle("DTECH MUSIC")
                            metadataBuilder.setSubtitle("DTECH MUSIC")
                            val metadata = metadataBuilder.build()

                            // 4. Build Media Item with Metadata
                            val mediaItemBuilder = MediaItem.Builder()
                                .setUri(url)
                                .setMediaId(mediaId)
                                .setMediaMetadata(metadata)

                            // Re-apply clipping configuration if present
                            if (startPosMs > 0L || endPosMs > Long.MIN_VALUE) {
                                mediaItemBuilder.setClippingConfiguration(
                                    MediaItem.ClippingConfiguration.Builder()
                                        .setStartPositionMs(startPosMs)
                                        .setEndPositionMs(endPosMs)
                                        .build()
                                )
                            }

                            val mediaItem = mediaItemBuilder.build()

                            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

                            player.setMediaSource(mediaSource)
                            player.prepare()
                            player.play()

                            Log.d("MusicService", "Player configured with Universal DefaultMediaSourceFactory.")

                        } catch (e: Exception) {
                            Log.e("MusicService", "Error preparing player: ${e.message}")
                        }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == GET_SESSION_ID_COMMAND.customAction) {
                val extras = Bundle().apply {
                    putInt("AUDIO_SESSION_ID", player.audioSessionId)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
            } else if (customCommand.customAction == ADD_TO_LIBRARY_COMMAND.customAction) {
                // Handle Add to Library directly from notification
                val currentMediaItem = player.currentMediaItem
                if (currentMediaItem != null && !currentMediaItem.mediaId.isNullOrBlank()) {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val videoItem = VideoItem(
                                id = currentMediaItem.mediaId,
                                title = currentMediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                                uploader = currentMediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
                                thumbnailUrl = currentMediaItem.mediaMetadata.artworkUri?.toString() ?: "",
                                duration = "", // Duration can be empty for this logic
                                webUrl = "https://www.youtube.com/watch?v=${currentMediaItem.mediaId}"
                            )
                            MusicRepository.addToLibrary(this@MusicService, videoItem)
                            AppLogger.log("[Service] Added to library from notification: ${videoItem.title}")
                        } catch (e: Exception) {
                            AppLogger.log("[Service] Error adding to library: ${e.message}")
                        }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // Handle dynamically added queue items ensuring they resolve properly
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
            val updatedMediaItems = mediaItems.map { item ->
                // Ensure the URI is maintained across the IPC boundary for our dtech scheme
                // The ResolvingDataSource we set up in onCreate will handle the actual resolution
                item.buildUpon()
                    .setUri(item.localConfiguration?.uri ?: item.mediaId.let { android.net.Uri.parse("dtech://stream/$it") })
                    .build()
            }.toMutableList()

            return com.google.common.util.concurrent.Futures.immediateFuture(updatedMediaItems)
        }
    }
}
