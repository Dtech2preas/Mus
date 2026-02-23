package com.example.musicdownloader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.data.StreamSong
import com.example.musicdownloader.utils.HapticUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    snackbarHostState: SnackbarHostState,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLiked: () -> Unit,
    onNavigateToArtists: () -> Unit
) {
    val songs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val streamSongs by viewModel.streamLibrarySongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State for Search Bar interaction
    var localSearchQuery by remember { mutableStateOf("") }

    // Manage adding to playlist
    var showAddToPlaylistForSong by remember { mutableStateOf<Song?>(null) }

    // Manage Metadata Editing
    var showEditMetadataForSong by remember { mutableStateOf<Song?>(null) }

    // Tabs State
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Downloads", "Stream Library")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = localSearchQuery,
            onValueChange = { localSearchQuery = it },
            label = { Text("Find in library", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricPurple,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Liked Songs Card
            NavigationCard(
                title = "Liked",
                icon = Icons.Default.Favorite,
                color = PremiumGold, // Gold for favorites
                modifier = Modifier.weight(1f),
                onClick = onNavigateToLiked
            )

            // Playlists Card
            NavigationCard(
                title = "Playlists",
                icon = Icons.Default.List,
                color = DTechBlue, // Blue for lists
                modifier = Modifier.weight(1f),
                onClick = onNavigateToPlaylists
            )

            // Artists Card
            NavigationCard(
                title = "Artists",
                icon = Icons.Default.Person,
                color = Color.White, // White for artists
                modifier = Modifier.weight(1f),
                onClick = onNavigateToArtists
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = ElectricPurple,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ElectricPurple
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            color = if (selectedTab == index) Color.White else Color.Gray,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // DOWNLOADS LIST
            val filteredSongs = remember(songs, localSearchQuery) {
                if (localSearchQuery.isBlank()) songs else songs.filter {
                    it.title.contains(localSearchQuery, ignoreCase = true) ||
                    it.artist.contains(localSearchQuery, ignoreCase = true)
                }
            }

            if (filteredSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No downloaded songs", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    items(items = filteredSongs, key = { it.id }) { song ->
                        var showMenu by remember { mutableStateOf(false) }

                        SwipeableSongRow(
                            onSwipeToQueue = {
                                val success = viewModel.addToQueue(song)
                                if (success) {
                                    snackbarHostState.showSnackbar("Added to Queue")
                                }
                                success
                            }
                        ) {
                             Box(modifier = Modifier.background(Color(0xFF0F0F13))) {
                                 val subtitle = if (song.album != "Unknown Album") "${song.artist} • ${song.album}" else song.artist
                                 MusicRowItem(
                                    title = song.title,
                                    subtitle = subtitle,
                                    thumbnailUrl = song.thumbnailUrl,
                                    isLibrary = true,
                                    onClick = {
                                        viewModel.playLocalSong(song.id, song.title, song.artist, song.thumbnailUrl)
                                    },
                                    onOptionClick = { showMenu = true }
                                )

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(Color(0xFF1C1C26))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add to Playlist", color = Color.White) },
                                        onClick = {
                                            showMenu = false
                                            showAddToPlaylistForSong = song
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit Metadata", color = Color.White) },
                                        onClick = {
                                            showMenu = false
                                            showEditMetadataForSong = song
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = Color.Red) },
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
        } else {
            // STREAM LIBRARY LIST
            val filteredStreams = remember(streamSongs, localSearchQuery) {
                if (localSearchQuery.isBlank()) streamSongs else streamSongs.filter {
                    it.title.contains(localSearchQuery, ignoreCase = true) ||
                    it.artist.contains(localSearchQuery, ignoreCase = true)
                }
            }

            if (filteredStreams.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No songs in Stream Library", color = Color.Gray)
                        Text("Play songs or click + to add them here", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    items(items = filteredStreams, key = { it.id }) { streamSong ->
                        var showMenu by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.background(Color(0xFF0F0F13))) {
                             val subtitle = if (streamSong.isManual) "Manual • ${streamSong.artist}" else "Auto • ${streamSong.artist}"

                             // Using MusicRowItem but maybe adding an icon to indicate Cloud/Manual status
                             MusicRowItem(
                                title = streamSong.title,
                                subtitle = subtitle,
                                thumbnailUrl = streamSong.thumbnailUrl,
                                isLibrary = false, // Not local
                                onClick = {
                                    viewModel.playStreamSong(streamSong)
                                },
                                onOptionClick = { showMenu = true },
                                trailingContent = {
                                    if (streamSong.isManual) {
                                        Icon(
                                            Icons.Default.CloudQueue,
                                            contentDescription = "Manual",
                                            tint = PremiumGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF1C1C26))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (streamSong.isManual) "Remove from Stream Library" else "Keep in Stream Library",
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        // Toggle logic handles promote or remove
                                        val videoItem = com.example.musicdownloader.VideoItem(
                                            id = streamSong.id,
                                            title = streamSong.title,
                                            duration = streamSong.duration,
                                            uploader = streamSong.artist,
                                            thumbnailUrl = streamSong.thumbnailUrl,
                                            webUrl = "https://www.youtube.com/watch?v=${streamSong.id}",
                                            album = streamSong.album
                                        )
                                        viewModel.toggleStreamLibrary(videoItem)
                                    }
                                )
                                // Can add "Download" option here too!
                                DropdownMenuItem(
                                    text = { Text("Download", color = DTechBlue) },
                                    onClick = {
                                        showMenu = false
                                        val videoItem = com.example.musicdownloader.VideoItem(
                                            id = streamSong.id,
                                            title = streamSong.title,
                                            duration = streamSong.duration,
                                            uploader = streamSong.artist,
                                            thumbnailUrl = streamSong.thumbnailUrl,
                                            webUrl = "https://www.youtube.com/watch?v=${streamSong.id}",
                                            album = streamSong.album
                                        )
                                        viewModel.downloadSong(videoItem)
                                    }
                                )
                            }
                         }
                    }
                }
            }
        }
    }

    if (showAddToPlaylistForSong != null) {
        val song = showAddToPlaylistForSong!!
        AddToPlaylistSheet(
            playlists = playlists,
            songs = listOf(song),
            onDismiss = { showAddToPlaylistForSong = null },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onAddToPlaylist = { playlist, _ ->
                viewModel.addSongToPlaylist(playlist.id.toInt(), song.id)
                scope.launch { snackbarHostState.showSnackbar("Added to ${playlist.name}") }
            }
        )
    }

    if (showEditMetadataForSong != null) {
        val song = showEditMetadataForSong!!
        EditMetadataDialog(
            song = song,
            onDismiss = { showEditMetadataForSong = null },
            onSave = { title, artist, album ->
                viewModel.updateSongMetadata(song, title, artist, album)
                showEditMetadataForSong = null
            }
        )
    }
}

@Composable
fun NavigationCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.align(Alignment.TopStart).size(24.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
