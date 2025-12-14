package com.example.musicdownloader

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private var audioPlayer: AudioPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioPlayer = AudioPlayer(this)
        setContent {
            MaterialTheme {
                MusicDownloaderScreen(audioPlayer)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer?.release()
    }
}

@Composable
fun MusicDownloaderScreen(audioPlayer: AudioPlayer?) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var hasActiveMedia by remember { mutableStateOf(false) }

    // Update isPlaying state using Player.Listener
    DisposableEffect(audioPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
            override fun onEvents(player: Player, events: Player.Events) {
                // Check if there is media item
                hasActiveMedia = player.currentMediaItem != null
            }
        }
        audioPlayer?.addListener(listener)
        // Initial state
        isPlaying = audioPlayer?.isPlaying == true
        // Note: checking currentMediaItem directly might not be reactive without listener,
        // but the listener will catch updates.

        onDispose {
            audioPlayer?.removeListener(listener)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Song") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (query.isNotBlank()) {
                        isLoading = true
                        scope.launch {
                            try {
                                results = YoutubeClient.searchVideos(query)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (downloadStatus.isNotEmpty()) {
            Text(
                text = downloadStatus,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Playback Control UI
        if (hasActiveMedia || isPlaying) {
            if (isPlaying) {
                 Button(onClick = { audioPlayer?.pause() }) {
                     Text("Pause Music")
                 }
            } else {
                 Button(onClick = { audioPlayer?.resume() }) {
                     Text("Resume Music")
                 }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // List of downloaded files
        val musicDir = File(context.filesDir, "music")
        var downloadedFiles by remember { mutableStateOf(emptyList<File>()) }

        // Initial load of downloaded files
        LaunchedEffect(Unit) {
            if (musicDir.exists()) {
                downloadedFiles = musicDir.listFiles()?.toList() ?: emptyList()
            }
        }

        Text("Downloaded Songs:", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.height(150.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
             items(downloadedFiles) { file ->
                 Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .clickable {
                             audioPlayer?.play(file.absolutePath)
                             hasActiveMedia = true
                         }
                         .padding(8.dp),
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     Text(file.name, modifier = Modifier.weight(1f))
                     Text("▶", modifier = Modifier.padding(start = 8.dp))
                 }
             }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Search Results:", style = MaterialTheme.typography.titleMedium)
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results) { video ->
                    VideoItemRow(video) {
                        // On Download Click
                        scope.launch {
                            downloadStatus = "Downloading ${video.title}..."
                            try {
                                val dir = File(context.filesDir, "music")
                                if (!dir.exists()) dir.mkdirs()

                                YoutubeClient.downloadAudio(video.webUrl, dir)
                                downloadStatus = "Downloaded: ${video.title}"
                                Toast.makeText(context, "Saved to ${dir.absolutePath}", Toast.LENGTH_LONG).show()
                                // Refresh list
                                downloadedFiles = dir.listFiles()?.toList() ?: emptyList()
                            } catch (e: Exception) {
                                downloadStatus = "Failed: ${e.message}"
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoItemRow(video: VideoItem, onDownload: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(video.thumbnailUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .padding(end = 8.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.uploader} • ${video.duration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onDownload) {
                // Using a simple text or icon
                // Since we didn't add material-icons-extended, let's use a textual representation or standard icon if available
                // Material3 usually has basic icons. Let's use a simple Text or "V" for download
                Text("⇩", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
