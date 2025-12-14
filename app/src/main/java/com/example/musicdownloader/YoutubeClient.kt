package com.example.musicdownloader

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class VideoItem(
    val id: String,
    val title: String,
    val duration: String,
    val uploader: String,
    val thumbnailUrl: String,
    val webUrl: String
)

class YoutubeClient {

    suspend fun searchVideos(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        try {
            // ytsearch10:query means search for "query" and return top 10 results
            val request = YoutubeDLRequest("ytsearch10:$query")
            request.addOption("--dump-json")
            request.addOption("--flat-playlist")
            // We don't need --skip-download explicitly with --dump-json as it implies info only, but good to ensure
            request.addOption("--skip-download")

            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out

            if (output.isNullOrBlank()) return@withContext emptyList()

            // The output is line-delimited JSON objects
            output.lines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        val json = JSONObject(line)

                        // Safe parsing logic with checks
                        val id = json.optString("id")
                        val title = json.optString("title")
                        // Duration might be null or numeric in flat-playlist
                        val durationObj = json.opt("duration")
                        val duration = if (durationObj != null) formatDuration(durationObj) else "0:00"

                        val uploader = json.optString("uploader")

                        // Construct URLs
                        val webUrl = "https://www.youtube.com/watch?v=$id"
                        // Standard YT thumbnail URL since flat-playlist might miss it
                        val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

                        if (id.isNotEmpty() && title.isNotEmpty()) {
                            videos.add(
                                VideoItem(
                                    id = id,
                                    title = title,
                                    duration = duration,
                                    uploader = uploader,
                                    thumbnailUrl = thumb,
                                    webUrl = webUrl
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Log parsing error but don't crash
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty list on failure instead of crashing
            return@withContext emptyList()
        }
        return@withContext videos
    }

    suspend fun downloadAudio(url: String, outputDir: File): File = withContext(Dispatchers.IO) {
        try {
            // Download best audio
            val request = YoutubeDLRequest(url)
            // Use worst[ext=m4a] for data saving as requested
            request.addOption("-f", "worst[ext=m4a]")
            request.addOption("-o", File(outputDir, "%(title)s.%(ext)s").absolutePath)

            val response = YoutubeDL.getInstance().execute(request)

            // Success if no exception
            return@withContext outputDir
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun getStreamUrl(url: String): String = withContext(Dispatchers.IO) {
         try {
            val request = YoutubeDLRequest(url)
            request.addOption("-g") // get-url
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best") // Prefer m4a for streaming too if possible, or best audio

            val response = YoutubeDL.getInstance().execute(request)
            val streamUrl = response.out?.trim()

            return@withContext streamUrl ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun formatDuration(durationObj: Any): String {
        return try {
            // If it's a number (seconds)
            if (durationObj is Number) {
                val seconds = durationObj.toLong()
                val m = seconds / 60
                val s = seconds % 60
                String.format("%d:%02d", m, s)
            } else {
                durationObj.toString()
            }
        } catch (e: Exception) {
            "0:00"
        }
    }
}
