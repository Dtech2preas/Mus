package com.example.musicdownloader.utils

import android.content.Context
import com.example.musicdownloader.AppLogger
import com.example.musicdownloader.MusicRepository
import com.example.musicdownloader.UserPreferences
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmartShuffleManager {

    private val sessionHistory = mutableSetOf<String>()

    suspend fun getNextRecommendation(context: Context): VideoItem? = withContext(Dispatchers.IO) {
        AppLogger.log("[SmartShuffle] Calculating next recommendation...")

        // Simple Random Strategy Selection
        val strategy = (1..3).random()
        var recommendation: VideoItem? = null

        try {
            when (strategy) {
                1 -> recommendation = getRecommendationFromFavorites(context)
                2 -> recommendation = getRecommendationFromTopArtist(context)
                3 -> recommendation = getRecommendationFromGenre(context)
            }
        } catch (e: Exception) {
            AppLogger.log("[SmartShuffle] Error in strategy $strategy: ${e.message}")
        }

        if (recommendation == null) {
            // Fallback
            AppLogger.log("[SmartShuffle] Primary strategy failed, falling back to Genre...")
            recommendation = getRecommendationFromGenre(context)
        }

        if (recommendation != null) {
            AppLogger.log("[SmartShuffle] Recommendation found: ${recommendation.title} (${recommendation.id})")
            sessionHistory.add(recommendation.id)
            if (sessionHistory.size > 50) sessionHistory.clear()
        } else {
            AppLogger.log("[SmartShuffle] No recommendation found.")
        }

        return@withContext recommendation
    }

    private suspend fun getRecommendationFromFavorites(context: Context): VideoItem? {
        AppLogger.log("[SmartShuffle] Strategy: Favorites")
        val likedIds = AppDatabase.getDatabase(context).favoriteDao().getAllLikedIdsSync()
        if (likedIds.isEmpty()) {
            AppLogger.log("[SmartShuffle] No favorites found.")
            return null
        }

        val randomId = likedIds.random()

        // Check if we have song details in DB
        val song = AppDatabase.getDatabase(context).songDao().getSongById(randomId)
        if (song != null) {
             return VideoItem(
                 id = song.id,
                 title = song.title,
                 duration = song.duration,
                 uploader = song.artist,
                 thumbnailUrl = song.thumbnailUrl,
                 webUrl = "https://youtube.com/watch?v=${song.id}",
                 album = song.album
             )
        }

        // If not in DB (should be rare for favorites), maybe search for it?
        // Or just return null and let fallback handle it.
        return null
    }

    private suspend fun getRecommendationFromTopArtist(context: Context): VideoItem? {
        AppLogger.log("[SmartShuffle] Strategy: Top Artist")
        val topArtist = AppDatabase.getDatabase(context).playHistoryDao().getTopArtistSync()
        if (topArtist == null) {
            AppLogger.log("[SmartShuffle] No top artist found.")
            return null
        }

        AppLogger.log("[SmartShuffle] Top Artist is: ${topArtist.artist}")
        val results = MusicRepository.searchVideos(context, topArtist.artist).getOrNull()
        if (results.isNullOrEmpty()) return null

        val candidates = results.filter { !sessionHistory.contains(it.id) }
        return if (candidates.isNotEmpty()) candidates.random() else null
    }

    private suspend fun getRecommendationFromGenre(context: Context): VideoItem? {
        AppLogger.log("[SmartShuffle] Strategy: Genre")
        val genres = UserPreferences.getGenres(context)
        if (genres.isEmpty()) {
            AppLogger.log("[SmartShuffle] No genres found.")
            return null
        }

        val randomGenre = genres.random()
        AppLogger.log("[SmartShuffle] Selected Genre: $randomGenre")

        val results = MusicRepository.searchVideos(context, randomGenre).getOrNull()
        if (results.isNullOrEmpty()) return null

        val candidates = results.filter { !sessionHistory.contains(it.id) }
        return if (candidates.isNotEmpty()) candidates.random() else null
    }
}
