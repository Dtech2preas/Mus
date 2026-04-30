package com.example.musicdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_mixes")
data class CustomMix(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val timestamp: Long
)
