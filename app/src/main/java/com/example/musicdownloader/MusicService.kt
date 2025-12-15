package com.example.musicdownloader

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Configure ExoPlayer with the IOS User-Agent to avoid 403 errors from YouTube
        // The User-Agent must match what InnerTubeClient uses.
        val userAgent = "com.google.ios.youtube/19.29.1 (iPhone; U; CPU iPhone OS 14_0 like Mac OS X; en_US)"
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

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
            .setMediaSourceFactory(mediaSourceFactory)
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
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
