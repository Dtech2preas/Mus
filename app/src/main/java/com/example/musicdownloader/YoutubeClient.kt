package com.example.musicdownloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

data class VideoItem(
    val id: String,
    val title: String,
    val duration: String,
    val uploader: String,
    val thumbnailUrl: String,
    val webUrl: String,
    val album: String? = null
)

data class DownloadStatus(
    val videoId: String,
    val title: String,
    val progress: Float, // 0-100
    val totalSize: String = "Unknown",
    val speed: String = "0 KB/s",
    val eta: String = "--:--",
    val isPaused: Boolean = false,
    val videoItem: VideoItem? = null
)

object YoutubeClient {

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadStatus>> = _downloadProgress

    // Track cancelled downloads to prevent race conditions from worker updates
    private val cancelledDownloads = java.util.Collections.synchronizedSet(java.util.HashSet<String>())

    // Regex to capture detailed stats: [download]  10.5% of 3.42MiB at 45.00KiB/s ETA 01:10
    private val progressRegex = Pattern.compile("\\[download\\]\\s+(\\d+\\.\\d+)%\\s+of\\s+([~\\d\\.]+\\w+)(?:\\s+at\\s+([\\d\\.]+\\w+/s))?(?:\\s+ETA\\s+([\\d:]+))?")

    // Regex for cleaning titles
    // Matches (...) or [...] containing specific keywords, case insensitive
    private val junkRegex = Regex("(?i)(\\(|\\[).*(official|video|audio|lyrics|4k|hd).*(]|\\))")

