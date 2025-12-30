package com.example.musicdownloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.ui.ElectricPurple
import java.util.Calendar

@Composable
fun HomeScreen(viewModel: MusicViewModel, onSongClick: (String) -> Unit) {
    val homeFeedState by viewModel.uiState.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val playHistory by viewModel.playHistory.collectAsStateWithLifecycle()

    // Map of downloaded song IDs for quick lookup
    val downloadedIds = remember(librarySongs) { librarySongs.map { it.id }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(16.dp)
    ) {
        // Banner Ad at Top
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StartAppBannerAd()
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Header
        GreetingHeader()

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f)
        ) {
            // 1. Recently Played Section
            if (playHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently Played",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playHistory) { historyItem ->
                            // Convert History to VideoItem for Card
                            // History item doesn't have album info usually, so we just use artist.
                            // Unless we fetch it or store it. PlayHistory struct: songId, title, artist, thumbnailUrl.
                            val song = VideoItem(
                                id = historyItem.songId,
                                title = historyItem.title,
                                duration = "",
                                uploader = historyItem.artist,
                                thumbnailUrl = historyItem.thumbnailUrl,
                                webUrl = "https://www.youtube.com/watch?v=${historyItem.songId}"
                            )
                            MusicCard(
                                title = song.title,
                                subtitle = song.uploader,
                                thumbnailUrl = song.thumbnailUrl,
                                isDownloaded = downloadedIds.contains(song.id),
                                downloadProgress = downloadProgress[song.id],
                                onClick = {
                                    if (downloadedIds.contains(song.id)) {
                                        viewModel.playLocalSong(song.id, song.title, song.uploader, song.thumbnailUrl)
                                    } else {
                                        viewModel.downloadAndPlay(song)
                                    }
                                },
                                onDownload = { viewModel.downloadAndPlay(song) },
                                modifier = Modifier.width(120.dp).height(160.dp) // Compact size
                            )
                        }
                    }
                }
            }

            if (homeFeedState.isLoading) {
                // Skeleton Loading
                item {
                     Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SkeletonLoader(modifier = Modifier.fillMaxWidth().height(200.dp))
                        SkeletonLoader(modifier = Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            } else if (homeFeedState.errorMessage != null) {
                item {
                    Text(text = "Error: ${homeFeedState.errorMessage}", color = Color.Red)
                }
            } else {
                 val feeds = homeFeedState.genreFeeds

                 feeds.forEachIndexed { index, feed ->
                     if (index == 0) {
                         // "Made for You" - 2 Rows Horizontal
                         item {
                             Column {
                                 Text(
                                     text = "Made for You: ${feed.genreName}",
                                     fontSize = 20.sp,
                                     fontWeight = FontWeight.Bold,
                                     color = Color.White,
                                     modifier = Modifier.padding(bottom = 12.dp)
                                 )

                                 // Horizontal Grid (simulated with Column of 2 items per chunk)
                                 val chunks = feed.songs.chunked(2)
                                 LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                     items(chunks) { chunk ->
                                         Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                             chunk.forEach { song ->
                                                 val subtitle = if (song.album != null && song.album != "Unknown Album") "${song.uploader} • ${song.album}" else song.uploader
                                                 MusicCard(
                                                     title = song.title,
                                                     subtitle = subtitle,
                                                     thumbnailUrl = song.thumbnailUrl,
                                                     isDownloaded = downloadedIds.contains(song.id),
                                                     downloadProgress = downloadProgress[song.id],
                                                     onClick = {
                                                         if (downloadedIds.contains(song.id)) {
                                                             viewModel.playLocalSong(song.id, song.title, song.uploader, song.thumbnailUrl)
                                                         } else {
                                                             viewModel.downloadAndPlay(song)
                                                         }
                                                     },
                                                     onDownload = { viewModel.downloadAndPlay(song) }
                                                 )
                                             }
                                         }
                                     }
                                 }
                             }
                         }
                     } else {
                         // "Trending" - Vertical Grid (2 Columns)
                         item {
                             Text(
                                 text = "Trending in ${feed.genreName}",
                                 fontSize = 20.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = Color.White,
                                 modifier = Modifier.padding(bottom = 8.dp)
                             )
                         }

                         val chunks = feed.songs.chunked(2)
                         items(chunks) { chunk ->
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.spacedBy(12.dp)
                             ) {
                                 chunk.forEach { song ->
                                     Box(modifier = Modifier.weight(1f)) {
                                         val subtitle = if (song.album != null && song.album != "Unknown Album") "${song.uploader} • ${song.album}" else song.uploader
                                         MusicCard(
                                             title = song.title,
                                             subtitle = subtitle,
                                             thumbnailUrl = song.thumbnailUrl,
                                             isDownloaded = downloadedIds.contains(song.id),
                                             downloadProgress = downloadProgress[song.id],
                                             onClick = {
                                                 if (downloadedIds.contains(song.id)) {
                                                     viewModel.playLocalSong(song.id, song.title, song.uploader, song.thumbnailUrl)
                                                 } else {
                                                     viewModel.downloadAndPlay(song)
                                                 }
                                             },
                                             onDownload = { viewModel.downloadAndPlay(song) },
                                             modifier = Modifier.fillMaxWidth().height(220.dp)
                                         )
                                     }
                                 }
                                 // Fill empty space if odd number
                                 if (chunk.size < 2) {
                                     Spacer(modifier = Modifier.weight(1f))
                                 }
                             }
                         }
                     }
                 }
            }
        }
    }
}

@Composable
fun GreetingHeader() {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Morning"
        in 12..17 -> "Afternoon"
        else -> "Evening"
    }

    // Animated Gradient
    val infiniteTransition = rememberInfiniteTransition(label = "header_gradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFF7D5FFF), Color(0xFF00E5FF), Color(0xFF7D5FFF)),
        start = Offset(offset, 0f),
        end = Offset(offset + 500f, 100f),
        tileMode = TileMode.Mirror
    )

    Text(
        text = "$greeting from DTECH",
        style = TextStyle(
            brush = brush,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    )
}
