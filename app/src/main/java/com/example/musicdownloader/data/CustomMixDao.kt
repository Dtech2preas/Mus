package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomMixDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMix(mix: CustomMix): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<MixSegment>)

    @Transaction
    @Query("SELECT * FROM custom_mixes ORDER BY timestamp DESC")
    fun getAllMixesWithSegments(): Flow<List<CustomMixWithSegments>>

    @Transaction
    @Query("SELECT * FROM custom_mixes WHERE id = :mixId")
    suspend fun getMixWithSegments(mixId: Int): CustomMixWithSegments?

    @Query("DELETE FROM custom_mixes WHERE id = :mixId")
    suspend fun deleteMix(mixId: Int)
}
