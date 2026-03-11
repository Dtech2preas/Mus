package com.dtech.music.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Android's VideoItem equivalent for the InnerTubeClient scope
data class InnerTubeItem(
    val id: String,
    val title: String,
    val author: String,
    val duration: String,
    val thumbnailUrl: String
)

object InnerTubeClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val API_KEY = "AIzaSyD2" + "C07e2_" + "49XC2" + "sT5e" + "0M_" + "E2" // Obfuscated slightly
    private const val BASE_URL = "https://youtubei.googleapis.com/youtubei/v1/search?key=$API_KEY"

    suspend fun search(query: String): List<InnerTubeItem> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20230810.00.00")
                })
            })
            put("query", query)
        }

        val request = Request.Builder()
            .url(BASE_URL)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val jsonResponse = JSONObject(bodyString)
                parseSearchResults(jsonResponse)
            }
        } catch (e: IOException) {
            println("InnerTubeClient Error: ${e.message}")
            emptyList()
        }
    }

    private fun parseSearchResults(jsonResponse: JSONObject): List<InnerTubeItem> {
        val results = mutableListOf<InnerTubeItem>()
        try {
            val contents = jsonResponse.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.optJSONObject(0)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i)
                    val videoRenderer = item.optJSONObject("videoRenderer")
                    if (videoRenderer != null) {
                        val videoId = videoRenderer.optString("videoId")
                        val title = videoRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                        val duration = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                        val author = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                        var thumbnailUrl = ""
                        val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        if (thumbnails != null && thumbnails.length() > 0) {
                            thumbnailUrl = thumbnails.optJSONObject(thumbnails.length() - 1).optString("url")
                        }

                        if (videoId.isNotEmpty() && title.isNotEmpty()) {
                            results.add(InnerTubeItem(videoId, title, author, duration, thumbnailUrl))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Parsing error: ${e.message}")
        }
        return results
    }
}
