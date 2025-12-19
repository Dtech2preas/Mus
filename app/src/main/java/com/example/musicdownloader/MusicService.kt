package com.example.musicdownloader

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    // Define the custom command constant
    companion object {
        val PLAY_STREAM_COMMAND = SessionCommand("PLAY_STREAM", Bundle())
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AppLogger.log("[Service] onCreate")

        // 1. Base DataSource Factory (Global)
        // We use DefaultHttpDataSource to ensure a clean slate for headers.
        val userAgent = NetworkUtils.USER_AGENT
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)

        // 2. HlsMediaSource Factory
        // Manually instantiate to force HLS handling without auto-detection issues
        val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)

        // 3. Load Control (Buffering Optimization)
        // bufferForPlaybackMs reduced to 500ms for "instant" start
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
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(hlsMediaSourceFactory)
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

    override fun onDestroy() {
        AppLogger.log("[Service] onDestroy")
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
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            
            // Check for our specific command
            if (customCommand.customAction == "PLAY_STREAM") {
                AppLogger.log("[Service] Received PLAY_STREAM command")

                val url = args.getString("URL")
                val mediaId = args.getString("MEDIA_ID") ?: ""
                val title = args.getString("TITLE")
                val artist = args.getString("ARTIST")
                val artworkUriString = args.getString("ARTWORK_URI")
                val mimeType = args.getString("MIME_TYPE")

                if (url != null) {
                    try {
                        // --- PLAYBACK LOGIC ---
                        
                        // 1. Create a FRESH DataSource Factory for this specific request
                        // Switch User-Agent to AppleCoreMedia and remove Referer to bypass strict token checks
                        val cookie = CookieManager.getCookie(this@MusicService)

                        // Only add Cookie header, NO Referer
                        val requestProps = mutableMapOf<String, String>()
                        if (cookie.isNotEmpty()) {
                            requestProps["Cookie"] = cookie
                        }

                        val dataSourceFactory = DefaultHttpDataSource.Factory()
                            .setUserAgent(NetworkUtils.USER_AGENT)
                            .setDefaultRequestProperties(requestProps)
                            .setAllowCrossProtocolRedirects(true)

                        // 2. Create HlsMediaSource
                        val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)

                        // 3. Reconstruct MediaMetadata
                        val mediaMetadataBuilder = MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)

                        if (artworkUriString != null) {
                            mediaMetadataBuilder.setArtworkUri(Uri.parse(artworkUriString))
                        }

                        // 4. Reconstruct MediaItem
                        val mediaItemBuilder = MediaItem.Builder()
                            .setUri(Uri.parse(url))
                            .setMediaId(mediaId)
                            .setMediaMetadata(mediaMetadataBuilder.build())

                        if (mimeType != null) {
                            mediaItemBuilder.setMimeType(mimeType)
                        }

                        val mediaItem = mediaItemBuilder.build()

                        // 5. Create Source & Play
                        val source = hlsFactory.createMediaSource(mediaItem)

                        // IMPORTANT: Set source, don't set item
                        player.setMediaSource(source)
                        player.prepare()
                        player.play()

                        AppLogger.log("[Service] Player configured with custom HlsMediaSource. Playing...")

                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    } catch (e: Exception) {
                        AppLogger.log("[Service] Error handling PLAY_STREAM: ${e.message}")
                        e.printStackTrace()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
                    }
                } else {
                     AppLogger.log("[Service] Error: URL is null in PLAY_STREAM command")
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}
