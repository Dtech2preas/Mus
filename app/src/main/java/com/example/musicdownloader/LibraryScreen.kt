package com.example.musicdownloader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloaded songs yet.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f)
            ) {
                items(songs, key = { it.id }) { song ->
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
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp)
                                    .background(androidx.compose.ui.graphics.Color.Green),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Add to Queue",
                                    color = androidx.compose.ui.graphics.Color.Black,
                                    modifier = Modifier.padding(start = 16.dp),
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
