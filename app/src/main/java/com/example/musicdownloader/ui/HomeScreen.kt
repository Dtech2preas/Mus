package com.example.musicdownloader.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.ui.ElectricPurple

@Composable
fun HomeScreen(viewModel: MusicViewModel, onSongClick: (String) -> Unit) {
    val homeFeedState by viewModel.uiState.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    // Map of downloaded song IDs for quick lookup
    val downloadedIds = remember(librarySongs) { librarySongs.map { it.id }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13))
            .padding(16.dp)
    ) {
        // Gradient Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF20202C), Color.Transparent)
                    )
                )
        ) {
            Text(
                text = "Home",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (homeFeedState.isLoading) {
            // Skeleton Loading
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonLoader(modifier = Modifier.fillMaxWidth().height(200.dp))
                SkeletonLoader(modifier = Modifier.fillMaxWidth().height(100.dp))
                SkeletonLoader(modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        } else if (homeFeedState.errorMessage != null) {
            Text(text = "Error: ${homeFeedState.errorMessage}", color = Color.Red)
        } else {
             LazyColumn(
                 verticalArrangement = Arrangement.spacedBy(24.dp),
                 modifier = Modifier.weight(1f)
             ) {
                 val feeds = homeFeedState.genreFeeds

                 // Strategy: First genre -> Horizontal. Rest -> Vertical.
                 feeds.forEachIndexed { index, feed ->
                     if (index == 0) {
                         // Horizontal Section
                         item {
                             Column {
                                 Text(
                                     text = "Made for You: ${feed.genreName}",
                                     fontSize = 20.sp,
                                     fontWeight = FontWeight.Bold,
                                     color = Color.White,
                                     modifier = Modifier.padding(bottom = 12.dp)
                                 )
                                 LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                     items(feed.songs) { song ->
                                         MusicCard(
                                             song = song,
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
                     } else {
                         // Vertical Section
                         item {
                             Text(
                                 text = "Trending in ${feed.genreName}",
                                 fontSize = 20.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = Color.White,
                                 modifier = Modifier.padding(bottom = 8.dp)
                             )
                         }
                         items(feed.songs) { song ->
                             MusicRowItem(
                                 title = song.title,
                                 artist = song.uploader,
                                 thumbnailUrl = song.thumbnailUrl,
                                 isLibrary = false,
                                 isDownloaded = downloadedIds.contains(song.id),
                                 downloadProgress = downloadProgress[song.id],
                                 onClick = {
                                     if (downloadedIds.contains(song.id)) {
                                         viewModel.playLocalSong(song.id, song.title, song.uploader, song.thumbnailUrl)
                                     } else {
                                         viewModel.downloadAndPlay(song)
                                     }
                                 },
                                 onDownloadClick = { viewModel.downloadAndPlay(song) }
                             )
                         }
                     }
                 }
             }
        }
    }
}

// Helper Card for Horizontal List
@Composable
fun MusicCard(
    song: VideoItem,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(220.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26)),
        onClick = onClick
    ) {
        Column {
            Box(modifier = Modifier.height(140.dp).fillMaxWidth()) {
                Image(
                    painter = rememberAsyncImagePainter(song.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Smart Overlays
                if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             CircularProgressIndicator(
                                 progress = { downloadProgress / 100f },
                                 modifier = Modifier.size(32.dp),
                                 color = ElectricPurple,
                                 trackColor = Color.White.copy(alpha = 0.3f),
                             )
                             Spacer(modifier = Modifier.height(4.dp))
                             Text("${downloadProgress.toInt()}%", color = Color.White, fontSize = 12.sp)
                         }
                    }
                } else if (isDownloaded) {
                    // Show Play Icon Bottom Right
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // Show Download Icon Bottom Right (Clickable separately or just indicate state)
                    // Requirement says: "No: Show Download button."
                     Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                // If card click downloads, this is just visual, but let's make it clickable too?
                                // Card click already handles action based on state.
                                // If not downloaded -> Download.
                                // So icon is just visual cue.
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Download",
                                tint = ElectricPurple,
                                modifier = Modifier.size(24.dp).align(Alignment.Center)
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 14.sp)
                Text(song.uploader, color = Color.Gray, maxLines = 1, fontSize = 12.sp)
            }
        }
    }
}
