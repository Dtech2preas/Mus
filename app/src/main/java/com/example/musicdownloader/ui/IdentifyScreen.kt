package com.example.musicdownloader.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
        var hasFound by remember { mutableStateOf(false) }

        // Proper cleanup to release microphone resources and prevent "Second Try" failure
        DisposableEffect(Unit) {
            onDispose {
                webView?.destroy()
                webView = null
            }
        }

        // Polling to check for success state
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (hasFound) break

                webView?.let { view ->
                    val url = view.url

                    if (url != null && url.contains("/track/")) {
                        // Found a track page, try to extract metadata using JavaScript
                        // We extract h1 (Song), h2 (Artist), and document.title as fallback
                        val js = "(function() { " +
                                "var h1 = document.querySelector('h1')?.innerText || ''; " +
                                "var h2 = document.querySelector('h2')?.innerText || ''; " +
                                "var t = document.title || ''; " +
                                "return h1 + '|||' + h2 + '|||' + t; " +
                                "})();"

                        view.evaluateJavascript(js) { result ->
                            // result is a JSON string, e.g., "\"Song|||Artist|||Title\""
                            if (result != null && result != "null" && !hasFound) {
                                val rawString = result.trim('"') // Remove surrounding quotes from JSON string
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
                                        // Fallback to title parsing
                                        searchQuery = pageTitle.replace("| Shazam", "")
                                            .replace("- Shazam", "")
                                            .trim()
                                    }

                                    if (searchQuery.isNotBlank() && searchQuery != "Shazam") {
                                        hasFound = true
                                        onSongFound(searchQuery)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

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

                    // Clear cache aggressively to ensure fresh permission request state
                    clearCache(true)
                    clearHistory()

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            val requestedResources = request.resources ?: emptyArray()
                            Log.d("IdentifyScreen", "Permission request from ${request.origin}: ${requestedResources.joinToString()}")

                            val resourcesToGrant = mutableListOf<String>()
                            for (res in requestedResources) {
                                if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                    // Check if we have the system permission
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        resourcesToGrant.add(res)
                                    } else {
                                        Log.w("IdentifyScreen", "Cannot grant AUDIO_CAPTURE: System permission missing")
                                    }
                                } else if (res != PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                                    // Grant other resources (like PROTECTED_MEDIA_ID) if requested,
                                    // but explicitly exclude VIDEO_CAPTURE as we don't have camera permission
                                    resourcesToGrant.add(res)
                                }
                            }

                            if (resourcesToGrant.isNotEmpty()) {
                                request.grant(resourcesToGrant.toTypedArray())
                            } else {
                                Log.d("IdentifyScreen", "Denying permission request")
                                request.deny()
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                        }
                    }

                    loadUrl("https://www.shazam.com/")
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                webView = it
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
