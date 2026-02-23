package com.example.musicdownloader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.data.Song
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
    var selectedTab by remember { mutableIntStateOf(0) }

    // Manage adding to playlist
    var showAddToPlaylistForSong by remember { mutableStateOf<Song?>(null) }
    var showAddToPlaylistForStreamSong by remember { mutableStateOf<com.example.musicdownloader.data.StreamSong?>(null) }

    // Manage Metadata Editing
    var showEditMetadataForSong by remember { mutableStateOf<Song?>(null) }

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

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ElectricPurple
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Downloads") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Stream Library") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // DOWNLOADS TAB
            val filteredSongs = remember(songs, localSearchQuery) {
                if (localSearchQuery.isBlank()) songs else songs.filter {
                    it.title.contains(localSearchQuery, ignoreCase = true) ||
                    it.artist.contains(localSearchQuery, ignoreCase = true)
                }
            }

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
        } else {
            // STREAM LIBRARY TAB
            val filteredStreamSongs = remember(streamSongs, localSearchQuery) {
                if (localSearchQuery.isBlank()) streamSongs else streamSongs.filter {
                    it.title.contains(localSearchQuery, ignoreCase = true) ||
                    it.artist.contains(localSearchQuery, ignoreCase = true)
                }
            }

            LazyColumn(
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f)
            ) {
                items(items = filteredStreamSongs, key = { it.id }) { streamSong ->
                    val isDownloaded = remember(songs, streamSong.id) {
                        songs.any { it.id == streamSong.id }
                    }
                    var showMenu by remember { mutableStateOf(false) }

                    // No Swipe to Queue for streams yet (needs resolution logic)
                    Box(modifier = Modifier.background(Color(0xFF0F0F13))) {
                        val subtitle = if (streamSong.album != "Unknown Album") "${streamSong.artist} • ${streamSong.album}" else streamSong.artist
                        val finalSubtitle = if (isDownloaded) "$subtitle • Downloaded" else subtitle

                        MusicRowItem(
                            title = streamSong.title,
                            subtitle = finalSubtitle,
                            thumbnailUrl = streamSong.thumbnailUrl,
                            isLibrary = true, // Shows standard layout
                            onClick = {
                                // Play Stream (ViewModel handles falling back to local if exists)
                                val video = VideoItem(
                                    id = streamSong.id,
                                    title = streamSong.title,
                                    uploader = streamSong.artist,
                                    thumbnailUrl = streamSong.thumbnailUrl,
                                    duration = streamSong.duration,
                                    webUrl = "https://www.youtube.com/watch?v=${streamSong.id}"
                                )
                                viewModel.playStream(video)
                            },
                            onOptionClick = { showMenu = true }
                        )

                        // Visual Indicator for Downloaded
                        if (isDownloaded) {
                             Icon(
                                 imageVector = Icons.Default.CheckCircle,
                                 contentDescription = "Downloaded",
                                 tint = DTechBlue,
                                 modifier = Modifier
                                     .align(Alignment.CenterEnd)
                                     .padding(end = 48.dp) // Left of option menu
                                     .size(16.dp)
                             )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1C1C26))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist", color = Color.White) },
                                onClick = {
                                    showMenu = false
                                    showAddToPlaylistForStreamSong = streamSong
                                }
                            )
                            if (!isDownloaded) {
                                DropdownMenuItem(
                                    text = { Text("Download", color = DTechBlue) },
                                    onClick = {
                                        showMenu = false
                                        val video = VideoItem(
                                            id = streamSong.id,
                                            title = streamSong.title,
                                            uploader = streamSong.artist,
                                            thumbnailUrl = streamSong.thumbnailUrl,
                                            duration = streamSong.duration,
                                            webUrl = "https://www.youtube.com/watch?v=${streamSong.id}"
                                        )
                                        viewModel.downloadSong(video)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Remove from Library", color = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    val video = VideoItem(
                                        id = streamSong.id,
                                        title = streamSong.title,
                                        uploader = streamSong.artist,
                                        thumbnailUrl = streamSong.thumbnailUrl,
                                        duration = streamSong.duration,
                                        webUrl = "https://www.youtube.com/watch?v=${streamSong.id}"
                                    )
                                    viewModel.toggleLike(video) // Toggles off
                                }
                            )
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

    if (showAddToPlaylistForStreamSong != null) {
        val sSong = showAddToPlaylistForStreamSong!!
        // Convert to Song for Playlist (ID is what matters)
        val song = Song(
            id = sSong.id,
            title = sSong.title,
            artist = sSong.artist,
            thumbnailUrl = sSong.thumbnailUrl,
            filePath = "",
            duration = sSong.duration,
            album = sSong.album
        )
        AddToPlaylistSheet(
            playlists = playlists,
            songs = listOf(song),
            onDismiss = { showAddToPlaylistForStreamSong = null },
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
