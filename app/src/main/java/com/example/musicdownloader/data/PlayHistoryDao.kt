package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<PlayHistory>>

    @Query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllHistorySync(limit: Int): List<PlayHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: PlayHistory)

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    // Optional: Keep history size manageable by deleting old entries
    @Query("DELETE FROM play_history WHERE id NOT IN (SELECT id FROM play_history ORDER BY timestamp DESC LIMIT 50)")
    suspend fun trimHistory()
}
