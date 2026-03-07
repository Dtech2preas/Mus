import re
with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "r") as f:
    content = f.read()

# Add NEXT_URL constant
content = content.replace('private const val PLAYER_URL = "https://youtubei.googleapis.com/youtubei/v1/player?key=$API_KEY"',
'''private const val PLAYER_URL = "https://youtubei.googleapis.com/youtubei/v1/player?key=$API_KEY"
    private const val NEXT_URL = "https://youtubei.googleapis.com/youtubei/v1/next?key=$API_KEY"''')

# Add getRelatedVideos function
new_func = '''    suspend fun getRelatedVideos(videoId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20230920.00.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("videoId", videoId)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(NEXT_URL)
            .post(requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("InnerTube Next failed: ${response.code}")

            val responseString = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(responseString)

            return@withContext parseInnerTubeNextResponse(json)
        }
    }

    private fun parseInnerTubeNextResponse(json: JSONObject): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        try {
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnWatchNextResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONArray("results")

            if (contents == null) return emptyList()

            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i) ?: continue
                val compactVideoRenderer = item.optJSONObject("compactVideoRenderer") ?: continue

                try {
                    val videoId = compactVideoRenderer.optString("videoId")
                    if (videoId.isEmpty()) continue

                    val title = compactVideoRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"
                    val lengthText = compactVideoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                    val ownerText = compactVideoRenderer.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"

                    val thumbnails = compactVideoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val thumbnailUrl = if (thumbnails != null && thumbnails.length() > 0) {
                        thumbnails.optJSONObject(thumbnails.length() - 1).optString("url")
                    } else {
                        "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                    }

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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return videos
    }
'''

content = content.replace('    private fun parseInnerTubeResponse', new_func + '\n    private fun parseInnerTubeResponse')

with open("app/src/main/java/com/example/musicdownloader/InnerTubeClient.kt", "w") as f:
    f.write(content)
