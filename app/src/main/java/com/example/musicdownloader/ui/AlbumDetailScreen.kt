package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicdownloader.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val allSongs by viewModel.librarySongs.collectAsStateWithLifecycle()

    // Filter for this album
    val albumSongs = remember(allSongs, albumName) {
        allSongs.filter { it.album == albumName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(albumName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${albumSongs.size} Songs", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Play All Button
                     IconButton(onClick = {
                         if (albumSongs.isNotEmpty()) {
                              val first = albumSongs.first()
                              // Play the first song, passing the full album list as the context queue
                              viewModel.playSong(first.id, first.title, first.artist, first.thumbnailUrl, albumSongs)
                         }
                     }) {
                         Icon(Icons.Default.PlayArrow, contentDescription = "Play Album", tint = ElectricPurple)
                     }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F13))
            )
        },
        containerColor = Color(0xFF0F0F13)
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(albumSongs) { song ->
                // Since this is Album Detail, redundant to show Album name in subtitle?
                // But requested format is "Artist • Album".
                // We can just show "Artist" if we are in Album context to save space, but consistency is key.
                // Let's stick to the rule: "Line 2: Display 'Artist • Album'".
                val subtitle = if (song.album != "Unknown Album") "${song.artist} • ${song.album}" else song.artist
                MusicRowItem(
                    title = song.title,
                    subtitle = subtitle,
                    thumbnailUrl = song.thumbnailUrl,
                    isLibrary = true,
                    onClick = {
                         // Play this song, within the context of the Album
                         viewModel.playSong(song.id, song.title, song.artist, song.thumbnailUrl, albumSongs)
                    }
                )
            }
        }
    }
}
