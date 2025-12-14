package com.example.musicdownloader

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
            return Result.success(it)
        }

        // 2. Try Piped/Invidious (Fast API)
        try {
            val streamUrl = PipedClient.getStreamUrl(cleanId)
            if (streamUrl.isNotBlank()) {
                streamUrlCache[cleanId] = streamUrl
                return Result.success(streamUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to YoutubeClient
        }

        // 3. Fallback to YoutubeDL (Slowest)
        return try {
            val streamUrl = YoutubeClient.getStreamUrl(url)
            if (streamUrl.isNotBlank()) {
                streamUrlCache[cleanId] = streamUrl
                Result.success(streamUrl)
            } else {
                Result.failure(Exception("Could not retrieve stream URL"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
