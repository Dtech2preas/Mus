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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import okhttp3.OkHttpClient

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Define the custom command constant
    companion object {
        val PLAY_STREAM_COMMAND = SessionCommand("PLAY_STREAM", Bundle())
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
        val userAgent = "AppleCoreMedia/1.0.0.1931042321 (iPad; U; CPU OS 17_5_1 like Mac OS X; en_us)"
        val httpDataSourceFactory = OkHttpDataSource.Factory(InnerTubeClient.client)
            .setUserAgent(userAgent)

        // Wrap in DefaultDataSource.Factory to support File URIs
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

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
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
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
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(CustomMediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        AppLogger.log("[Service] onGetSession for package: ${controllerInfo.packageName}")
        return mediaSession
    }

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
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            
            // Add our custom PLAY_STREAM command to the allowed list
            val sessionCommands = SessionCommands.Builder()
                .add(PLAY_STREAM_COMMAND)
                .build()

            // Allow all standard player commands (Play, Pause, etc.) + Custom Commands
            val playerCommands = Player.Commands.Builder().addAllCommands().build()

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == PLAY_STREAM_COMMAND.customAction) {
                val url = args.getString("url")

                // --- CRITICAL FIX: Extract Metadata from Bundle ---
                val title = args.getString("TITLE")
                val artist = args.getString("ARTIST")
                val artworkUri = args.getString("ARTWORK_URI")

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
                            val metadata = metadataBuilder.build()

                            // 4. Build Media Item with Metadata
                            val mediaItem = MediaItem.Builder()
                                .setUri(url)
                                .setMediaMetadata(metadata)
                                .build()

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
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}
