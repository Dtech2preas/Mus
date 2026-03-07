package com.example.musicdownloader.utils

import android.content.Context
import com.example.musicdownloader.AppLogger
import com.example.musicdownloader.MusicRepository
import com.example.musicdownloader.UserPreferences
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.InnerTubeClient
import com.example.musicdownloader.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.LinkedList

object SmartShuffleManager {

    private val sessionHistory = LinkedList<String>()
    private const val MAX_HISTORY_SIZE = 400

    private val PARENTHESIS_REGEX = Regex("\\(.*?\\)")
    private val BRACKET_REGEX = Regex("\\[.*?\\]")
    private val FEAT_REGEX = Regex("(?i)\\b(ft\\.?|feat\\.?|featuring)\\b.*$")
    private val EMOJI_REGEX = Regex("[🎥🎬🔥].*$")

    private fun recordHistory(id: String) {
        if (!sessionHistory.contains(id)) {
            sessionHistory.addLast(id)
            if (sessionHistory.size > MAX_HISTORY_SIZE) {
                sessionHistory.removeFirst()
            }
        }
    }

    private fun cleanTrackAndArtist(title: String, artist: String): Pair<String, String> {
        var cleanTitle = title
        var cleanArtist = artist

        // Check for "Artist - Track" format
        if (cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ", limit = 2)
            if (parts.size == 2) {
                cleanArtist = parts[0].trim()
                cleanTitle = parts[1].trim()
            }
        }

        // Remove (Official Video), [Official Audio], etc.
        cleanTitle = cleanTitle.replace(PARENTHESIS_REGEX, "")
        cleanTitle = cleanTitle.replace(BRACKET_REGEX, "")

        // Remove "ft.", "feat.", "featuring" and anything after
        cleanTitle = cleanTitle.replace(FEAT_REGEX, "")

        // Remove anything after a pipe "|"
        if (cleanTitle.contains("|")) {
            cleanTitle = cleanTitle.substringBefore("|")
        }

        // Remove common emojis often trailing in video titles
        cleanTitle = cleanTitle.replace(EMOJI_REGEX, "")

        return Pair(cleanTitle.trim(), cleanArtist.trim())
    }

    suspend fun getNextRecommendation(
        context: Context,
        currentTitle: String? = null,
        currentArtist: String? = null,
        currentVideoId: String? = null
    ): VideoItem? = withContext(Dispatchers.IO) {
        AppLogger.log("[SmartShuffle] Calculating next recommendation...")

        // Fetch long term history IDs
        val db = AppDatabase.getDatabase(context)
        val longTermHistoryIds = db.playHistoryDao().getAllHistoryIdsSync()

        fun isDuplicate(videoItem: VideoItem): Boolean {
            if (sessionHistory.contains(videoItem.id)) return true
            if (longTermHistoryIds.contains(videoItem.id)) return true

            val (cleanTitle, cleanArtist) = cleanTrackAndArtist(videoItem.title, videoItem.uploader)
            if (currentTitle != null && currentArtist != null) {
                 val (currentCleanTitle, currentCleanArtist) = cleanTrackAndArtist(currentTitle, currentArtist)
                 if (cleanTitle.equals(currentCleanTitle, ignoreCase = true) &&
                     cleanArtist.equals(currentCleanArtist, ignoreCase = true)) {
                     return true
                 }
            }
            return false
        }


        // 1. Last.fm Similar Tracks Strategy
        if (!currentTitle.isNullOrEmpty() && !currentArtist.isNullOrEmpty()) {
            try {
                val (cleanTitle, cleanArtist) = cleanTrackAndArtist(currentTitle, currentArtist)
                AppLogger.log("[SmartShuffle] Trying Last.fm for: $cleanTitle - $cleanArtist")
                val similarTracks = LastFmClient.getSimilarTracks(cleanTitle, cleanArtist)
                if (similarTracks.isNotEmpty()) {
                    // Try to find a track that isn't in our session history
                    for (track in similarTracks.shuffled()) { // Shuffle to pick random similar tracks
                        val query = "${track.name} ${track.artist}"
                        AppLogger.log("[SmartShuffle] Searching YouTube for Last.fm rec: $query")
                        val results = MusicRepository.searchVideos(context, query).getOrNull()
                        val validCandidate = results?.firstOrNull { !isDuplicate(it) }

                        if (validCandidate != null) {
                            AppLogger.log("[SmartShuffle] Found Last.fm recommendation: ${validCandidate.title}")
                            recordHistory(validCandidate.id)
                            return@withContext validCandidate
                        }
                    }
                } else {
                    AppLogger.log("[SmartShuffle] No Last.fm similar tracks found.")
                }
            } catch (e: Exception) {
                 AppLogger.log("[SmartShuffle] Error fetching from Last.fm: ${e.message}")
            }
        }

        // 2. Priority: Repo Recommendations (Filtered & Fresh)
        try {
            val recommendations = MusicRepository.getRecommendedSongs(context).first()
            val candidate = recommendations.filter { !sessionHistory.contains(it.id) && !longTermHistoryIds.contains(it.id) }.randomOrNull()

            if (candidate != null) {
                AppLogger.log("[SmartShuffle] Recommendation found from Repo: ${candidate.title}")
                recordHistory(candidate.id)

                return@withContext VideoItem(
                    id = candidate.id,
                    title = candidate.title,
                    uploader = candidate.artist,
                    duration = candidate.duration,
                    thumbnailUrl = candidate.thumbnailUrl,
                    webUrl = "https://youtube.com/watch?v=${candidate.id}",
                    album = candidate.album
                )
            }
        } catch (e: Exception) {
            AppLogger.log("[SmartShuffle] Failed to fetch repo recommendations: ${e.message}")
        }

        // Simple Random Strategy Selection (Fallback if repo empty)
        // Ensure we don't reuse strategies too many times, add related videos strategy
        val strategyList = mutableListOf(1, 2, 3, 4).shuffled()
        var recommendation: VideoItem? = null

        for (strategy in strategyList) {
            try {
                when (strategy) {
                    1 -> recommendation = getRecommendationFromFavorites(context, ::isDuplicate)
                    2 -> recommendation = getRecommendationFromTopArtist(context, ::isDuplicate)
                    3 -> recommendation = getRecommendationFromGenre(context, ::isDuplicate)
                    4 -> if (currentVideoId != null) recommendation = getRecommendationFromRelated(context, currentVideoId, ::isDuplicate)
                }
            } catch (e: Exception) {
                AppLogger.log("[SmartShuffle] Error in strategy $strategy: ${e.message}")
            }
            if (recommendation != null) break
        }

        if (recommendation == null) {
            // Ultimate Fallback
            AppLogger.log("[SmartShuffle] All primary strategies failed, falling back to Genre...")
            recommendation = getRecommendationFromGenre(context, ::isDuplicate)
        }

        if (recommendation != null) {
            AppLogger.log("[SmartShuffle] Recommendation found: ${recommendation.title} (${recommendation.id})")
            recordHistory(recommendation.id)
        } else {
            AppLogger.log("[SmartShuffle] No recommendation found.")
        }

        return@withContext recommendation
    }

