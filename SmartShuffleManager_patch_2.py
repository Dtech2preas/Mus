import re
with open("app/src/main/java/com/example/musicdownloader/utils/SmartShuffleManager.kt", "r") as f:
    content = f.read()

# Update functions with isDuplicate signature
fav_replace = """    private suspend fun getRecommendationFromFavorites(context: Context): VideoItem? {"""
fav_new = """    private suspend fun getRecommendationFromFavorites(context: Context, isDuplicate: (VideoItem) -> Boolean): VideoItem? {"""
content = content.replace(fav_replace, fav_new)

top_replace = """    private suspend fun getRecommendationFromTopArtist(context: Context): VideoItem? {"""
top_new = """    private suspend fun getRecommendationFromTopArtist(context: Context, isDuplicate: (VideoItem) -> Boolean): VideoItem? {"""
content = content.replace(top_replace, top_new)

gen_replace = """    private suspend fun getRecommendationFromGenre(context: Context): VideoItem? {"""
gen_new = """    private suspend fun getRecommendationFromGenre(context: Context, isDuplicate: (VideoItem) -> Boolean): VideoItem? {"""
content = content.replace(gen_replace, gen_new)


# Replace filter inside functions
content = content.replace("""val candidates = likedIds.filter { !sessionHistory.contains(it) }""", """// To properly use isDuplicate on IDs, we would need metadata, but we only have IDs. Let's just check session/history here
        val longTermHistoryIds = AppDatabase.getDatabase(context).playHistoryDao().getAllHistoryIdsSync() ?: emptyList()
        val candidates = likedIds.filter { !sessionHistory.contains(it) && !longTermHistoryIds.contains(it) }""")

content = content.replace("""val candidates = results.filter { !sessionHistory.contains(it.id) }""", """val candidates = results.filter { !isDuplicate(it) }""")

# Add getRecommendationFromRelated
rel_func = """
    private suspend fun getRecommendationFromRelated(context: Context, videoId: String, isDuplicate: (VideoItem) -> Boolean): VideoItem? {
        AppLogger.log("[SmartShuffle] Strategy: Related (Next)")
        val results = com.example.musicdownloader.InnerTubeClient.getRelatedVideos(videoId)
        if (results.isNullOrEmpty()) return null

        val candidates = results.filter { !isDuplicate(it) }
        return if (candidates.isNotEmpty()) candidates.shuffled().first() else null
    }
}"""
content = content.replace("}\n}", "}\n" + rel_func)

with open("app/src/main/java/com/example/musicdownloader/utils/SmartShuffleManager.kt", "w") as f:
    f.write(content)
