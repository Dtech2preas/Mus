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
            return Result.success(it)
        }

        // Parallel Race Strategy
        return try {
            val resultUrl = startRace(url, cleanId)

            if (resultUrl != null) {
                streamUrlCache[cleanId] = resultUrl
                Result.success(resultUrl)
            } else {
                Result.failure(Exception("Could not retrieve stream URL from any source"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun startRace(url: String, cleanId: String): String? {
        val raceJob = Job()
        // Use a manual scope so we don't wait for children to complete upon cancellation
        val scope = CoroutineScope(Dispatchers.IO + raceJob)
        // Use CONFLATED to avoid dropping results if send happens before receive
        val channel = Channel<String>(Channel.CONFLATED)
        val failures = AtomicInteger(0)
        val totalSources = 3

        fun launchTask(block: suspend () -> String?) {
            scope.launch {
                try {
                    val res = block()
                    if (!res.isNullOrBlank()) {
                        channel.trySend(res)
                    } else {
                        if (failures.incrementAndGet() == totalSources) {
                            channel.close()
                        }
                    }
                } catch (e: Exception) {
                    if (failures.incrementAndGet() == totalSources) {
                        channel.close()
                    }
                }
            }
        }

        launchTask { InnerTubeClient.getStreamUrl(cleanId) }
        launchTask { PipedClient.getStreamUrl(cleanId) }
        launchTask { YoutubeClient.getStreamUrl(url) }

        return try {
            channel.receive()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            // Cancel the others immediately.
            // Because we are using a manual Job/Scope, this call returns immediately
            // and does NOT wait for the children to finish cleanup.
            raceJob.cancel()
        }
    }
}
