package com.example.musicdownloader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "mix_segments",
    primaryKeys = ["mixId", "orderIndex"],
    foreignKeys = [
        ForeignKey(
            entity = CustomMix::class,
            parentColumns = ["id"],
            childColumns = ["mixId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mixId")]
)
data class MixSegment(
    val mixId: Int,
    val orderIndex: Int,
    val songId: String,
    val startMs: Long,
    val endMs: Long
)
