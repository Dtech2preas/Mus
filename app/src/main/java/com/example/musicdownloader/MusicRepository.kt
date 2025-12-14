package com.example.musicdownloader

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Ideally we would use Hilt or Dagger for DI, but to keep it simple and without adding more dependencies unless requested,
// we will just make this a standard class or singleton object.
// Given the user requested a refactor to MVVM, a singleton Repository or manually injected one is fine.
// We'll use a Singleton object for simplicity in this context, or a class instantiated by the ViewModel.
// Let's use an object for now to mimic a Singleton provided by DI.

object MusicRepository {

    suspend fun searchVideos(query: String): Result<List<VideoItem>> {
        return try {
            val videos = YoutubeClient.searchVideos(query)
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
        return try {
            val streamUrl = YoutubeClient.getStreamUrl(url)
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