    suspend fun searchVideos(context: Context, query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        try {
            // New command: ytmusicsearch5:[QUERY] --flat-playlist --print "%(id)s::%(title)s::%(uploader)s::%(duration)s::%(album)s::%(track)s::%(artist)s"
            // Note: ytmusicsearch queries YouTube Music.
            val request = YoutubeDLRequest("ytmusicsearch5:$query")
            request.addOption("--flat-playlist")
            // We append a custom separator for album, track, artist
            request.addOption("--print", "%(id)s::%(title)s::%(uploader)s::%(duration)s::%(album)s::%(track)s::%(artist)s")
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
                            val rawTitle = parts[1]
                            val uploader = parts[2]
                            val durationRaw = parts[3]
                            // Album might be "NA" or empty if missing
                            val albumRaw = if (parts.size >= 5) parts[4] else "Unknown Album"
                            val trackRaw = if (parts.size >= 6) parts[5] else ""
                            val artistRaw = if (parts.size >= 7) parts[6] else ""

                            val album = if (albumRaw == "NA" || albumRaw.isBlank()) "Unknown Album" else albumRaw
                            val track = if (trackRaw == "NA" || trackRaw.isBlank()) "" else trackRaw
                            val artist = if (artistRaw == "NA" || artistRaw.isBlank()) "" else artistRaw

                            // Metadata Cleaning Logic
                            // 1. Title: Prioritize 'track'. If missing, clean 'title'.
                            val finalTitle = if (track.isNotBlank()) {
                                track
                            } else {
                                cleanTitle(rawTitle)
                            }

                            // 2. Artist: Prioritize 'artist'. If missing, use 'uploader'.
                            val finalArtist = if (artist.isNotBlank()) {
                                artist
                            } else {
                                uploader // Uploader is usually the channel name, which is often the artist
                            }

                            val duration = formatDuration(durationRaw)

                            // Filter: Skip shorts (< 60s)
                            val durationSeconds = try {
                                when {
                                    durationRaw.contains(":") -> {
                                        val p = durationRaw.split(":").map { it.toLong() }
                                        if (p.size == 2) p[0] * 60 + p[1]
                                        else if (p.size == 3) p[0] * 3600 + p[1] * 60 + p[2]
                                        else 0L
                                    }
                                    else -> durationRaw.toDoubleOrNull()?.toLong() ?: 0L
                                }
                            } catch (e: Exception) {
                                0L
                            }

                            if (durationSeconds >= 60) {
                                val webUrl = "https://www.youtube.com/watch?v=$id"
                                val thumb = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

                                videos.add(
                                    VideoItem(
                                        id = id,
                                        title = finalTitle,
                                        duration = duration,
                                        uploader = finalArtist,
                                        thumbnailUrl = thumb,
                                        webUrl = webUrl,
                                        album = album
                                    )
                                )
                            }
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

    private fun cleanTitle(title: String): String {
        var cleaned = title

        // 1. Remove anything after |
        if (cleaned.contains("|")) {
            cleaned = cleaned.split("|")[0]
        }

        // 2. Remove junk regex
        cleaned = junkRegex.replace(cleaned, "")

        // 3. Trim
        return cleaned.trim()
    }

    suspend fun downloadAudio(context: Context, videoId: String, title: String, outputDir: File): File = withContext(Dispatchers.IO) {
        try {
            // Clear cancellation flag if retrying
            cancelledDownloads.remove(videoId)

            // Reset progress for this video
            updateProgress(videoId, title, 0f, "Calculating...", "", "")

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

            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                if (line.isNotBlank()) {
                    AppLogger.log("[yt-dlp] $line")

                    // Parse progress from line
                    val matcher = progressRegex.matcher(line)
                    if (matcher.find()) {
                        val percentStr = matcher.group(1)
                        val totalSize = matcher.group(2) ?: "Unknown"
                        val speed = matcher.group(3) ?: ""
                        val eta = matcher.group(4) ?: ""

                        val percent = percentStr?.toFloatOrNull()
                        if (percent != null) {
                            updateProgress(videoId, title, percent, totalSize, speed, eta)
                        }
                    } else if (progress > 0) {
                         // Fallback to library progress if available
                         updateProgress(videoId, title, progress, "Unknown", "", "")
                    }
                }
            }

            // Find the file that was actually created
            val foundFile = outputDir.listFiles { _, name ->
                 name.startsWith(videoId)
            }?.firstOrNull()

            if (foundFile == null || !foundFile.exists()) {
                 throw java.io.FileNotFoundException("Downloaded file not found in ${outputDir.absolutePath}")
            }

            // Clear progress on success
            updateProgress(videoId, title, 100f, "Done", "", "")
            // Optional: remove from map after a delay? For now, 100% is fine.

            return@withContext foundFile
        } catch (e: Exception) {
            e.printStackTrace()
            // Clear progress on failure
            updateProgress(videoId, title, 0f, "Error", "", "")
            throw e
        }
    }

    // Overload for when we want to pass VideoItem specifically (e.g. at start)
    fun initializeDownloadStatus(video: VideoItem) {
        cancelledDownloads.remove(video.id)
        val current = _downloadProgress.value.toMutableMap()
        current[video.id] = DownloadStatus(
            videoId = video.id,
            title = video.title,
            progress = 0f,
            videoItem = video
        )
        _downloadProgress.value = current
    }

    private fun updateProgress(videoId: String, title: String, percent: Float, totalSize: String, speed: String, eta: String) {
        if (cancelledDownloads.contains(videoId)) return

        val current = _downloadProgress.value.toMutableMap()
        // Preserve videoItem if exists
        val existing = current[videoId]

        // If paused, keep paused status.
        val isPaused = existing?.isPaused ?: false

        current[videoId] = DownloadStatus(
            videoId,
            title,
            percent,
            totalSize,
            speed,
            eta,
            videoItem = existing?.videoItem,
            isPaused = isPaused
        )
        _downloadProgress.value = current
    }

    fun pauseDownloadStatus(videoId: String) {
        val current = _downloadProgress.value.toMutableMap()
        val existing = current[videoId]
        if (existing != null) {
            current[videoId] = existing.copy(isPaused = true)
            _downloadProgress.value = current
        }
    }

    fun removeDownloadStatus(videoId: String) {
        // Mark as cancelled so late updates are ignored
        cancelledDownloads.add(videoId)
        val current = _downloadProgress.value.toMutableMap()
        current.remove(videoId)
        _downloadProgress.value = current
    }

    suspend fun getStreamUrl(context: Context, url: String): StreamInfo = withContext(Dispatchers.IO) {
         try {
            val request = YoutubeDLRequest(url)
            request.addOption("-g")
            request.addOption("-f", "bestaudio[ext=m4a]")
            request.addOption("--no-warnings")
            request.addOption("--force-ipv4")
            request.addOption("--extractor-args", "youtube:player_client=ios")

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
