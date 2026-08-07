package com.example.musicdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.FirebaseManager
import com.example.musicdownloader.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerLibraryScreen(onBack: () -> Unit, onPlaySong: (VideoItem) -> Unit) {
    var libraryData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var currentSubScreen by remember { mutableStateOf<PartnerSubScreen>(PartnerSubScreen.Main) }

    LaunchedEffect(Unit) {
        FirebaseManager.getPartnerLibrary { data ->
            libraryData = data
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentSubScreen) {
                            PartnerSubScreen.Main -> "Partner's Library"
                            PartnerSubScreen.LikedSongs -> "Partner's Liked Songs"
                            is PartnerSubScreen.PlaylistDetail -> (currentSubScreen as PartnerSubScreen.PlaylistDetail).name
                            PartnerSubScreen.AllSongs -> "Partner's Songs"
                            PartnerSubScreen.Artists -> "Partner's Artists"
                            PartnerSubScreen.DnaStats -> "Partner's DNA Stats"
                        },
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSubScreen == PartnerSubScreen.Main) onBack()
                        else currentSubScreen = PartnerSubScreen.Main
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlue)
            )
        },
        containerColor = DeepBlue
    ) { padding ->
        if (libraryData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PremiumGold)
            }
        } else {
            when (val screen = currentSubScreen) {
                PartnerSubScreen.Main -> {
                    val playlists = libraryData!!["playlists"] as? List<Map<String, Any>> ?: emptyList()
                    val likedSongs = libraryData!!["likedSongs"] as? List<Map<String, Any>> ?: emptyList()
                    val allSongs = libraryData!!["allSongs"] as? List<Map<String, Any>> ?: emptyList()

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        item {
                            LibrarySectionItem(
                                icon = Icons.Default.Favorite,
                                iconColor = Color.Red,
                                title = "Liked Songs",
                                subtitle = "${likedSongs.size} songs",
                                onClick = { currentSubScreen = PartnerSubScreen.LikedSongs }
                            )
                        }

                        item {
                            LibrarySectionItem(
                                icon = Icons.Default.MusicNote,
                                iconColor = PremiumGold,
                                title = "All Songs",
                                subtitle = "${allSongs.size} songs",
                                onClick = { currentSubScreen = PartnerSubScreen.AllSongs }
                            )
                        }

                        item {
                            LibrarySectionItem(
                                icon = Icons.Default.Person,
                                iconColor = Color.Cyan,
                                title = "Artists",
                                subtitle = "View partner's artists",
                                onClick = { currentSubScreen = PartnerSubScreen.Artists }
                            )
                        }

                        item {
                            LibrarySectionItem(
                                icon = Icons.Default.Face,
                                iconColor = Color.Magenta,
                                title = "Music DNA",
                                subtitle = "View partner's stats",
                                onClick = { currentSubScreen = PartnerSubScreen.DnaStats }
                            )
                        }

                        item {
                            Text("Playlists", color = PremiumGold, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
                        }

                        items(playlists) { pl ->
                            val name = pl["name"] as? String ?: "Unnamed Playlist"
                            val id = (pl["id"] as? Number)?.toInt() ?: 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentSubScreen = PartnerSubScreen.PlaylistDetail(id, name) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(name, color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }
                PartnerSubScreen.LikedSongs -> {
                    val likedSongs = libraryData!!["likedSongs"] as? List<Map<String, Any>> ?: emptyList()
                    PartnerSongList(likedSongs, padding, onPlaySong)
                }
                is PartnerSubScreen.PlaylistDetail -> {
                    var playlistSongs by remember { mutableStateOf<List<Map<String, Any>>?>(null) }
                    LaunchedEffect(screen.id) {
                        FirebaseManager.getPartnerPlaylistSongs(screen.id) { songs ->
                            playlistSongs = songs
                        }
                    }
                    if (playlistSongs == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PremiumGold)
                        }
                    } else {
                        PartnerSongList(playlistSongs!!, padding, onPlaySong)
                    }
                }
                PartnerSubScreen.AllSongs -> {
                    val allSongs = libraryData!!["allSongs"] as? List<Map<String, Any>> ?: emptyList()
                    PartnerSongList(allSongs, padding, onPlaySong)
                }
                PartnerSubScreen.Artists -> {
                    val allSongs = libraryData!!["allSongs"] as? List<Map<String, Any>> ?: emptyList()
                    val artists = allSongs.mapNotNull { it["artist"] as? String }.distinct().sorted()
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                        items(artists) { artist ->
                            Text(
                                artist,
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                fontSize = 18.sp
                            )
                        }
                    }
                }
                PartnerSubScreen.DnaStats -> {
                    Box(modifier = Modifier.padding(padding)) {
                        PartnerDnaStatsScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun LibrarySectionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.Gray)
        }
    }
}

@Composable
fun PartnerSongList(songs: List<Map<String, Any>>, padding: PaddingValues, onPlaySong: (VideoItem) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        items(songs) { song ->
            val title = song["title"] as? String ?: "Unknown"
            val artist = song["artist"] as? String ?: "Unknown"
            val thumbnailUrl = song["thumbnailUrl"] as? String ?: ""
            val id = song["id"] as? String ?: song["videoId"] as? String ?: ""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (id.isNotEmpty()) {
                            onPlaySong(VideoItem(id, title, "", artist, thumbnailUrl, "https://youtube.com/watch?v=$id"))
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberAsyncImagePainter(song["thumbnailUrl"] as? String),
                    contentDescription = null,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(artist, color = Color.Gray, fontSize = 14.sp)
                }
                IconButton(onClick = {
                    if (id.isNotEmpty()) {
                        onPlaySong(VideoItem(id, title, "", artist, thumbnailUrl, "https://youtube.com/watch?v=$id"))
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = PremiumGold)
                }
            }
        }
    }
}

sealed class PartnerSubScreen {
    object Main : PartnerSubScreen()
    object LikedSongs : PartnerSubScreen()
    data class PlaylistDetail(val id: Int, val name: String) : PartnerSubScreen()
    object AllSongs : PartnerSubScreen()
    object Artists : PartnerSubScreen()
    object DnaStats : PartnerSubScreen()
}


@Composable
fun PartnerDnaStatsScreen() {
    val partnerStats by FirebaseManager.partnerDnaStats.collectAsState()

    if (partnerStats != null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            DnaDashboard(stats = partnerStats!!)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("DNA Stats not available for partner yet.", color = Color.Gray)
        }
    }
}
