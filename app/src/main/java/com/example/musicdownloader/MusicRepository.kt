package com.example.musicdownloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.workers.MusicDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Enqueues a download request to WorkManager.
     * Returns true if queued, or if file exists, returns existing file wrapped in success result logic (handled by UI).
     *
     * Note: WorkManager is asynchronous. This function initiates the download.
     * For "play immediately", we might need to observe the database or wait for work info.
     * But for now, let's keep the signature similar or adjust for the new flow.
     *
     * Actually, if we use WorkManager, we can't return a File immediately unless it's already there.
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
}
