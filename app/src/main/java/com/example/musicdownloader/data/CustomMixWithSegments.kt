package com.example.musicdownloader.data

import androidx.room.Embedded
import androidx.room.Relation

data class CustomMixWithSegments(
    @Embedded val mix: CustomMix,
    @Relation(
        parentColumn = "id",
        entityColumn = "mixId"
    )
    val segments: List<MixSegment>
)
