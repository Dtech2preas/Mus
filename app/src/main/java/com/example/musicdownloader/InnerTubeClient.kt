package com.example.musicdownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object InnerTubeClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .dns(IPv4Dns)
        .build()

    // Public InnerTube API Key (WEB Client)
    private const val API_KEY = "AIzaSyD2" + "C07e2_" + "49XC2" + "sT5e" + "0M_" + "E2" // Obfuscated slightly

    private const val BASE_URL = "https://youtubei.googleapis.com/youtubei/v1/search?key=$API_KEY"
    private const val PLAYER_URL = "https://youtubei.googleapis.com/youtubei/v1/player?key=$API_KEY"

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20230920.00.00") // A relatively recent version
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("query", query)
            // "EgIQAQ%3D%3D" is the param for "Video" filter.
            // Without it, it searches everything (channels, playlists).
            // But to be safe, we can just search everything and filter in code, or use params.
            // "EgIQAQ==" corresponds to "Type: Video"
            put("params", "EgIQAQ%3D%3D")
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(BASE_URL)
            .post(requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube Search failed: ${response.code}")

            val responseString = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(responseString)

            return@withContext parseInnerTubeResponse(json)
        }
    }

    suspend fun getStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "IOS")
                    put("clientVersion", "19.29.1")
                    put("deviceMake", "Apple")
                    put("deviceModel", "sq1")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(PLAYER_URL)
            .post(requestBody)
            .addHeader("User-Agent", NetworkUtils.USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube Player failed: ${response.code}")

            val responseString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(responseString)

            return@withContext parsePlayerResponse(json)
        }
    }

    private fun parseInnerTubeResponse(json: JSONObject): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        try {
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents == null) return emptyList()

            for (i in 0 until contents.length()) {
                val itemSection = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")
                val results = itemSection?.optJSONArray("contents") ?: continue

                for (j in 0 until results.length()) {
                    val videoRenderer = results.optJSONObject(j)?.optJSONObject("videoRenderer")
                    if (videoRenderer != null) {
                        try {
                            val videoId = videoRenderer.optString("videoId")
                            val title = videoRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"

                            // Duration
                            val lengthText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: ""

                            // Uploader
                            val ownerText = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"

                            // Thumbnail (get the last/largest one)
                            val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumbnailUrl = if (thumbnails != null && thumbnails.length() > 0) {
                                thumbnails.optJSONObject(thumbnails.length() - 1).optString("url")
                            } else {
                                "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                            }

                            if (videoId.isNotEmpty()) {
                                videos.add(
                                    VideoItem(
                                        id = videoId,
                                        title = title,
                                        duration = lengthText,
                                        uploader = ownerText,
                                        thumbnailUrl = thumbnailUrl,
                                        webUrl = "https://www.youtube.com/watch?v=$videoId"
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return videos
    }

    private fun parsePlayerResponse(json: JSONObject): String {
        val streamingData = json.optJSONObject("streamingData") ?: throw IOException("No streaming data")
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: throw IOException("No adaptive formats")

        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.optJSONObject(i) ?: continue
            val mimeType = format.optString("mimeType")
            val url = format.optString("url")

            if (mimeType.contains("audio/mp4") && url.isNotEmpty()) {
                return url
            }
        }

        // Fallback: search for any audio if no mp4 audio found
        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.optJSONObject(i) ?: continue
            val mimeType = format.optString("mimeType")
            val url = format.optString("url")

            if (mimeType.contains("audio") && url.isNotEmpty()) {
                return url
            }
        }

        throw IOException("No valid audio stream found")
    }
}
