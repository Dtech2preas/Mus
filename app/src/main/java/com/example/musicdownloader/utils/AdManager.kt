package com.example.musicdownloader.utils

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.example.musicdownloader.AppLogger
import com.example.musicdownloader.UserPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.TimeUnit

object AdManager {

    private val adLinks = listOf(
        "https://otieu.com/4/10250311",
        "https://otieu.com/4/9515888",
        "https://otieu.com/4/10205357",
        "https://otieu.com/4/10358600"
    )

    // Use Channel with Conflated behavior to ensure the event is buffered if no collector is present (App Start race condition),
    // but consumed once collected so it doesn't replay indefinitely (Zombie event on rotation).
    private val _showAdDialogEvent = Channel<Unit>(Channel.CONFLATED)
    val showAdDialogEvent = _showAdDialogEvent.receiveAsFlow()

    // Track when the ad was clicked to enforce viewing duration
    var lastAdClickTime: Long = 0L

    fun showRandomAd(context: Context) {
        try {
            val url = adLinks.random()
            val customTabsIntent = CustomTabsIntent.Builder().build()

            // Record timestamp before launch
            lastAdClickTime = System.currentTimeMillis()

            customTabsIntent.launchUrl(context, Uri.parse(url))
            UserPreferences.setAdShownToday(context)
            AppLogger.log("[AdManager] Showing Ad: $url at $lastAdClickTime")
        } catch (e: Exception) {
            AppLogger.log("[AdManager] Failed to show ad: ${e.message}")
            // Reset if failed
            lastAdClickTime = 0L
        }
    }

    fun checkSmartTrigger(context: Context) {
        if (UserPreferences.isAdShownToday(context)) {
            AppLogger.log("[AdManager] Ad already shown today. Skipping.")
            return
        }

        val downloads = UserPreferences.getDailyDownloadCount(context)
        val firstOpenTime = UserPreferences.getFirstOpenTime(context)
        val hoursSinceFirstOpen = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - firstOpenTime)

        AppLogger.log("[AdManager] Checking Trigger: Downloads=$downloads, Hours=$hoursSinceFirstOpen")

        if (downloads >= 15 || hoursSinceFirstOpen >= 10) {
            AppLogger.log("[AdManager] Trigger Met! Requesting Ad Dialog.")
            _showAdDialogEvent.trySend(Unit)
        }
    }

    fun incrementDownloadCount(context: Context) {
        UserPreferences.incrementDailyDownloadCount(context)
        checkSmartTrigger(context)
    }
}
