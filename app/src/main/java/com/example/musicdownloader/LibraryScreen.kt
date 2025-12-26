package com.example.musicdownloader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.musicdownloader.ui.MusicRowItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    snackbarHostState: SnackbarHostState
) {
    val songs by viewModel.librarySongs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSortMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Observe active downloads
    val workManager = remember { WorkManager.getInstance(context) }
    val workInfos by workManager.getWorkInfosByTagLiveData("download").observeAsState(emptyList())
    val downloadingInfos = workInfos.filter { it.state == WorkInfo.State.RUNNING }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloaded Music",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Text("⇅", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Newest First") },
                        onClick = { viewModel.setSortOption(SortOption.NEWEST_FIRST); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("A-Z (Title)") },
                        onClick = { viewModel.setSortOption(SortOption.A_Z); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Z-A (Title)") },
                        onClick = { viewModel.setSortOption(SortOption.Z_A); showSortMenu = false }
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Library") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        )

        // Downloads Section
        if (downloadingInfos.isNotEmpty()) {
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloadingInfos) { workInfo ->
                    val title = workInfo.progress.getString("title") ?: "Downloading song..."

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        val filteredSongs = remember(songs, searchQuery) {
            if (searchQuery.isBlank()) songs
            else songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }
        }

        if (filteredSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (searchQuery.isEmpty()) "No downloaded songs yet." else "No matches found.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    // Swipe to Queue (Start to End)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.addToQueue(song)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Added to Queue")
                                }
                                false // Don't dismiss the item, just trigger action
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp)
                                    .background(Color(0xFF006064))
                                    .padding(start = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = "Queue",
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Queue",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        },
                        enableDismissFromEndToStart = false
                    ) {
                         MusicRowItem(
                            title = song.title,
                            artist = song.artist,
                            thumbnailUrl = song.thumbnailUrl,
                            isPlaying = isPlaying,
                            isCurrentSong = currentMediaItem?.mediaId == song.id,
                            duration = song.duration,
                            onClick = {
                                viewModel.playLocalSong(
                                    id = song.id,
                                    title = song.title,
                                    artist = song.artist,
                                    thumbnailUrl = song.thumbnailUrl
                                )
                            },
                            onDelete = {
                                viewModel.deleteSong(song)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Deleted ${song.title}",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreSong(song)
                                    } else if (result == SnackbarResult.Dismissed) {
                                        viewModel.finalizeDelete(song)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
