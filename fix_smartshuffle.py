import re
with open("app/src/main/java/com/example/musicdownloader/utils/SmartShuffleManager.kt", "r") as f:
    content = f.read()

# Fix the body replace which has a syntax error
content = content.replace("""        fun isDuplicate(videoItem: VideoItem): Boolean {
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
""", """        fun isDuplicate(videoItem: VideoItem): Boolean {
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
""")

with open("app/src/main/java/com/example/musicdownloader/utils/SmartShuffleManager.kt", "w") as f:
    f.write(content)
