package com.example.musicdownloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

        AppLogger.log("[Race] Started for $cleanId")

        // Parallel Race Strategy
        return try {
            val resultUrl = startRace(url, cleanId)

            if (resultUrl != null) {
                AppLogger.log("[Race] WINNER found. Returning result NOW.")
                streamUrlCache[cleanId] = resultUrl
                Result.success(resultUrl)
            } else {
                AppLogger.log("[Race] All sources failed.")
                Result.failure(Exception("Could not retrieve stream URL from any source"))
            }
        } catch (e: Exception) {
            AppLogger.log("[Race] Exception: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun startRace(url: String, cleanId: String): String? {
        // Detached scope: We do NOT use structured concurrency here because we want
        // the losers to keep running (or die eventually) without blocking the winner.
        // Dispatchers.IO is used for network operations.
        // We use a supervisor Job or just a new Job to ensure if one fails it doesn't cancel others (though we handle exceptions manually).
        // Actually, the prompt says "Do not wait for the other slow tasks... Let them die in the background."
        // Using a completely detached scope is what's requested.
        val scope = CoroutineScope(Dispatchers.IO)

        val channel = Channel<String>(Channel.CONFLATED)
        val failures = AtomicInteger(0)
        val totalSources = 3
        val startTime = System.currentTimeMillis()

        fun launchTask(name: String, block: suspend () -> String?) {
            scope.launch {
                try {
                    val res = block()
                    if (!res.isNullOrBlank()) {
                        val duration = System.currentTimeMillis() - startTime
                        AppLogger.log("[Source] $name finished in $duration ms")
                        channel.trySend(res)
                    } else {
                        // Failed to find url
                        if (failures.incrementAndGet() == totalSources) {
                            channel.close() // Close channel if all failed
                        }
                    }
                } catch (e: Exception) {
                     // Error
                     if (failures.incrementAndGet() == totalSources) {
                        channel.close()
                     }
                }
            }
        }

        launchTask("InnerTube") { InnerTubeClient.getStreamUrl(cleanId) }
        launchTask("Piped") { PipedClient.getStreamUrl(cleanId) }
        launchTask("YoutubeDL") { YoutubeClient.getStreamUrl(url) }

        return try {
            // Wait for the first result
            channel.receive()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Channel closed without value (all failed) or other error
            null
        }
        // Note: We do NOT cancel the scope or jobs here. We return immediately.
        // The background tasks will finish or timeout on their own.
    }
}
