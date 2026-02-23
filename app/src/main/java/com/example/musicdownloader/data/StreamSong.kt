package com.example.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_songs")
data class StreamSong(
    @PrimaryKey val id: String, // Video ID
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val dateAdded: Long,
    val isManual: Boolean, // True = Liked/Manual Library, False = Auto/History
    val duration: String,
    val album: String = "Unknown Album"
)