    private suspend fun getRecommendationFromFavorites(context: Context, isDuplicate: (VideoItem) -> Boolean): VideoItem? {
        AppLogger.log("[SmartShuffle] Strategy: Favorites")
        val likedIds = AppDatabase.getDatabase(context).favoriteDao().getAllLikedIdsSync()
        if (likedIds.isEmpty()) {
            AppLogger.log("[SmartShuffle] No favorites found.")
            return null
        }

        // To properly use isDuplicate on IDs, we would need metadata, but we only have IDs. Let's just check session/history here
        val longTermHistoryIds = AppDatabase.getDatabase(context).playHistoryDao().getAllHistoryIdsSync()
        val candidates = likedIds.filter { !sessionHistory.contains(it) && !longTermHistoryIds.contains(it) }
        val randomId = if(candidates.isNotEmpty()) candidates.random() else likedIds.random() // break the loop if completely out

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
        return null
    }

    private suspend fun getRecommendationFromTopArtist(context: Context, isDuplicate: (VideoItem) -> Boolean): VideoItem? {
        AppLogger.log("[SmartShuffle] Strategy: Top Artist")
        val topArtist = AppDatabase.getDatabase(context).playHistoryDao().getTopArtistSync()
        if (topArtist == null) {
            AppLogger.log("[SmartShuffle] No top artist found.")
            return null
        }

        AppLogger.log("[SmartShuffle] Top Artist is: ${topArtist.artist}")
        val results = MusicRepository.searchVideos(context, topArtist.artist).getOrNull()
        if (results.isNullOrEmpty()) return null

        val candidates = results.filter { !isDuplicate(it) }
        // Shuffle candidates so we don't always pick top 1
        return if (candidates.isNotEmpty()) candidates.shuffled().first() else null
    }

    private suspend fun getRecommendationFromGenre(context: Context, isDuplicate: (VideoItem) -> Boolean): VideoItem? {
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

        val candidates = results.filter { !isDuplicate(it) }
        return if (candidates.isNotEmpty()) candidates.shuffled().first() else null
    }

    private suspend fun getRecommendationFromRelated(context: Context, videoId: String, isDuplicate: (VideoItem) -> Boolean): VideoItem? {
        AppLogger.log("[SmartShuffle] Strategy: Related (Next)")
        val results = com.example.musicdownloader.InnerTubeClient.getRelatedVideos(videoId)
        if (results.isNullOrEmpty()) return null

        val candidates = results.filter { !isDuplicate(it) }
        return if (candidates.isNotEmpty()) candidates.shuffled().first() else null
    }
}
