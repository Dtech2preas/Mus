import re
with open("app/src/main/java/com/example/musicdownloader/data/PlayHistoryDao.kt", "r") as f:
    content = f.read()

if "fun getAllHistoryIdsSync" not in content:
    content = content.replace("    fun getAllHistoryIds(): Flow<List<String>>", "    fun getAllHistoryIds(): Flow<List<String>>\n\n    @Query(\"SELECT songId FROM play_history ORDER BY id DESC LIMIT 500\")\n    fun getAllHistoryIdsSync(): List<String>")

with open("app/src/main/java/com/example/musicdownloader/data/PlayHistoryDao.kt", "w") as f:
    f.write(content)


with open("app/src/main/java/com/example/musicdownloader/utils/SmartShuffleManager.kt", "r") as f:
    content = f.read()

# Fix type mismatch in Repo fallback
# val candidate = recommendations.filter { !isDuplicate(it) }.randomOrNull()
# But recommendations is List<StreamSong> and isDuplicate expects VideoItem
content = content.replace("""val candidate = recommendations.filter { !isDuplicate(it) }.randomOrNull()""", """val candidate = recommendations.filter { !sessionHistory.contains(it.id) && !longTermHistoryIds.contains(it.id) }.randomOrNull()""")
content = content.replace("""val longTermHistoryIds = db.playHistoryDao().getAllHistoryIdsSync() ?: emptyList()""", """val longTermHistoryIds = db.playHistoryDao().getAllHistoryIdsSync()""")
content = content.replace("""val longTermHistoryIds = AppDatabase.getDatabase(context).playHistoryDao().getAllHistoryIdsSync() ?: emptyList()""", """val longTermHistoryIds = AppDatabase.getDatabase(context).playHistoryDao().getAllHistoryIdsSync()""")

with open("app/src/main/java/com/example/musicdownloader/utils/SmartShuffleManager.kt", "w") as f:
    f.write(content)
