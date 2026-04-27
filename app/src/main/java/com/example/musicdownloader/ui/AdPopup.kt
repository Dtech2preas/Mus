package com.example.musicdownloader.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdPopup(url: String, onDismiss: () -> Unit, autoCloseSeconds: Int? = null) {
    if (autoCloseSeconds != null) {
        LaunchedEffect(Unit) {
            delay(autoCloseSeconds * 1000L)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E2A))
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                return handleUrl(view, requestUrl)
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                val requestUrl = url ?: return false
                                return handleUrl(view, requestUrl)
                            }

                            private fun handleUrl(view: WebView?, url: String): Boolean {
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    return false // Let WebView handle it
                                }

                                val context = view?.context ?: return true

                                try {
                                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)

                                    try {
                                        context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        // Fallback to browser fallback URL if provided
                                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                        if (!fallbackUrl.isNullOrEmpty()) {
                                            view.loadUrl(fallbackUrl)
                                            return true
                                        }

                                        // Fallback to Play Store if package is provided
                                        val packageName = intent.`package`
                                        if (!packageName.isNullOrEmpty()) {
                                            try {
                                                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                                                context.startActivity(playStoreIntent)
                                            } catch (e2: Exception) {
                                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                                                context.startActivity(webIntent)
                                            }
                                            return true
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("WebViewFallback", "Error parsing custom URL scheme: $url", e)
                                    try {
                                        val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(genericIntent)
                                    } catch (e2: Exception) {
                                        Log.e("WebViewFallback", "Generic intent failed for url: $url", e2)
                                    }
                                }

                                return true // We handled it, so WebView shouldn't try and fail
                            }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Close button overlay
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0x80000000), shape = RoundedCornerShape(24.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Ad",
                    tint = Color.White
                )
            }
        }
    }
}
