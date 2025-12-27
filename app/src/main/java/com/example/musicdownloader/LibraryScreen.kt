package com.example.musicdownloader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
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
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.ui.MusicRowItem
import com.example.musicdownloader.ui.AddToPlaylistSheet
import com.example.musicdownloader.ui.ElectricPurple
import com.example.musicdownloader.utils.HapticUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    snackbarHostState: SnackbarHostState,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLiked: () -> Unit
) {
    val songs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State for Search Bar interaction
    var localSearchQuery by remember { mutableStateOf("") }

    // Manage adding to playlist
    var showAddToPlaylistForSong by remember { mutableStateOf<Song?>(null) }

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
                title = "Liked Songs",
                icon = Icons.Default.Favorite,
                color = ElectricPurple,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToLiked
            )

            // Playlists Card
            NavigationCard(
                title = "Playlists",
                icon = Icons.Default.List,
                color = Color(0xFF00E5FF), // Cyan Accent
                modifier = Modifier.weight(1f),
                onClick = onNavigateToPlaylists
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("All Songs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))

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
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.StartToEnd) {
                            HapticUtils.performHapticFeedback(context)
                            viewModel.addToQueue(song)
                            scope.launch { snackbarHostState.showSnackbar("Added to Queue") }
                            false // Don't dismiss, just trigger action
                        } else {
                            false
                        }
                    }
                )

                // Fade in Queue Text logic
                val progress = dismissState.progress
                val alpha by animateFloatAsState(targetValue = if (progress > 0.1f) 1f else 0f)

                var showMenu by remember { mutableStateOf(false) }

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF7D5FFF)) // Deep Purple
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                             Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
                                Icon(Icons.Default.Add, contentDescription = "Queue", tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Queue", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    enableDismissFromEndToStart = false
                ) {
                     Box(modifier = Modifier.background(Color(0xFF0F0F13))) {
                         MusicRowItem(
                            title = song.title,
                            artist = song.artist,
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
