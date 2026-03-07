import re
with open("app/src/main/java/com/example/musicdownloader/utils/SmartShuffleManager.kt", "r") as f:
    content = f.read()

# Replace MAX_HISTORY_SIZE = 100 with 400
content = content.replace("private const val MAX_HISTORY_SIZE = 100", "private const val MAX_HISTORY_SIZE = 400")

# Add InnerTubeClient import
if "import com.example.musicdownloader.InnerTubeClient" not in content:
    content = content.replace("import com.example.musicdownloader.VideoItem", "import com.example.musicdownloader.VideoItem\nimport com.example.musicdownloader.InnerTubeClient")

# Update getNextRecommendation signature and body to check long term history
body_to_replace = """    suspend fun getNextRecommendation(
        context: Context,
        currentTitle: String? = null,
        currentArtist: String? = null
    ): VideoItem? = withContext(Dispatchers.IO) {
        AppLogger.log("[SmartShuffle] Calculating next recommendation...")"""

new_body = """    suspend fun getNextRecommendation(
        context: Context,
        currentTitle: String? = null,
        currentArtist: String? = null,
        currentVideoId: String? = null
    ): VideoItem? = withContext(Dispatchers.IO) {
        AppLogger.log("[SmartShuffle] Calculating next recommendation...")

        // Fetch long term history IDs
        val db = AppDatabase.getDatabase(context)
        val longTermHistoryIds = db.playHistoryDao().getAllHistoryIdsSync() ?: emptyList()

        fun isDuplicate(videoItem: VideoItem): Boolean {
            if (sessionHistory.contains(videoItem.id)) return true
            if (longTermHistoryIds.contains(videoItem.id)) return true

            // Deduplication by title & artist
            val (cleanTitle, cleanArtist) = cleanTrackAndArtist(videoItem.title, videoItem.uploader)

            // Ensure this same track (e.g. lyric vs official video) isn't in history
            // We only check against the currentTitle/currentArtist as an example of deduplication
            // Or ideally, we can maintain a set of seen track names if we want it fully strict,
            // but comparing it against the current playing track is a good start.
            if (currentTitle != null && currentArtist != null) {
                 val (currentCleanTitle, currentCleanArtist) = cleanTrackAndArtist(currentTitle, currentArtist)
                 if (cleanTitle.equals(currentCleanTitle, ignoreCase = true) &&
                     cleanArtist.equals(currentCleanArtist, ignoreCase = true)) {
                     return true
                 }
            }
            return false
        }
"""
content = content.replace(body_to_replace, new_body)

# Replace the Last.fm strategy block to use isDuplicate
content = content.replace("""val validCandidate = results?.firstOrNull { !sessionHistory.contains(it.id) }""", """val validCandidate = results?.firstOrNull { !isDuplicate(it) }""")

# Replace the Repo strategy block to use isDuplicate
content = content.replace("""val candidate = recommendations.filter { !sessionHistory.contains(it.id) }.randomOrNull()""", """val candidate = recommendations.filter { !isDuplicate(it) }.randomOrNull()""")

# Replace the simple random strategy
strategies_to_replace = """        // Simple Random Strategy Selection (Fallback if repo empty)
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
        }"""

new_strategies = """        // Simple Random Strategy Selection (Fallback if repo empty)
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
        }"""

content = content.replace(strategies_to_replace, new_strategies)


with open("app/src/main/java/com/example/musicdownloader/utils/SmartShuffleManager.kt", "w") as f:
    f.write(content)
