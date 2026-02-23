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
import com.example.musicdownloader.data.StreamCache
import com.example.musicdownloader.data.StreamSong
import com.example.musicdownloader.workers.MusicDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class GenreFeed(val genreName: String, val songs: List<VideoItem>)

object MusicRepository {

    // Simple In-Memory Cache
    private val searchCache = ConcurrentHashMap<String, List<VideoItem>>()

    // Cache duration constant
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    suspend fun searchVideos(context: Context, query: String): Result<List<VideoItem>> {
        AppLogger.log("[Repo] searchVideos called for: '$query'")
        // 1. Check Cache
        searchCache[query]?.let {
            AppLogger.log("[Repo] Cache HIT for '$query' (${it.size} items)")
            return Result.success(it)
        }
        AppLogger.log("[Repo] Cache MISS for '$query'")

        // 2. Try InnerTube (Fastest & Most Reliable)
        try {
            AppLogger.log("[Repo] Trying InnerTube search...")
            val videos = InnerTubeClient.search(query)
            if (videos.isNotEmpty()) {
                AppLogger.log("[Repo] InnerTube success: ${videos.size} items found")
                searchCache[query] = videos
                return Result.success(videos)
            } else {
                 AppLogger.log("[Repo] InnerTube returned empty list.")
            }
        } catch (e: Exception) {
            AppLogger.log("[Repo] InnerTube failed: ${e.message}")
            e.printStackTrace()
            // Continue to fallbacks
        }

        // 3. Fallback to YoutubeClient (Slow but reliable backup)
        return try {
            AppLogger.log("[Repo] Fallback to YoutubeClient search...")
            val videos = YoutubeClient.searchVideos(context, query)
            if (videos.isNotEmpty()) {
                AppLogger.log("[Repo] YoutubeClient success: ${videos.size} items found")
                searchCache[query] = videos
            } else {
                AppLogger.log("[Repo] YoutubeClient returned empty list.")
            }
            Result.success(videos)
        } catch (e: Exception) {
            AppLogger.log("[Repo] YoutubeClient failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchGenreFeeds(context: Context, genres: Set<String>): List<GenreFeed> = coroutineScope {
        val lastRefreshed = UserPreferences.getLastGenreRefreshTime(context)
        val currentTime = System.currentTimeMillis()
        val shouldRefresh = (currentTime - lastRefreshed) > CACHE_DURATION_MS

        if (shouldRefresh) {
            // Clear memory cache if we are due for a refresh
            searchCache.clear()
            UserPreferences.setLastGenreRefreshTime(context, currentTime)
        }

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
                    duration = video.duration,
                    album = video.album ?: "Unknown Album"
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
            "duration" to video.duration,
            "album" to (video.album ?: "Unknown Album")
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag("download") // Generic tag
            .addTag("download_${video.id}") // Specific tag
            .build()

        WorkManager.getInstance(context).enqueue(downloadRequest)

        return Result.success("Download queued")
    }

    fun deleteDownload(context: Context, videoId: String) {
        AppLogger.log("[Repo] Deleting download for $videoId")

        // 1. Cancel Work
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$videoId")

        // 2. Remove from Client Status
        YoutubeClient.removeDownloadStatus(videoId)

        // 3. Delete Files (Partially downloaded or complete)
        val outputDir = File(context.filesDir, "music_downloads")
        if (outputDir.exists()) {
            outputDir.listFiles { _, name -> name.startsWith(videoId) }?.forEach { file ->
                try {
                    file.delete()
                    AppLogger.log("[Repo] Deleted file: ${file.name}")
                } catch (e: Exception) {
                    AppLogger.log("[Repo] Failed to delete file: ${file.name}")
                }
            }
        }
    }

    fun pauseDownload(context: Context, videoId: String) {
        AppLogger.log("[Repo] Pausing download for $videoId")
        // Cancel work (stops process)
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$videoId")
        // Mark as paused in Client
        YoutubeClient.pauseDownloadStatus(videoId)
    }

    // Helper to sync file system with DB on startup
    suspend fun syncFilesWithDatabase(context: Context) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val outputDir = File(context.filesDir, "music_downloads")

        // Migrate legacy favorites first
        migrateLegacyFavorites(context)

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

        fixUnknownSongs(context)

        // Prune expired stream cache on startup
        try {
            database.streamCacheDao().clearExpired(System.currentTimeMillis() / 1000)
            AppLogger.log("[Repo] Expired stream cache pruned.")
        } catch (e: Exception) {
            AppLogger.log("[Repo] Failed to prune stream cache: ${e.message}")
        }
    }

    suspend fun rescanLibrary(context: Context) = withContext(Dispatchers.IO) {
        AppLogger.log("[Repo] Starting library rescan...")
        syncFilesWithDatabase(context)
        fixUnknownSongs(context)
        AppLogger.log("[Repo] Library rescan complete.")
    }

    private suspend fun fixUnknownSongs(context: Context) = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)
        val songs = database.songDao().getAllSongsSync() // Need a synchronous fetch or flow collection

