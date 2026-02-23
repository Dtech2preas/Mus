package com.example.musicdownloader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: StreamSong)

    @Delete
    suspend fun delete(song: StreamSong)

    @Query("DELETE FROM stream_songs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM stream_songs ORDER BY dateAdded DESC")
    fun getAllStreamSongs(): Flow<List<StreamSong>>

    @Query("SELECT * FROM stream_songs WHERE isManual = 1 ORDER BY dateAdded DESC")
    fun getManualStreamSongs(): Flow<List<StreamSong>>

    @Query("SELECT * FROM stream_songs WHERE isManual = 0 ORDER BY dateAdded DESC")
    fun getAutoStreamSongs(): Flow<List<StreamSong>>

    @Query("SELECT * FROM stream_songs WHERE id = :id LIMIT 1")
    suspend fun getStreamSong(id: String): StreamSong?

    @Query("SELECT EXISTS(SELECT 1 FROM stream_songs WHERE id = :id AND isManual = 1)")
    fun isLiked(id: String): Flow<Boolean>
}
