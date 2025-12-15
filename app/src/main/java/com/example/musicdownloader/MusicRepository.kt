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
        AppLogger.log("MusicRepo", "Searching for: $query")
        // 1. Check Cache
        searchCache[query]?.let {
            AppLogger.log("MusicRepo", "Search cache hit for: $query")
            return Result.success(it)
        }

        // 2. Try InnerTube (Fastest & Most Reliable)
        try {
            AppLogger.log("MusicRepo", "Trying InnerTube search...")
            val videos = InnerTubeClient.search(query)
            if (videos.isNotEmpty()) {
                AppLogger.log("MusicRepo", "InnerTube search success, found ${videos.size} videos")
                searchCache[query] = videos
                return Result.success(videos)
            } else {
                AppLogger.log("MusicRepo", "InnerTube search returned empty")
            }
        } catch (e: Exception) {
            AppLogger.log("MusicRepo", "InnerTube search failed: ${e.message}")
            e.printStackTrace()
            // Continue to fallbacks
        }

        // 3. Fallback to YoutubeClient (Slow but reliable backup)
        return try {
            AppLogger.log("MusicRepo", "Falling back to YoutubeClient search...")
            val videos = YoutubeClient.searchVideos(query)
            if (videos.isNotEmpty()) {
                searchCache[query] = videos
            }
            AppLogger.log("MusicRepo", "YoutubeClient search done, found ${videos.size} videos")
            Result.success(videos)
        } catch (e: Exception) {
            AppLogger.log("MusicRepo", "YoutubeClient search failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun downloadAudio(url: String, outputDir: File): Result<File> {
        AppLogger.log("MusicRepo", "Starting download for $url")
        return try {
            val file = YoutubeClient.downloadAudio(url, outputDir)
            AppLogger.log("MusicRepo", "Download success: ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            AppLogger.log("MusicRepo", "Download failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getStreamUrl(url: String): Result<String> {
        AppLogger.log("MusicRepo", "Getting stream URL for: $url")
        // Extract ID
        val id = if (url.contains("v=")) url.substringAfter("v=") else url.substringAfterLast("/")
        val cleanId = if (id.contains("&")) id.substringBefore("&") else id

        // 1. Check Cache
        streamUrlCache[cleanId]?.let {
            AppLogger.log("MusicRepo", "Stream URL cache hit for $cleanId")
            return Result.success(it)
        }

        AppLogger.log("MusicRepo", "Starting stream race for $cleanId")
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
                    fun launchTask(name: String, block: suspend () -> String?) {
                        launch {
                            AppLogger.log("MusicRepo", "Race: $name started")
                            val start = System.currentTimeMillis()
                            val res = block()
                            val time = System.currentTimeMillis() - start
                            if (res != null) {
                                AppLogger.log("MusicRepo", "Race: $name SUCCESS in ${time}ms")
                                resultChannel.trySend(res)
                            } else {
                                AppLogger.log("MusicRepo", "Race: $name FAILED/NULL in ${time}ms")
                                if (failures.incrementAndGet() == totalSources) {
                                    AppLogger.log("MusicRepo", "Race: ALL sources failed")
                                    resultChannel.close() // Close indicates failure
                                }
                            }
                        }
                    }

                    // Launch all sources concurrently
                    launchTask("InnerTube") {
                        try { InnerTubeClient.getStreamUrl(cleanId).takeIf { it.isNotBlank() } } catch (e: Exception) {
                            AppLogger.log("MusicRepo", "Race: InnerTube error: ${e.message}"); null
                        }
                    }
                    launchTask("Piped") {
                        try { PipedClient.getStreamUrl(cleanId).takeIf { it.isNotBlank() } } catch (e: Exception) {
                            AppLogger.log("MusicRepo", "Race: Piped error: ${e.message}"); null
                        }
                    }
                    launchTask("YoutubeDL") {
                        try { YoutubeClient.getStreamUrl(url).takeIf { it.isNotBlank() } } catch (e: Exception) {
                            AppLogger.log("MusicRepo", "Race: YoutubeDL error: ${e.message}"); null
                        }
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
                        AppLogger.log("MusicRepo", "Race winner found, caching result.")
                        streamUrlCache[cleanId] = resultUrl
                        Result.success(resultUrl)
                    } else {
                        Result.failure(Exception("Could not retrieve stream URL from any source"))
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.log("MusicRepo", "Stream fetch exception: ${e.message}")
            Result.failure(e)
        }
    }
}
