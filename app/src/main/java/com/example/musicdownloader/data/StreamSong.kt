package com.example.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_songs")
data class StreamSong(
    @PrimaryKey val id: String, // Video ID
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: String,
    val album: String = "Unknown Album",
    val isManual: Boolean = false, // True = Added by user, False = Auto-added by history/playback
    val timestamp: Long = System.currentTimeMillis() // For expiration of auto-added songs
)
