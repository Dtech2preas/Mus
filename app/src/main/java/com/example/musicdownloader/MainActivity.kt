package com.example.musicdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RequestNotificationPermission()
                MusicPlayerScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (!isGranted) {
                    // User denied permission, maybe show a rationale or just proceed
                }
            }
        )

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(permission)
            }
        }
    }
}

@Composable
fun MusicDownloaderScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onShowLogs: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.downloadMessage) {
        uiState.downloadMessage?.let {
             if (it.startsWith("Downloaded") || it.startsWith("Failed")) {
                 Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                 viewModel.clearDownloadMessage()
             }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

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
                    viewModel.search(query)
                },
                enabled = !uiState.isLoading
            ) {
                Text("Search")
            }
        }

                // Show Logs Button
                Button(
                    onClick = { onShowLogs() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Show Logs")
                }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = contentPadding // Use the passed padding
            ) {
                items(uiState.results) { video ->
                    VideoItemRow(
                        video = video,
                        onDownload = {
                            val dir = File(context.filesDir, "music")
                            if (!dir.exists()) dir.mkdirs()
                            viewModel.download(video, dir)
                        },
                        onPlay = {
                            viewModel.play(video)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoItemRow(video: VideoItem, onDownload: () -> Unit, onPlay: () -> Unit) {
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
            IconButton(onClick = onPlay) {
                Text("▶", style = MaterialTheme.typography.headlineSmall)
            }
            IconButton(onClick = onDownload) {
                Text("⇩", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
