package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: StreamSong)

    @Query("DELETE FROM stream_songs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM stream_songs ORDER BY timestamp DESC")
    fun getAllStreamSongs(): Flow<List<StreamSong>>

    @Query("SELECT * FROM stream_songs WHERE isManual = 1")
    suspend fun getManualStreamSongsSync(): List<StreamSong>

    @Query("SELECT * FROM stream_songs WHERE id = :id LIMIT 1")
    suspend fun getStreamSong(id: String): StreamSong?

    @Query("DELETE FROM stream_songs WHERE isManual = 0 AND timestamp < :threshold")
    suspend fun deleteExpiredAutoSongs(threshold: Long)
}
