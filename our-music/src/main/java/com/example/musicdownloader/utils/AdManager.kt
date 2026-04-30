package com.example.musicdownloader.utils

import android.content.Context
import com.example.musicdownloader.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.random.Random

object AdManager {
    private const val PREFS_NAME = "ad_prefs"
    private const val KEY_LAST_FETCH = "last_fetch_time"
    private const val KEY_SHOW_ADS = "show_ads"
    private const val KEY_AD_LINKS = "ad_links"
    private const val KEY_TIME_THRESHOLD = "time_threshold_mins"
    private const val KEY_SONG_THRESHOLD = "song_threshold_count"

    private val client = OkHttpClient()

    suspend fun syncAdConfig(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        val now = System.currentTimeMillis()

        // Fetch every 24 hours
        if (now - lastFetch < 24 * 60 * 60 * 1000L) {
            return@withContext
        }

        try {
            // Fetch ads.txt
            val adsRequest = Request.Builder()
                .url("https://www.dtech-services.co.za/ads.txt")
                .build()

            var showAds = false
            client.newCall(adsRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string()?.trim()?.lines()?.firstOrNull()
                    showAds = content.equals("yes", ignoreCase = true)
                }
            }

            // Fetch ads-links.txt
            val linksRequest = Request.Builder()
                .url("https://www.dtech-services.co.za/ads-links.txt")
                .build()

            var adLinks = emptyList<String>()
            var timeThreshold = 60
            var songThreshold = 7

            client.newCall(linksRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val lines = response.body?.string()?.trim()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
                    if (lines != null && lines.size >= 2) {
                        // Last line is song threshold
                        songThreshold = lines.last().toIntOrNull() ?: 7
                        // Second to last line is time threshold
                        timeThreshold = lines[lines.size - 2].toIntOrNull() ?: 60
                        // The rest are URLs
                        if (lines.size > 2) {
                            adLinks = lines.subList(0, lines.size - 2)
                        }
                    }
                }
            }

            prefs.edit()
                .putLong(KEY_LAST_FETCH, now)
                .putBoolean(KEY_SHOW_ADS, showAds)
                .putString(KEY_AD_LINKS, adLinks.joinToString(","))
                .putInt(KEY_TIME_THRESHOLD, timeThreshold)
                .putInt(KEY_SONG_THRESHOLD, songThreshold)
                .apply()

            AppLogger.log("AdManager synced. showAds=$showAds, links=${adLinks.size}, time=$timeThreshold, songs=$songThreshold")

        } catch (e: Exception) {
            AppLogger.log("Failed to sync ad config: ${e.message}")
        }
    }

    fun isAdsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_ADS, false)
    }

    fun getAdLinks(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val linksStr = prefs.getString(KEY_AD_LINKS, "") ?: ""
        return if (linksStr.isEmpty()) emptyList() else linksStr.split(",")
    }

    fun getRandomAdLink(context: Context): String? {
        val links = getAdLinks(context)
        if (links.isEmpty()) return null
        return links[Random.nextInt(links.size)]
    }

    fun getTimeThresholdMins(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TIME_THRESHOLD, 60)
    }

    fun getSongThreshold(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SONG_THRESHOLD, 7)
    }
}
