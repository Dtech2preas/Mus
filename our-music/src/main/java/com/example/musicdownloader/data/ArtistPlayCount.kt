package com.example.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artist_play_counts")
data class ArtistPlayCount(
    @PrimaryKey val artist: String,
    val playCount: Int
)
