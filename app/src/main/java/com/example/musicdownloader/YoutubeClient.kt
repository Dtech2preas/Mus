package com.example.musicdownloader

import android.content.Context
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

    suspend fun searchVideos(context: Context, query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        try {
            // New command: ytsearch5:[QUERY] --flat-playlist --print "%(id)s::%(title)s::%(uploader)s::%(duration)s"
            val request = YoutubeDLRequest("ytsearch5:$query")
            request.addOption("--flat-playlist")
            request.addOption("--print", "%(id)s::%(title)s::%(uploader)s::%(duration)s")
            request.addOption("--force-ipv4")

            val cookieFile = CookieManager.getCookieFile(context)
            if (cookieFile != null) {
                request.addOption("--cookies", cookieFile.absolutePath)
            }

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

    suspend fun downloadAudio(context: Context, videoId: String, outputDir: File): File = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val request = YoutubeDLRequest(url)

            // Speed fix options and preferred format
            request.addOption("-f", "ba/b")
            request.addOption("-S", "+size,+br")
            request.addOption("--no-check-certificate")
            request.addOption("--extractor-args", "youtube:player_client=android,ios")

            // Use videoId for filename to ensure consistency
            val outputFile = File(outputDir, "$videoId.%(ext)s")
            request.addOption("-o", outputFile.absolutePath)

            request.addOption("--force-ipv4")
            request.addOption("--no-warnings")

            val cookieFile = CookieManager.getCookieFile(context)
            if (cookieFile != null) {
                request.addOption("--cookies", cookieFile.absolutePath)
            }

            YoutubeDL.getInstance().execute(request) { _, _, line ->
                if (line.isNotBlank()) {
                    AppLogger.log("[yt-dlp] $line")
                }
            }

            // Find the file that was actually created
            val foundFile = outputDir.listFiles { _, name ->
                 name.startsWith(videoId)
            }?.firstOrNull()

            if (foundFile == null || !foundFile.exists()) {
                 throw java.io.FileNotFoundException("Downloaded file not found in ${outputDir.absolutePath}")
            }

            return@withContext foundFile
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun getStreamUrl(context: Context, url: String): StreamInfo = withContext(Dispatchers.IO) {
         try {
            val request = YoutubeDLRequest(url)
            request.addOption("-g")
            request.addOption("-f", "bestaudio[ext=m4a]")
            request.addOption("--no-warnings")
            request.addOption("--force-ipv4")

            val cookieFile = CookieManager.getCookieFile(context)
            if (cookieFile != null) {
                request.addOption("--cookies", cookieFile.absolutePath)
            }

            val response = YoutubeDL.getInstance().execute(request) { _, _, line ->
                if (line.isNotBlank()) {
                    AppLogger.log("[yt-dlp stream] $line")
                }
            }
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