        // Filter for "Unknown Song" OR any song with "Unknown Artist" to be more thorough
        songs.filter { it.title.startsWith("Unknown Song") || it.artist == "Unknown Artist" }.forEach { song ->
            try {
                AppLogger.log("[Repo] Attempting to recover metadata for ${song.id}")
                val metadata = InnerTubeClient.fetchMetadata(context, song.id)

                // Only update if we actually got valid data back
                if (metadata.title.isNotBlank() && metadata.title != "Unknown Title") {
                    val updatedSong = song.copy(
                        title = metadata.title,
                        artist = metadata.uploader,
                        duration = metadata.duration,
                        thumbnailUrl = metadata.thumbnailUrl,
                        album = metadata.album ?: song.album
                    )

                    database.songDao().insert(updatedSong) // Insert with same ID replaces
                    AppLogger.log("[Repo] Recovered metadata for ${song.id}: ${metadata.title}")
                } else {
                    AppLogger.log("[Repo] Metadata fetch returned empty/invalid for ${song.id}")
                }
            } catch (e: Exception) {
                AppLogger.log("[Repo] Failed to recover metadata for ${song.id}: ${e.message}")
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
        AppLogger.log("[Repo] addToHistory (VideoItem): ${video.title}")
        val history = PlayHistory(
            songId = video.id,
            title = video.title,
            artist = video.uploader,
            thumbnailUrl = video.thumbnailUrl
        )
        AppDatabase.getDatabase(context).playHistoryDao().insert(history)
    }

    suspend fun addToHistory(context: Context, song: Song) {
        AppLogger.log("[Repo] addToHistory (Song): ${song.title}")
        val history = PlayHistory(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl
        )
        AppDatabase.getDatabase(context).playHistoryDao().insert(history)
    }

    // DNA Stats Methods
    fun getTopArtist(context: Context): Flow<com.example.musicdownloader.data.ArtistCount?> {
        return AppDatabase.getDatabase(context).playHistoryDao().getTopArtist()
    }

    fun getTotalPlayCount(context: Context): Flow<Int> {
        return AppDatabase.getDatabase(context).playHistoryDao().getTotalPlayCount()
    }

    // Helper method for Smart Shuffle
    suspend fun getTopArtistsList(context: Context): List<String> {
        return withContext(Dispatchers.IO) {
             emptyList() // Placeholder
        }
    }

    // --- Stream Library Methods (New) ---

    fun getStreamLibrary(context: Context): Flow<List<StreamSong>> {
        // Returns ALL stream songs (both manual and auto, maybe? Or just manual?)
        // User asked for "Stream Library" to contain liked/manual songs.
        // Auto songs are for cache. But maybe we show them too?
        // User: "in thst syream library we would need to have songs that we would periodically fectch the stream url awalys have it ready n those that we auto fetched which will be below what the users own manually added songs"
        // So show ALL, but maybe sorted? Or manual first?
        // StreamSongDao.getAllStreamSongs is ordered by dateAdded DESC.
        // Let's filter in UI or return two flows. For now, let's return all Manual ones for the main "Library" view,
        // unless requested otherwise. The prompt implies separation.
        // Actually, the prompt says "Stream Library ... shows 'Liked Songs' ... and potentially Playlists".
        // It also says "those that we auto fetched which will be below what the users own manually added songs".
        // So we should return all, but maybe user wants them sectioned.
        // Let's return all for now.
        return AppDatabase.getDatabase(context).streamSongDao().getAllStreamSongs()
    }

    fun getManualStreamSongs(context: Context): Flow<List<StreamSong>> {
        return AppDatabase.getDatabase(context).streamSongDao().getManualStreamSongs()
    }

    suspend fun toggleLike(context: Context, video: VideoItem) {
        val dao = AppDatabase.getDatabase(context).streamSongDao()
        val existing = dao.getStreamSong(video.id)

        if (existing != null && existing.isManual) {
            // Un-like: Change to auto? or Delete?
            // "Unliking" usually removes from library.
            // If we delete, it disappears from cache refresh too.
            // Let's delete for now to be clean.
            dao.delete(existing)
            AppLogger.log("[Repo] Removed ${video.title} from Stream Library")
        } else {
            // Like: Insert or Update as Manual
            val song = StreamSong(
                id = video.id,
                title = video.title,
                artist = video.uploader,
                thumbnailUrl = video.thumbnailUrl,
                dateAdded = System.currentTimeMillis(),
                isManual = true,
                duration = video.duration,
                album = video.album ?: "Unknown Album"
            )
            dao.insert(song)
            AppLogger.log("[Repo] Added ${video.title} to Stream Library")

            // Trigger prefetch
            prefetchStream(context, video.id)
        }
    }

    suspend fun autoAddStreamSong(context: Context, video: VideoItem) {
        val dao = AppDatabase.getDatabase(context).streamSongDao()
        val existing = dao.getStreamSong(video.id)

        if (existing == null) {
            val song = StreamSong(
                id = video.id,
                title = video.title,
                artist = video.uploader,
                thumbnailUrl = video.thumbnailUrl,
                dateAdded = System.currentTimeMillis(),
                isManual = false, // Auto added
                duration = video.duration,
                album = video.album ?: "Unknown Album"
            )
            dao.insert(song)
            AppLogger.log("[Repo] Auto-added ${video.title} to Stream Cache")
        } else {
            // Already exists. If it's manual, do nothing.
            // If it's auto, maybe update dateAdded to keep it fresh?
            if (!existing.isManual) {
                dao.insert(existing.copy(dateAdded = System.currentTimeMillis()))
            }
        }
    }

    fun getLikedSongIds(context: Context): Flow<List<String>> {
        // Used by ViewModel to check isLiked status
        return AppDatabase.getDatabase(context).streamSongDao().getManualStreamSongs()
            .map { list -> list.map { it.id } }
    }

    fun isLiked(context: Context, songId: String): Flow<Boolean> {
        return AppDatabase.getDatabase(context).streamSongDao().isLiked(songId)
    }

    suspend fun migrateLegacyFavorites(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val favDao = db.favoriteDao()
        val streamDao = db.streamSongDao()

        val legacyIds = favDao.getAllLikedIdsSync()
        if (legacyIds.isNotEmpty()) {
            AppLogger.log("[Repo] Migrating ${legacyIds.size} legacy favorites...")
            legacyIds.forEach { id ->
                // Check if already in StreamSong
                if (streamDao.getStreamSong(id) == null) {
                    try {
                        // Fetch Metadata
                        val metadata = InnerTubeClient.fetchMetadata(context, id)
                         if (metadata.title.isNotBlank() && metadata.title != "Unknown Title") {
                            val song = StreamSong(
                                id = id,
                                title = metadata.title,
                                artist = metadata.uploader,
                                thumbnailUrl = metadata.thumbnailUrl,
                                dateAdded = System.currentTimeMillis(),
                                isManual = true,
                                duration = metadata.duration,
                                album = metadata.album ?: "Unknown Album"
                            )
                            streamDao.insert(song)
                            AppLogger.log("[Repo] Migrated $id: ${metadata.title}")
                         }
                    } catch (e: Exception) {
                         AppLogger.log("[Repo] Failed to migrate $id: ${e.message}")
                    }
                }
                // Remove from legacy table
                favDao.deleteById(id)
            }
            AppLogger.log("[Repo] Migration complete.")
        }
    }

    // Playlist Methods
    suspend fun createPlaylist(context: Context, name: String) {
        AppDatabase.getDatabase(context).playlistDao().insertPlaylist(Playlist(name = name))
    }

    fun getPlaylists(context: Context): Flow<List<Playlist>> {
        return AppDatabase.getDatabase(context).playlistDao().getAllPlaylists()
    }

    suspend fun addSongToPlaylist(context: Context, playlistId: Int, songId: String) {
        AppDatabase.getDatabase(context).playlistDao().addSongToPlaylist(PlaylistEntry(playlistId, songId))
    }

    suspend fun replaceSongFile(context: Context, oldSong: Song, newFile: File): Song = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(context)

        val oldFile = File(oldSong.filePath)
        val extension = newFile.extension
        val finalFile = File(context.filesDir, "music_downloads/${oldSong.id}.$extension")

        // Ensure parent dir exists
        finalFile.parentFile?.mkdirs()

        // 1. Move new file to final location
        if (newFile.absolutePath != finalFile.absolutePath) {
            // If final file exists (and is not our new file source), delete it to allow rename
            if (finalFile.exists()) {
                if (!finalFile.delete()) {
                    // Try to proceed, but rename might fail
                    AppLogger.log("[Repo] Warning: Could not delete target file ${finalFile.name}")
                }
            }

            if (!newFile.renameTo(finalFile)) {
                // Fallback: Copy and Delete
                try {
                    newFile.copyTo(finalFile, overwrite = true)
                    newFile.delete()
                } catch (e: Exception) {
                    throw java.io.IOException("Failed to move compressed file to ${finalFile.absolutePath}: ${e.message}")
                }
            }
        }

        // 2. Delete old file (only if it is a different path than the final one)
        if (oldFile.exists() && oldFile.absolutePath != finalFile.absolutePath) {
            oldFile.delete()
        }

        // 3. Update Database
        val updatedSong = oldSong.copy(
            filePath = finalFile.absolutePath
        )
        database.songDao().insert(updatedSong) // Insert with same ID replaces

        AppLogger.log("[Repo] Replaced song file for ${oldSong.title}. New path: ${finalFile.absolutePath}")
        return@withContext updatedSong
    }

