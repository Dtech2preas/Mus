package com.example.musicdownloader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(emptyList<File>()) }

    // Refresh files on launch
    LaunchedEffect(Unit) {
        files = MusicRepository.getDownloadedFiles(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Downloaded Music",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloaded songs yet.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = contentPadding
            ) {
                items(files) { file ->
                    DownloadedFileRow(file = file, onClick = {
                        // Play the local file
                        // Note: We might lack metadata (title/artist) if we just read files.
                        // We will try to parse from filename if possible or just use filename.
                        // Filename format from YoutubeClient: "ID.ext" or "Title.ext"
                        // But we saved as "ID.ext" in MusicRepository.downloadAndPlay.
                        // Wait, user might want to see Title.

                        // NOTE: In MusicRepository.downloadAndPlay, we saved as "ID.ext".
                        // This makes it hard to show Title in UI.
                        // Ideally we should save a metadata sidecar or name it "Title [ID].ext".
                        // BUT for now, let's just play it.

                        val mediaItem = MediaItem.Builder()
                            .setUri(android.net.Uri.fromFile(file))
                            .setMediaId(file.name)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(file.nameWithoutExtension)
                                    .build()
                            )
                            .build()
                        MusicControllerManager.playMedia(mediaItem)
                    })
                }
            }
        }
    }
}

@Composable
fun DownloadedFileRow(file: File, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.length() / 1024 / 1024} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text("▶", style = MaterialTheme.typography.headlineSmall)
        }
    }
}
