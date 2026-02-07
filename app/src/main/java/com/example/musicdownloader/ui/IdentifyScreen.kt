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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class IdentifyState {
    INITIALIZING, // Waiting for Shazam button
    READY,        // Button found, ready to listen
    LISTENING,    // User clicked, listening to audio
    SEARCHING,    // Searching for match
    ERROR         // Failed to identify or timeout
}

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
        var currentState by remember { mutableStateOf(IdentifyState.INITIALIZING) }
        val coroutineScope = rememberCoroutineScope()

        // Javascript to find the button
        val findButtonJs = """
            (function() {
                var btn = document.querySelector('[aria-label*="Shazam" i]');
                if (!btn) btn = document.querySelector('[aria-label*="Listening" i]');
                if (!btn) {
                    var all = document.querySelectorAll('div[role="button"], button');
                    for(var i=0; i<all.length; i++) {
                        if(all[i].innerText && all[i].innerText.toLowerCase().includes('shazam')) {
                            btn = all[i];
                            break;
                        }
                    }
                }
                return btn != null;
            })();
        """.trimIndent()

        // Javascript to click the button
        val clickButtonJs = """
            (function() {
                var btn = document.querySelector('[aria-label*="Shazam" i]');
                if (!btn) btn = document.querySelector('[aria-label*="Listening" i]');
                if (!btn) {
                     var all = document.querySelectorAll('div[role="button"], button');
                    for(var i=0; i<all.length; i++) {
                        if(all[i].innerText && all[i].innerText.toLowerCase().includes('shazam')) {
                            btn = all[i];
                            break;
                        }
                    }
                }
                if (btn) {
                    btn.click();
                    return true;
                }
                return false;
            })();
        """.trimIndent()

        // Javascript to scroll (trigger button appearance)
        val scrollJs = """
            window.scrollTo({ top: 500, behavior: 'smooth' });
            setTimeout(function() { window.scrollTo({ top: 0, behavior: 'smooth' }); }, 800);
        """.trimIndent()

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
                            // If we are in LISTENING or SEARCHING state, finding a URL means success
                            // We can reuse the extraction logic here
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

        // Polling logic for button detection and timeouts
        LaunchedEffect(webView, currentState) {
            if (currentState == IdentifyState.INITIALIZING) {
                var checks = 0
                while(currentState == IdentifyState.INITIALIZING) {
                    delay(2000) // check every 2 seconds
                    webView?.evaluateJavascript(findButtonJs) { result ->
                        if (result == "true") {
                            currentState = IdentifyState.READY
                        } else {
                            checks++
                            if (checks >= 15) { // 30 seconds timeout
                                 currentState = IdentifyState.ERROR
                            } else if (checks % 3 == 0) { // Every 6 seconds
                                webView?.evaluateJavascript(scrollJs, null)
                            }
                        }
                    }
                }
            } else if (currentState == IdentifyState.LISTENING) {
                // Timeout for listening phase
                delay(20000) // 20 seconds
                if (!hasFoundResult && currentState == IdentifyState.LISTENING) {
                    currentState = IdentifyState.ERROR
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // The hidden WebView
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

                        webViewClient = object : WebViewClient() {}
                        loadUrl("https://www.shazam.com/")
                        webView = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.01f), // Almost invisible but technically rendered
                update = { webView = it }
            )

            // The Custom Overlay
            IdentifyOverlay(
                state = currentState,
                onStartListening = {
                    currentState = IdentifyState.LISTENING
                    // Trigger JS click
                    webView?.evaluateJavascript(clickButtonJs, null)
                },
                onRetry = {
                    currentState = IdentifyState.INITIALIZING
                    hasFoundResult = false
                    webView?.reload()
                }
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

@Composable
fun PulsingCircle() {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun IdentifyOverlay(
    state: IdentifyState,
    onStartListening: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn() with fadeOut()
            },
            label = "IdentifyState"
        ) { targetState ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (targetState) {
                    IdentifyState.INITIALIZING -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Initializing D-TECH AI...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IdentifyState.READY -> {
                        Button(
                            onClick = onStartListening,
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Listen",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Tap to Identify",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IdentifyState.LISTENING -> {
                        Box(contentAlignment = Alignment.Center) {
                            PulsingCircle()
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = "Listening",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Listening...",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IdentifyState.SEARCHING -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Analyzing Match...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IdentifyState.ERROR -> {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Error",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Couldn't identify song",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}
