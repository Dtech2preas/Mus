package com.example.musicdownloader

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
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
import okhttp3.OkHttpClient

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Audio Effects
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    // A-B Repeat
    private var aPoint: Long = C.TIME_UNSET
    private var bPoint: Long = C.TIME_UNSET
    private val abLoopHandler = Handler(Looper.getMainLooper())
    private val abLoopRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying && aPoint != C.TIME_UNSET && bPoint != C.TIME_UNSET) {
                val current = player.currentPosition
                if (current >= bPoint || current < aPoint) {
                     // Check if we are past B or before A (e.g. user seeked back too far)
                     // Usually we only care if we pass B.
                     if (current >= bPoint) {
                         player.seekTo(aPoint)
                     }
                }
            }
            abLoopHandler.postDelayed(this, 50)
        }
    }

    // Define the custom command constant
    companion object {
        val PLAY_STREAM_COMMAND = SessionCommand("PLAY_STREAM", Bundle())
        val SET_SPEED_COMMAND = SessionCommand("SET_SPEED", Bundle())
        val SET_BASS_COMMAND = SessionCommand("SET_BASS", Bundle())
        val SET_VIRT_COMMAND = SessionCommand("SET_VIRT", Bundle())
        val SET_AB_COMMAND = SessionCommand("SET_AB", Bundle())
        val SET_MONO_COMMAND = SessionCommand("SET_MONO", Bundle())
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

        // Initialize Effects and Listeners
        initializeEffects()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                initializeAudioEffects(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    abLoopHandler.post(abLoopRunnable)
                } else {
                    abLoopHandler.removeCallbacks(abLoopRunnable)
                }
            }
        })

        // Apply Saved Preferences
        val speed = UserPreferences.getPlaybackSpeed(this)
        if (speed != 1.0f) {
            player.setPlaybackParameters(PlaybackParameters(speed))
        }

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

    private fun initializeEffects() {
        // Initial setup if session ID is already available (unlikely in onCreate but good practice)
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            initializeAudioEffects(player.audioSessionId)
        }
    }

    private fun initializeAudioEffects(audioSessionId: Int) {
        try {
            bassBoost?.release()
            virtualizer?.release()

            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(UserPreferences.getBassBoostStrength(this@MusicService).toShort())
            }

            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(UserPreferences.getVirtualizerStrength(this@MusicService).toShort())
            }
            Log.d("MusicService", "Audio Effects Initialized for Session $audioSessionId")
        } catch (e: Exception) {
            Log.e("MusicService", "Error initializing audio effects: ${e.message}")
        }
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
        abLoopHandler.removeCallbacks(abLoopRunnable)
        bassBoost?.release()
        virtualizer?.release()
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
                .add(SET_SPEED_COMMAND)
                .add(SET_BASS_COMMAND)
                .add(SET_VIRT_COMMAND)
                .add(SET_AB_COMMAND)
                .add(SET_MONO_COMMAND)
                .build()

            // Allow all standard player commands (Play, Pause, etc.) + Custom Commands
            val playerCommands = Player.Commands.Builder().addAllCommands().build()

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PLAY_STREAM_COMMAND.customAction -> {
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
                SET_SPEED_COMMAND.customAction -> {
                    val speed = args.getFloat("SPEED", 1.0f)
                    player.setPlaybackParameters(PlaybackParameters(speed))
                    UserPreferences.setPlaybackSpeed(this@MusicService, speed)
                }
                SET_BASS_COMMAND.customAction -> {
                    val strength = args.getInt("STRENGTH", 0)
                    bassBoost?.setStrength(strength.toShort())
                    UserPreferences.setBassBoostStrength(this@MusicService, strength)
                }
                SET_VIRT_COMMAND.customAction -> {
                    val strength = args.getInt("STRENGTH", 0)
                    virtualizer?.setStrength(strength.toShort())
                    UserPreferences.setVirtualizerStrength(this@MusicService, strength)
                }
                SET_AB_COMMAND.customAction -> {
                    aPoint = args.getLong("A_POINT", C.TIME_UNSET)
                    bPoint = args.getLong("B_POINT", C.TIME_UNSET)
                }
                SET_MONO_COMMAND.customAction -> {
                     val enabled = args.getBoolean("ENABLED", false)
                     UserPreferences.setMonoAudioEnabled(this@MusicService, enabled)
                     // Note: Runtime Mono toggling requires player recreation or complex processor chain handling.
                     // For this version, we save the preference.
                }
                else -> {
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
