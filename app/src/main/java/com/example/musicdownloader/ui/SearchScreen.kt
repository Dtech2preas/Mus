package com.example.musicdownloader.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel

@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues,
    initialQuery: String? = null,
    onViewDownloads: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery ?: "") }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            query = initialQuery
            viewModel.search(initialQuery)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val initializingDownloads by viewModel.initializingDownloads.collectAsStateWithLifecycle()
    val cachedStreamIds by viewModel.cachedStreamIds.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val downloadedIds = remember(librarySongs) { librarySongs.map { it.id }.toSet() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F13))
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search Song", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricPurple,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = ElectricPurple
                ),
                trailingIcon = {
                    IconButton(onClick = { viewModel.search(query) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = ElectricPurple)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) })
            )

            // Download Summary Bar
            if (activeDownloads.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onViewDownloads,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                    shape = RoundedCornerShape(24.dp)
                ) {
                     Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                     Spacer(modifier = Modifier.width(8.dp))
                     Text("Currently downloading ${activeDownloads.size} songs", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricPurple)
                }
            } else {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.results) { video ->
                        val subtitle = if (video.album != null && video.album != "Unknown Album") "${video.uploader} • ${video.album}" else video.uploader
                        MusicRowItem(
                            title = video.title,
                            subtitle = subtitle,
                            thumbnailUrl = video.thumbnailUrl,
                            isLibrary = false,
                            isDownloaded = downloadedIds.contains(video.id),
                            downloadProgress = downloadProgress[video.id]?.progress,
                            isWaiting = initializingDownloads.contains(video.id),
                            isCached = cachedStreamIds.contains(video.id),
                            onClick = {
                                if (downloadedIds.contains(video.id)) {
                                    viewModel.playSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                                } else {
                                    viewModel.playStream(video)
                                }
                            },
                            onDownloadClick = {
                                 viewModel.downloadSong(video)
                            }
                        )
                    }
                }
            }
        }
    }
}
