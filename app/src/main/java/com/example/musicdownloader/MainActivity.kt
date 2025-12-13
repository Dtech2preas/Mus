package com.example.musicdownloader

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MusicDownloaderScreen()
            }
        }
    }
}

@Composable
fun MusicDownloaderScreen() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
