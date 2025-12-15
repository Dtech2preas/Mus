package com.example.musicdownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            return Result.success(it)
        }

        // Parallel Race Strategy on IO Dispatcher
        return try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    // Use a buffered channel to ensure we don't miss the signal if receiver isn't ready immediately
                    // or if multiple return concurrently.
                    val resultChannel = Channel<String>(Channel.CONFLATED)
                    val failures = AtomicInteger(0)
                    val totalSources = 3

                    // Helper to launch tasks
                    fun launchTask(block: suspend () -> String?) {
                        launch {
                            val res = block()
                            if (res != null) {
                                resultChannel.trySend(res)
                            } else {
                                if (failures.incrementAndGet() == totalSources) {
                                    resultChannel.close() // Close indicates failure
                                }
                            }
                        }
                    }

                    // Launch all sources concurrently
                    launchTask {
                        try { InnerTubeClient.getStreamUrl(cleanId).takeIf { it.isNotBlank() } } catch (e: Exception) { null }
                    }
                    launchTask {
                        try { PipedClient.getStreamUrl(cleanId).takeIf { it.isNotBlank() } } catch (e: Exception) { null }
                    }
                    launchTask {
                        try { YoutubeClient.getStreamUrl(url).takeIf { it.isNotBlank() } } catch (e: Exception) { null }
                    }

                    // Wait for first successful result
                    val resultUrl = try {
                        resultChannel.receive()
                    } catch (e: Exception) {
                        null // Channel closed (all failed)
                    }

                    // Cancel remaining jobs since we found a result or failed entirely
                    this.coroutineContext.cancelChildren()

                    if (resultUrl != null) {
                        streamUrlCache[cleanId] = resultUrl
                        Result.success(resultUrl)
                    } else {
                        Result.failure(Exception("Could not retrieve stream URL from any source"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
