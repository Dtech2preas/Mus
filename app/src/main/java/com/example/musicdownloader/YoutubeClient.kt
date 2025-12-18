package com.example.musicdownloader

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VideoItem(
    val id: String,
    val title: String,
    val duration: String,
    val uploader: String,
    val thumbnailUrl: String,
    val webUrl: String
)

object YoutubeClient {

    suspend fun searchVideos(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        try {
            // New command: ytsearch5:[QUERY] --flat-playlist --print "%(id)s::%(title)s::%(uploader)s::%(duration)s"
            val request = YoutubeDLRequest("ytsearch5:$query")
            request.addOption("--flat-playlist")
            request.addOption("--print", "%(id)s::%(title)s::%(uploader)s::%(duration)s")
            request.addOption("--force-ipv4")

            val response = YoutubeDL.getInstance().execute(request)
            val output = response.out

            if (output.isNullOrBlank()) return@withContext emptyList()

            // Parse output line-by-line
            output.lines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        val parts = line.split("::")
                        if (parts.size >= 4) {
                            val id = parts[0]
                            val title = parts[1]
                            val uploader = parts[2]
                            val durationRaw = parts[3]

                            val duration = formatDuration(durationRaw)

                            val webUrl = "https://www.youtube.com/watch?v=$id"
                            val thumb = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

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
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            request.addOption("--force-ipv4")

            val response = YoutubeDL.getInstance().execute(request)

            // Success if no exception
            return@withContext outputDir
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun getStreamUrl(url: String): StreamInfo = withContext(Dispatchers.IO) {
         try {
            val request = YoutubeDLRequest(url)
            request.addOption("-g")
            request.addOption("-f", "bestaudio[ext=m4a]")
            request.addOption("--no-warnings")
            request.addOption("--force-ipv4")

            val response = YoutubeDL.getInstance().execute(request)
            val streamUrl = response.out?.trim() ?: ""

            // YT-DLP usually returns direct links, unless using --hls-prefer-native which we aren't
            // But we can check if it looks like m3u8
            val isHls = streamUrl.contains(".m3u8")
            return@withContext StreamInfo(streamUrl, isHls)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun formatDuration(durationObj: Any): String {
        return try {
            val seconds = when (durationObj) {
                is Number -> durationObj.toLong()
                is String -> durationObj.toDoubleOrNull()?.toLong() ?: 0L
                else -> 0L
            }

            if (seconds > 0) {
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
