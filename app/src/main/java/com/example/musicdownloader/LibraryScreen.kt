package com.example.musicdownloader

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
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
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    // View Mode State
    var selectedArtist by remember { mutableStateOf<String?>(null) } // null = All Songs
    var showingArtistsMode by remember { mutableStateOf(false) }
    var showingPlaylistsMode by remember { mutableStateOf(false) } // Playlist Mode
    var showingLikedOnly by remember { mutableStateOf(false) } // Liked Filter

    // Dialog States
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<com.example.musicdownloader.data.Song?>(null) }

    // Data from ViewModel
    val playlists by viewModel.playlists.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()

    // Observe active downloads
    val workManager = remember { WorkManager.getInstance(context) }
    val workInfos by workManager.getWorkInfosByTagLiveData("download").observeAsState(emptyList())
    val downloadingInfos = workInfos.filter { it.state == WorkInfo.State.RUNNING }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var newPlaylistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            showCreatePlaylistDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add To Playlist Dialog
    if (showAddToPlaylistDialog != null) {
        val songToAdd = showAddToPlaylistDialog!!
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = null },
            title = { Text("Add to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists found. Create one first!")
                } else {
                    LazyColumn {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                modifier = Modifier.clickable {
                                    viewModel.addSongToPlaylist(playlist.id, songToAdd.id)
                                    showAddToPlaylistDialog = null
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Added to ${playlist.name}")
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 1. Top Bar Area: Search & Chips
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Find in library") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Playlists Chip
            FilterChip(
                selected = showingPlaylistsMode,
                onClick = {
                    showingPlaylistsMode = !showingPlaylistsMode
                    // Reset other modes
                    if (showingPlaylistsMode) {
                        showingArtistsMode = false
                        showingLikedOnly = false
                        selectedArtist = null
                    }
                },
                label = { Text("Playlists") }
            )

            // Artists Chip
            FilterChip(
                selected = showingArtistsMode,
                onClick = {
                    showingArtistsMode = !showingArtistsMode
                    if (showingArtistsMode) {
                        showingPlaylistsMode = false
                        // Keep Liked Filter? Maybe. Let's reset for simplicity.
                        showingLikedOnly = false
                        selectedArtist = null
                    } else {
                         selectedArtist = null // Reset filter when toggling mode off
                    }
                },
                label = { Text("Artists") }
            )

            // Show Active Filter Chips (Artist or Liked)
            if (selectedArtist != null) {
                InputChip(
                    selected = true,
                    onClick = { selectedArtist = null },
                    label = { Text("Artist: $selectedArtist X") }
                )
            }
            if (showingLikedOnly) {
                 InputChip(
                    selected = true,
                    onClick = { showingLikedOnly = false },
                    label = { Text("Liked Songs X") },
                    leadingIcon = { Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Downloads Section (always visible if active)
        if (downloadingInfos.isNotEmpty()) {
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloadingInfos) { workInfo ->
                    val title = workInfo.progress.getString("title") ?: "Downloading song..."
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(title, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main Content Switcher
        if (showingPlaylistsMode) {
             // SHOW PLAYLISTS
             Box(modifier = Modifier.weight(1f)) {
                 if (playlists.isEmpty()) {
                     Text("No playlists yet.", modifier = Modifier.align(Alignment.Center))
                 } else {
                     LazyColumn(contentPadding = contentPadding) {
                         items(playlists) { playlist ->
                             ListItem(
                                 headlineContent = { Text(playlist.name) },
                                 leadingContent = { Icon(Icons.Default.List, contentDescription = null) },
                                 modifier = Modifier.clickable {
                                     // Placeholder: Play playlist or Show songs
                                     // For now just toast as per requirements we only need to Create and List them
                                     // But likely we want to view them. For now, basic implementation.
                                     Toast.makeText(context, "Playing Playlist ${playlist.name}", Toast.LENGTH_SHORT).show()
                                 }
                             )
                         }
                     }
                 }
                 FloatingActionButton(
                     onClick = { showCreatePlaylistDialog = true },
                     modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                 ) {
                     Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                 }
             }

        } else if (showingArtistsMode && selectedArtist == null) {
            // SHOW ARTIST LIST
            val artists = remember(songs) { songs.map { it.artist }.distinct().sorted() }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = contentPadding
            ) {
                items(artists) { artist ->
                    ListItem(
                        headlineContent = { Text(artist) },
                        modifier = Modifier.clickable {
                            selectedArtist = artist
                            showingArtistsMode = false // Switch back to song list with filter
                        }
                    )
                }
            }
        } else {
            // SHOW SONG LIST (Filtered)
            val filteredSongs = remember(songs, searchQuery, selectedArtist, showingLikedOnly, likedSongIds) {
                songs.filter { song ->
                    val matchesSearch = if (searchQuery.isBlank()) true else
                        (song.title.contains(searchQuery, ignoreCase = true) || song.artist.contains(searchQuery, ignoreCase = true))
                    val matchesArtist = if (selectedArtist == null) true else song.artist == selectedArtist
                    val matchesLiked = if (showingLikedOnly) likedSongIds.contains(song.id) else true

                    matchesSearch && matchesArtist && matchesLiked
                }
            }

            if (filteredSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No music found.")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp), // Compact
                    contentPadding = contentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    // Static "Liked Songs" item if not filtering and search empty
                    if (searchQuery.isEmpty() && selectedArtist == null && !showingLikedOnly) {
                         item {
                             ListItem(
                                 headlineContent = { Text("Liked Songs") },
                                 leadingContent = {
                                     Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                 },
                                 modifier = Modifier.clickable {
                                     showingLikedOnly = true
                                 }
                             )
                             Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                         }
                    }

                    items(filteredSongs, key = { it.id }) { song ->
                        // Swipe to Queue
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.addToQueue(song)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Added to Queue")
                                    }
                                    false
                                } else {
                                    false
                                }
                            }
                        )

                        // Menu State for "More"
                        var showMenu by remember { mutableStateOf(false) }

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF006064))
                                        .padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Queue", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Queue", color = Color.White)
                                }
                            },
                            enableDismissFromEndToStart = false
                        ) {
                             Box {
                                 MusicRowItem(
                                    title = song.title,
                                    artist = song.artist,
                                    thumbnailUrl = song.thumbnailUrl,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentMediaItem?.mediaId == song.id,
                                    isLibrary = true,
                                    onClick = {
                                        viewModel.playLocalSong(
                                            id = song.id,
                                            title = song.title,
                                            artist = song.artist,
                                            thumbnailUrl = song.thumbnailUrl
                                        )
                                    },
                                    onAction = { showMenu = true } // Open Menu instead of deleting immediately
                                )

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add to Playlist") },
                                        onClick = {
                                            showMenu = false
                                            showAddToPlaylistDialog = song
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            showMenu = false
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
        }
    }
}
