package com.example.musicdownloader

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MusicRepository {

    // Simple In-Memory Cache
    private val searchCache = ConcurrentHashMap<String, List<VideoItem>>()
    private val streamUrlCache = ConcurrentHashMap<String, StreamInfo>()

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

    suspend fun downloadAndPlay(context: Context, video: VideoItem): Result<File> {
        val outputDir = File(context.filesDir, "music_downloads")
        if (!outputDir.exists()) outputDir.mkdirs()

        // Check if file already exists
        // We use ID as filename for reliable checking
        val existingFiles = outputDir.listFiles { _, name -> name.startsWith(video.id) }
        if (!existingFiles.isNullOrEmpty()) {
            AppLogger.log("[Repo] File already exists for ${video.id}")
            return Result.success(existingFiles.first())
        }

        AppLogger.log("[Repo] Starting download for ${video.id}")

        return try {
            AppLogger.log("[Repo] Using yt-dlp with optimized settings")
            val file = YoutubeClient.downloadAudio(context, video.id, outputDir)
            Result.success(file)
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            if (msg.contains("No address associated with hostname") || e is java.net.UnknownHostException) {
                AppLogger.log("[Repo] Network Error: Check internet connection.")
                Result.failure(Exception("Network Error: Check internet connection."))
            } else {
                AppLogger.log("[Repo] Download failed: $msg")
                Result.failure(e)
            }
        }
    }

    suspend fun getDownloadedFiles(context: Context): List<File> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val outputDir = File(context.filesDir, "music_downloads")
        if (!outputDir.exists()) return@withContext emptyList()
        return@withContext outputDir.listFiles()?.toList() ?: emptyList()
    }
}
