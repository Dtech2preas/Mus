package com.example.musicdownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PipedClient {

    private val instances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacy.com.de",
        "https://pipedapi.drgns.space",
        "https://api.piped.kotatsu.org"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        for (baseUrl in instances) {
            try {
                return@withContext searchOnInstance(baseUrl, query)
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue to next instance
            }
        }
        throw IOException("All Piped instances failed for search")
    }

    private fun searchOnInstance(baseUrl: String, query: String): List<VideoItem> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search?q=$encodedQuery&filter=music_videos"
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val jsonString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(jsonString)
            val items = json.optJSONArray("items") ?: return emptyList()
            val videos = mutableListOf<VideoItem>()

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val type = item.optString("type")
                if (type != "stream") continue

                val title = item.optString("title")
                val uploader = item.optString("uploaderName")
                val durationSeconds = item.optLong("duration", 0)
                val thumbnail = item.optString("thumbnail")

                // The item object has a 'url' field which is usually "/watch?v=ID"
                val relativeUrl = item.optString("url")
                val id = relativeUrl.substringAfter("v=")

                val webUrl = "https://www.youtube.com/watch?v=$id"

                videos.add(
                    VideoItem(
                        id = id,
                        title = title,
                        duration = formatDuration(durationSeconds),
                        uploader = uploader,
                        thumbnailUrl = thumbnail,
                        webUrl = webUrl
                    )
                )
            }
            return videos
        }
    }

    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        for (baseUrl in instances) {
            try {
                return@withContext getStreamUrlOnInstance(baseUrl, videoId)
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue to next instance
            }
        }
        throw IOException("All Piped instances failed for stream URL")
    }

    private fun getStreamUrlOnInstance(baseUrl: String, videoId: String): String {
        val url = "$baseUrl/streams/$videoId"
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val jsonString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(jsonString)
            val audioStreams = json.optJSONArray("audioStreams") ?: throw IOException("No audio streams found")

            // Find best m4a stream
            var bestUrl = ""
            var maxBitrate = -1

            for (i in 0 until audioStreams.length()) {
                val stream = audioStreams.optJSONObject(i) ?: continue
                val mimeType = stream.optString("mimeType") // e.g. "audio/mp4"
                val bitrate = stream.optInt("bitrate", 0)
                val streamUrl = stream.optString("url")

                // Prefer m4a/mp4
                if (mimeType.contains("mp4") || mimeType.contains("m4a")) {
                    if (bitrate > maxBitrate) {
                        maxBitrate = bitrate
                        bestUrl = streamUrl
                    }
                }
            }

            // Fallback to any audio if no mp4 found
            if (bestUrl.isEmpty() && audioStreams.length() > 0) {
                 val stream = audioStreams.getJSONObject(0)
                 bestUrl = stream.optString("url")
            }

            if (bestUrl.isEmpty()) throw IOException("No valid audio stream url found")
            return bestUrl
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0:00"
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }
}
