package com.dtech.music.windows.data

import org.jetbrains.exposed.sql.Table

// Models
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val filePath: String,
    val originalUrl: String,
    val thumbnailUrl: String
)

data class StreamSong(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val isLibrary: Boolean,
    val isDownloaded: Boolean,
    val thumbnailUrl: String,
    val source: String,
    val isManual: Boolean
)

data class Playlist(
    val id: Int,
    val name: String
)

data class PlaylistEntry(
    val id: Int,
    val playlistId: Int,
    val songId: String
)

data class StreamCache(
    val videoId: String,
    val streamUrl: String,
    val expiryTimestamp: Long
)

data class FavoriteSong(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnailUrl: String
)

data class PlayHistory(
    val id: Int,
    val songId: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnailUrl: String,
    val timestamp: Long
)

// Tables
object SongsTable : Table("songs") {
    val id = varchar("id", 255)
    val title = varchar("title", 255)
    val artist = varchar("artist", 255)
    val duration = varchar("duration", 255)
    val filePath = varchar("file_path", 512)
    val originalUrl = varchar("original_url", 512)
    val thumbnailUrl = varchar("thumbnail_url", 512)
    override val primaryKey = PrimaryKey(id)
}

object StreamSongsTable : Table("stream_songs") {
    val id = varchar("id", 255)
    val title = varchar("title", 255)
    val artist = varchar("artist", 255)
    val duration = varchar("duration", 255)
    val isLibrary = bool("is_library")
    val isDownloaded = bool("is_downloaded")
    val thumbnailUrl = varchar("thumbnail_url", 512)
    val sourceType = varchar("source", 255)
    val isManual = bool("is_manual").default(false)
    override val primaryKey = PrimaryKey(id)
}

object PlaylistsTable : Table("playlists") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    override val primaryKey = PrimaryKey(id)
}

object PlaylistEntriesTable : Table("playlist_entries") {
    val id = integer("id").autoIncrement()
    val playlistId = integer("playlist_id")
    val songId = varchar("song_id", 255)
    override val primaryKey = PrimaryKey(id)
}

object StreamCacheTable : Table("stream_cache") {
    val videoId = varchar("video_id", 255)
    val streamUrl = varchar("stream_url", 1024)
    val expiryTimestamp = long("expiry_timestamp")
    override val primaryKey = PrimaryKey(videoId)
}

object FavoriteSongsTable : Table("favorite_songs") {
    val id = varchar("id", 255)
    val title = varchar("title", 255)
    val artist = varchar("artist", 255)
    val duration = varchar("duration", 255)
    val thumbnailUrl = varchar("thumbnail_url", 512)
    override val primaryKey = PrimaryKey(id)
}

object PlayHistoryTable : Table("play_history") {
    val id = integer("id").autoIncrement()
    val songId = varchar("song_id", 255)
    val title = varchar("title", 255)
    val artist = varchar("artist", 255)
    val duration = varchar("duration", 255)
    val thumbnailUrl = varchar("thumbnail_url", 512)
    val timestamp = long("timestamp")
    override val primaryKey = PrimaryKey(id)
}
