package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onSongClick: (String) -> Unit
) {
    val likedIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val allSongs by viewModel.librarySongs.collectAsStateWithLifecycle()

    // Filter locally for now
    val likedSongs = remember(likedIds, allSongs) {
        allSongs.filter { likedIds.contains(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liked Songs", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                   if (likedSongs.isNotEmpty()) {
                       viewModel.playSong(
                           id = likedSongs.first().id,
                           title = likedSongs.first().title,
                           artist = likedSongs.first().artist,
                           thumbnailUrl = likedSongs.first().thumbnailUrl,
                           contextQueue = likedSongs
                       )
                   }
                },
                containerColor = com.example.musicdownloader.ui.ElectricPurple,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Refresh, "Shuffle") },
                text = { Text("Shuffle All") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(padding)
        ) {
             if (likedSongs.isEmpty()) {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                     Text("No liked songs yet", color = Color.Gray)
                 }
             } else {
                 LazyColumn {
                     items(likedSongs) { song ->
                         MusicRowItem(
                             title = song.title,
                             artist = song.artist,
                             thumbnailUrl = song.thumbnailUrl,
                             isLibrary = true,
                             onClick = {
                                 viewModel.playSong(
                                     id = song.id,
                                     title = song.title,
                                     artist = song.artist,
                                     thumbnailUrl = song.thumbnailUrl,
                                     contextQueue = likedSongs
                                 )
                             }
                         )
                     }
                 }
             }
        }
    }
}
