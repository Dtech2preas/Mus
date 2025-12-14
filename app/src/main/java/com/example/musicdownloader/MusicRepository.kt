package com.example.musicdownloader

import java.io.File

class MusicRepository(private val youtubeClient: YoutubeClient) {

    suspend fun searchVideos(query: String): Result<List<VideoItem>> {
        return try {
            val videos = youtubeClient.searchVideos(query)
            Result.success(videos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAudio(url: String, outputDir: File): Result<File> {
        return try {
            val file = youtubeClient.downloadAudio(url, outputDir)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStreamUrl(url: String): Result<String> {
        return try {
            val streamUrl = youtubeClient.getStreamUrl(url)
            if (streamUrl.isNotBlank()) {
                Result.success(streamUrl)
            } else {
                Result.failure(Exception("Could not retrieve stream URL"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
