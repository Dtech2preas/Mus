package com.example.musicdownloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.FavoriteSong
import com.example.musicdownloader.data.PlayHistory
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.data.PlaylistEntry
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.workers.MusicDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class GenreFeed(val genreName: String, val songs: List<VideoItem>)

object MusicRepository {

    // Simple In-Memory Cache
    private val searchCache = ConcurrentHashMap<String, List<VideoItem>>()

    suspend fun searchVideos(context: Context, query: String): Result<List<VideoItem>> {
        // 1. Check Cache
        searchCache[query]?.let {
            return Result.success(it)
        }

        // 2. Try InnerTube (Fastest & Most Reliable)
        try {
            val videos = InnerTubeClient.search(query)
            if (videos.isNotEmpty()) {
                searchCache[query] = videos
                return Result.success(videos)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue to fallbacks
        }

        // 3. Fallback to YoutubeClient (Slow but reliable backup)
        return try {
            val videos = YoutubeClient.searchVideos(context, query)
            if (videos.isNotEmpty()) {
                searchCache[query] = videos
            }
            Result.success(videos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGenreFeeds(context: Context, genres: Set<String>): List<GenreFeed> = coroutineScope {
        // Fetch feeds in parallel
        genres.map { genre ->
            async {
                val results = searchVideos(context, genre).getOrDefault(emptyList())
                // Limit to 10 items for the feed
                GenreFeed(genre, results.take(10))
            }
        }.awaitAll()
    }

    /**
     * Enqueues a download request to WorkManager.
     */
    suspend fun downloadSong(context: Context, video: VideoItem): Result<String> {
        val outputDir = File(context.filesDir, "music_downloads")
        if (!outputDir.exists()) outputDir.mkdirs()

        // Check if file already exists
        val existingFiles = outputDir.listFiles { _, name -> name.startsWith(video.id) }
        if (!existingFiles.isNullOrEmpty()) {
            AppLogger.log("[Repo] File already exists for ${video.id}")

            // Ensure it's in DB
            val database = AppDatabase.getDatabase(context)
            if (database.songDao().getSongById(video.id) == null) {
                // Insert if missing
                 val song = Song(
                    id = video.id,
                    title = video.title,
                    artist = video.uploader,
                    thumbnailUrl = video.thumbnailUrl,
                    filePath = existingFiles.first().absolutePath,
                    duration = video.duration
                )
                database.songDao().insert(song)
            }
            return Result.success("File already exists")
        }

        AppLogger.log("[Repo] Enqueueing download worker for ${video.id}")

        val workData = workDataOf(
            "videoId" to video.id,
            "title" to video.title,
            "artist" to video.uploader,
            "thumbnailUrl" to video.thumbnailUrl,
            "duration" to video.duration
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag("download")
            .build()

        WorkManager.getInstance(context).enqueue(downloadRequest)

        return Result.success("Download queued")
    }

    // Helper to sync file system with DB on startup
    suspend fun syncFilesWithDatabase(context: Context) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val outputDir = File(context.filesDir, "music_downloads")
        if (!outputDir.exists()) return@withContext

        val files = outputDir.listFiles() ?: return@withContext

        // Cleanup .deleted files
        files.filter { it.name.endsWith(".deleted") }.forEach {
             AppLogger.log("[Repo] Cleaning up .deleted file: ${it.name}")
             it.delete()
        }

        files.filter { !it.name.endsWith(".deleted") }.forEach { file ->
            // Filename format: {id}.{ext} usually
            val id = file.nameWithoutExtension

            val existingSong = database.songDao().getSongById(id)
            if (existingSong == null) {
                AppLogger.log("[Repo] Found orphaned file: ${file.name}, inserting into DB")
                val song = Song(
                    id = id,
                    title = "Unknown Song ($id)",
                    artist = "Unknown Artist",
                    thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg", // Guess thumbnail
                    filePath = file.absolutePath,
                    duration = ""
                )
                database.songDao().insert(song)
            }
        }
    }

    // History Methods
    fun getRecentHistory(context: Context): Flow<List<PlayHistory>> {
        // Fetch 50, then distinct by Video ID, then take 6
        return AppDatabase.getDatabase(context).playHistoryDao().getRecentHistory(50)
            .map { list ->
                list.distinctBy { it.songId }.take(6)
            }
    }

    suspend fun addToHistory(context: Context, video: VideoItem) {
        val history = PlayHistory(
            songId = video.id,
            title = video.title,
            artist = video.uploader,
            thumbnailUrl = video.thumbnailUrl
        )
        AppDatabase.getDatabase(context).playHistoryDao().insert(history)
    }

    suspend fun addToHistory(context: Context, song: Song) {
        val history = PlayHistory(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl
        )
        AppDatabase.getDatabase(context).playHistoryDao().insert(history)
    }

    // Favorites Methods
    suspend fun setLikeStatus(context: Context, songId: String, isLiked: Boolean) {
        val dao = AppDatabase.getDatabase(context).favoriteDao()
        if (isLiked) {
            dao.insert(FavoriteSong(songId))
        } else {
            dao.deleteById(songId)
        }
    }

    fun getLikedSongIds(context: Context): Flow<List<String>> {
        return AppDatabase.getDatabase(context).favoriteDao().getAllLikedIds()
    }

    fun isLiked(context: Context, songId: String): Flow<Boolean> {
        return AppDatabase.getDatabase(context).favoriteDao().isLiked(songId)
    }

    // Playlist Methods
    suspend fun createPlaylist(context: Context, name: String) {
        AppDatabase.getDatabase(context).playlistDao().createPlaylist(Playlist(name = name))
    }

    fun getPlaylists(context: Context): Flow<List<Playlist>> {
        return AppDatabase.getDatabase(context).playlistDao().getAllPlaylists()
    }

    suspend fun addSongToPlaylist(context: Context, playlistId: Int, songId: String) {
        AppDatabase.getDatabase(context).playlistDao().addSongToPlaylist(PlaylistEntry(playlistId, songId))
    }
}
