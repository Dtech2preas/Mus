package com.example.musicdownloader

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class AudioPlayer(context: Context) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    val isPlaying: Boolean
        get() = exoPlayer.isPlaying

    fun addListener(listener: Player.Listener) {
        exoPlayer.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        exoPlayer.removeListener(listener)
    }

    fun play(filePath: String) {
        val file = File(filePath)
        val uri = Uri.fromFile(file)
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun resume() {
        exoPlayer.play()
    }

    fun release() {
        exoPlayer.release()
    }
}
