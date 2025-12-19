package com.example.musicdownloader

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AppLogger.log("[Service] onCreate")

        // Configure ExoPlayer with the IOS User-Agent to avoid 403 errors from YouTube
        // The User-Agent must match what InnerTubeClient uses.
        // We use DefaultHttpDataSource to ensure a clean slate for headers.
        val userAgent = NetworkUtils.USER_AGENT
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)

        // Manually instantiate HlsMediaSource.Factory to force HLS handling without auto-detection
        val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)

        // optimize buffering for faster playback start ("instant")
        // minBufferMs: Minimum duration of media that the player attempts to buffer.
        // maxBufferMs: Maximum duration of media that the player attempts to buffer.
        // bufferForPlaybackMs: The duration of media that must be buffered for playback to start or resume following a user action such as a seek.
        // bufferForPlaybackAfterRebufferMs: The default duration of media that must be buffered for playback to resume after a rebuffer.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // minBufferMs (reduced from 50s)
                30_000, // maxBufferMs (reduced from 50s)
                500,    // bufferForPlaybackMs (reduced from 2500ms -> 500ms for instant start)
                1000    // bufferForPlaybackAfterRebufferMs (reduced from 5000ms -> 1000ms)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(hlsMediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true) // Handle audio focus
            .build()

        // Create a PendingIntent to launch the UI when the notification is clicked
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

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
        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
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
                        // Create DataSource Factory with custom headers
                        val dataSourceFactory = DefaultHttpDataSource.Factory()
                            .setUserAgent(NetworkUtils.USER_AGENT)
                            .setDefaultRequestProperties(mapOf("Referer" to "https://www.youtube.com/"))

                        // Create HlsMediaSource.Factory using the custom dataSourceFactory
                        val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)

                        // Reconstruct MediaItem
                        val mediaMetadataBuilder = MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)

                        if (artworkUriString != null) {
                            mediaMetadataBuilder.setArtworkUri(Uri.parse(artworkUriString))
                        }

                        val mediaItemBuilder = MediaItem.Builder()
                            .setUri(Uri.parse(url))
                            .setMediaId(mediaId)
                            .setMediaMetadata(mediaMetadataBuilder.build())

                        if (mimeType != null) {
                            mediaItemBuilder.setMimeType(mimeType)
                        }

                        val mediaItem = mediaItemBuilder.build()

                        // Create MediaSource and set it to player
                        val source = hlsFactory.createMediaSource(mediaItem)

                        // Execute playback on main thread (MediaSession callback runs on main looper by default)
                        player.setMediaSource(source)
                        player.prepare()
                        player.play()

                        AppLogger.log("[Service] Player configured with custom HlsMediaSource")

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