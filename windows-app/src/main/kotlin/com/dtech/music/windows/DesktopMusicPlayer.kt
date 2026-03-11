package com.dtech.music.windows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import uk.co.caprica.vlcj.player.base.State

class DesktopMusicPlayer {

    private val mediaPlayerComponent = AudioPlayerComponent()
    private val mediaPlayer = mediaPlayerComponent.mediaPlayer()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    var onCompletionListener: (() -> Unit)? = null

    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _isPlaying.value = true
                _duration.value = mediaPlayer.status().length()
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                _currentPosition.value = 0L
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                onCompletionListener?.invoke()
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _isPlaying.value = false
                println("DesktopMusicPlayer: Playback error")
                // In a real scenario, we might retry or play next
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _currentPosition.value = newTime
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _duration.value = newLength
            }
        })
    }

    fun playUrl(url: String) {
        println("Playing URL: $url")
        mediaPlayer.media().play(url)
    }

    fun pause() {
        if (mediaPlayer.status().isPlaying) {
            mediaPlayer.controls().pause()
        }
    }

    fun resume() {
        if (mediaPlayer.status().state() == State.PAUSED) {
            mediaPlayer.controls().play()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else resume()
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer.controls().setTime(positionMs)
    }

    fun stop() {
        mediaPlayer.controls().stop()
    }

    fun release() {
        mediaPlayer.release()
        mediaPlayerComponent.release()
    }
}
