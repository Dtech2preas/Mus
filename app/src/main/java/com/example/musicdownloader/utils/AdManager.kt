package com.example.musicdownloader.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.example.musicdownloader.AppLogger

object AdManager {

    private val adLinks = listOf(
        "https://otieu.com/4/10250311",
        "https://otieu.com/4/9515888",
        "https://otieu.com/4/10205357",
        "https://otieu.com/4/10358600"
    )

    fun openRandomAd(context: Context) {
        if (!isOnline(context)) {
            AppLogger.log("[AdManager] Device is offline. Skipping ad.")
            return
        }

        try {
            val url = adLinks.random()
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, Uri.parse(url))
            AppLogger.log("[AdManager] Opening Ad: $url")
        } catch (e: Exception) {
            AppLogger.log("[AdManager] Failed to open ad: ${e.message}")
        }
    }

    private fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
