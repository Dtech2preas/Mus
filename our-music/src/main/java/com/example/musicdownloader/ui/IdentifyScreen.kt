package com.example.musicdownloader.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IdentifyScreen(
    onSongFound: (String) -> Unit
) {
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasAudioPermission = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (hasAudioPermission) {
        var webView: WebView? by remember { mutableStateOf(null) }
        var hasFoundResult by remember { mutableStateOf(false) }

        // Proper cleanup
        DisposableEffect(Unit) {
            onDispose {
                webView?.destroy()
                webView = null
            }
        }

        // Logic to poll for result URL
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (hasFoundResult) break

                webView?.let { view ->
                    val url = view.url
                    if (url != null) {
                        if (url.contains("/song/") || url.contains("/track/")) {
                            // If we find a result, we extract it
                            extractResult(view, url) { query ->
                                if (query.isNotBlank() && !hasFoundResult) {
                                    hasFoundResult = true
                                    onSongFound(query)
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // The fully visible WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.allowContentAccess = true
                        settings.allowFileAccess = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        // Keep mobile User Agent for proper mobile site rendering
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        clearCache(true)
                        clearHistory()

                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) {
                                val requestedResources = request.resources ?: emptyArray()
                                val resourcesToGrant = mutableListOf<String>()
                                for (res in requestedResources) {
                                    if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            resourcesToGrant.add(res)
                                        }
                                    } else if (res != PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                                        resourcesToGrant.add(res)
                                    }
                                }
                                if (resourcesToGrant.isNotEmpty()) {
                                    request.grant(resourcesToGrant.toTypedArray())
                                } else {
                                    request.deny()
                                }
                            }
                        }

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
                        loadUrl("https://www.shazam.com/")
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(), // No alpha modification, fully visible
                update = { webView = it }
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

// Helper to extract result
fun extractResult(view: WebView, url: String, onFound: (String) -> Unit) {
    if (url.contains("/song/")) {
        try {
            val uri = android.net.Uri.parse(url)
            val pathSegments = uri.pathSegments
            if (pathSegments.size >= 3 && pathSegments[0] == "song") {
                val slug = pathSegments.last()
                val searchQuery = slug.replace("-", " ")
                onFound(searchQuery)
            }
        } catch (e: Exception) {
            Log.e("IdentifyScreen", "Error parsing song URL", e)
        }
    } else if (url.contains("/track/")) {
        val js = "(function() { " +
                "var h1 = document.querySelector('h1')?.innerText || ''; " +
                "var h2 = document.querySelector('h2')?.innerText || ''; " +
                "var t = document.title || ''; " +
                "return h1 + '|||' + h2 + '|||' + t; " +
                "})();"
        view.evaluateJavascript(js) { result ->
            if (result != null && result != "null") {
                val rawString = result.trim('"')
                val parts = rawString.split("|||")
                if (parts.size >= 3) {
                    val song = parts[0].trim()
                    val artist = parts[1].trim()
                    val pageTitle = parts[2].trim()
                    var searchQuery = ""
                    if (song.isNotBlank() && artist.isNotBlank()) {
                        searchQuery = "$artist - $song"
                    } else if (song.isNotBlank()) {
                        searchQuery = song
                    } else if (pageTitle.isNotBlank()) {
                        searchQuery = pageTitle.replace("| Shazam", "").replace("- Shazam", "").trim()
                    }
                    if (searchQuery.isNotBlank() && searchQuery != "Shazam") {
                        onFound(searchQuery)
                    }
                }
            }
        }
    }
}
