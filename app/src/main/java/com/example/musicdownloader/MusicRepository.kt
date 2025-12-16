package com.example.musicdownloader

import java.io.File
import java.util.concurrent.ConcurrentHashMap

object MusicRepository {

    // Simple In-Memory Cache
    private val searchCache = ConcurrentHashMap<String, List<VideoItem>>()
    private val streamUrlCache = ConcurrentHashMap<String, String>()

    suspend fun searchVideos(query: String): Result<List<VideoItem>> {
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
            val videos = YoutubeClient.searchVideos(query)
            if (videos.isNotEmpty()) {
                searchCache[query] = videos
            }
            Result.success(videos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAudio(url: String, outputDir: File): Result<File> {
        return try {
            val file = YoutubeClient.downloadAudio(url, outputDir)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStreamUrl(url: String): Result<String> {
        // Extract ID
        val id = if (url.contains("v=")) url.substringAfter("v=") else url.substringAfterLast("/")
        val cleanId = if (id.contains("&")) id.substringBefore("&") else id

        // 1. Check Cache
        streamUrlCache[cleanId]?.let {
            AppLogger.log("Cache Hit for $cleanId")
            return Result.success(it)
        }

        AppLogger.log("[Stream] Requesting URL for $cleanId")

        // Step 1: InnerTube (Priority)
        try {
            val innerTubeUrl = InnerTubeClient.getStreamUrl(cleanId)
            if (innerTubeUrl.isNotEmpty()) {
                AppLogger.log("[Stream] InnerTube Success")
                streamUrlCache[cleanId] = innerTubeUrl
                return Result.success(innerTubeUrl)
            }
        } catch (e: Exception) {
            AppLogger.log("[Stream] InnerTube Failed: ${e.message}. Switching to Fallback.")
        }

        // Step 2: Fallback to YoutubeClient (yt-dlp)
        // PipedClient is removed as per requirements.
        return try {
            val ytUrl = YoutubeClient.getStreamUrl(url)
            if (ytUrl.isNotEmpty()) {
                AppLogger.log("[Stream] YoutubeDL Fallback Success")
                streamUrlCache[cleanId] = ytUrl
                Result.success(ytUrl)
            } else {
                AppLogger.log("[Stream] All sources failed.")
                Result.failure(Exception("Could not retrieve stream URL from any source"))
            }
        } catch (e: Exception) {
            AppLogger.log("[Stream] YoutubeDL Fallback Exception: ${e.message}")
            Result.failure(e)
        }
    }
}
