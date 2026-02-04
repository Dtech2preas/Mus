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

        // Polling to check for success state
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (hasFound) break

                webView?.let { view ->
                    val url = view.url
                    val title = view.title

                    if (url != null && url.contains("/track/")) {
                        // Found a track!
                        // Try to extract title from document title
                        // Format is usually: "Song Name - Artist | Shazam" or just "Song Name - Artist"
                        if (!title.isNullOrBlank()) {
                            val cleanedTitle = title.replace("| Shazam", "")
                                                    .replace("- Shazam", "")
                                                    .trim()

                            // Heuristic: If it looks like a song (has content), trigger
                            if (cleanedTitle.isNotBlank() && cleanedTitle != "Shazam") {
                                hasFound = true
                                onSongFound(cleanedTitle)
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
                    settings.mediaPlaybackRequiresUserGesture = false

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            request.grant(request.resources)
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
