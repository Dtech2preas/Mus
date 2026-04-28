package com.example.musicdownloader.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
fun AdPopup(url: String, onDismiss: () -> Unit, autoCloseSeconds: Int? = null) {
    val context = LocalContext.current

    LaunchedEffect(url) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(url))

        // Custom tabs handle their own lifecycle and opening, but we want to simulate autoClose
        // if requested, though this won't strictly "close" the custom tab, it cleans up our state
        if (autoCloseSeconds != null) {
            delay(autoCloseSeconds * 1000L)
            onDismiss()
        } else {
            // We just clear our state immediately after launch so we don't try launching again
            onDismiss()
        }
    }
}
