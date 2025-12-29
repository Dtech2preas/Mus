package com.example.musicdownloader.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
    contentPadding: PaddingValues
) {
    var query by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val downloadedIds = remember(librarySongs) { librarySongs.map { it.id }.toSet() }

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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
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

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricPurple)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.results) { video ->
                    val subtitle = if (video.album != null && video.album != "Unknown Album") "${video.uploader} • ${video.album}" else video.uploader
                    MusicCard(
                        title = video.title,
                        subtitle = subtitle,
                        thumbnailUrl = video.thumbnailUrl,
                        isDownloaded = downloadedIds.contains(video.id),
                        downloadProgress = downloadProgress[video.id],
                        onClick = {
                            if (downloadedIds.contains(video.id)) {
                                viewModel.playLocalSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                            } else {
                                Toast.makeText(context, "Downloading ${video.title}...", Toast.LENGTH_SHORT).show()
                                viewModel.downloadAndPlay(video)
                            }
                        },
                        onDownload = {
                             Toast.makeText(context, "Downloading ${video.title}...", Toast.LENGTH_SHORT).show()
                             viewModel.downloadAndPlay(video)
                        },
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    )
                }
            }
        }
    }
}