    suspend fun importLocalSongs(context: Context): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATA
            )

            // Query for audio files
            val cursor = context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                val durationCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)

                val database = AppDatabase.getDatabase(context)

                while (it.moveToNext()) {
                    val fileId = it.getLong(idCol).toString()
                    val title = it.getString(titleCol) ?: "Unknown Title"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val durationMs = it.getLong(durationCol)
                    val path = it.getString(dataCol)

                    // Format Duration
                    val durationSec = durationMs / 1000
                    val minutes = durationSec / 60
                    val seconds = durationSec % 60
                    val durationStr = String.format("%d:%02d", minutes, seconds)

                    // Check for duplicates by path or ID prefix "local_"
                    val localId = "local_$fileId"
                    if (database.songDao().getSongById(localId) == null) {
                         val song = Song(
                             id = localId,
                             title = title,
                             artist = artist,
                             album = album,
                             duration = durationStr,
                             filePath = path,
                             thumbnailUrl = "" // No thumbnail for local yet
                         )
                         database.songDao().insert(song)
                         count++
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.log("[Repo] Import failed: ${e.message}")
            e.printStackTrace()
        }
        return@withContext count
    }

    // --- Stream Caching & Prefetching ---

    suspend fun getStreamUrlWithCache(context: Context, videoId: String, webUrl: String): StreamInfo {
        val dao = AppDatabase.getDatabase(context).streamCacheDao()
        val currentTime = System.currentTimeMillis() / 1000

        // 1. Check DB Cache
        val cached = dao.getStreamCache(videoId)
        if (cached != null) {
            if (cached.expireTime > currentTime) {
                AppLogger.log("[Repo] Stream Cache HIT for $videoId. Expires in ${(cached.expireTime - currentTime)}s")
                return StreamInfo(cached.streamUrl, cached.streamUrl.contains(".m3u8"))
            } else {
                AppLogger.log("[Repo] Stream Cache EXPIRED for $videoId")
            }
        } else {
            AppLogger.log("[Repo] Stream Cache MISS for $videoId")
        }

        // 2. Fetch new URL
        val streamInfo = YoutubeClient.getStreamUrl(context, webUrl)

        // 3. Parse Expiration & Cache
        if (streamInfo.url.isNotBlank()) {
            val expire = extractExpiration(streamInfo.url)
            if (expire > 0) {
                // Safety buffer: subtract 5 minutes from expiration
                val safeExpire = expire - 300
                if (safeExpire > currentTime) {
                    dao.insert(StreamCache(videoId, streamInfo.url, safeExpire, currentTime))
                    AppLogger.log("[Repo] Cached stream for $videoId. Expires at $safeExpire")
                }
            }
        }

        return streamInfo
    }

    suspend fun prefetchStream(context: Context, videoId: String) {
        // Construct webUrl (standard format)
        val webUrl = "https://www.youtube.com/watch?v=$videoId"
        try {
            // This will trigger the cache logic
            getStreamUrlWithCache(context, videoId, webUrl)
        } catch (e: Exception) {
            AppLogger.log("[Repo] Prefetch failed for $videoId: ${e.message}")
        }
    }

    private fun extractExpiration(url: String): Long {
        try {
            // Regex for 'expire=1234567890'
            val matcher = Pattern.compile("expire=(\\d+)").matcher(url)
            if (matcher.find()) {
                return matcher.group(1)?.toLong() ?: 0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }
}
