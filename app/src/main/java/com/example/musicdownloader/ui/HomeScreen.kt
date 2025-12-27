package com.example.musicdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.VideoItem
import kotlin.random.Random

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onNavigateToSearch: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val playHistory by viewModel.playHistory.collectAsState()
    val genreFeeds = viewModel.uiState.collectAsState().value.genreFeeds

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        // 1. Header
        item {
            Text(
                text = "Good Morning",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }

        // 2. Jump Back In (Recents)
        if (playHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Jump Back In",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                // 2-Row Grid (Manually constructed with Rows for simplicity in LazyColumn)
                // We take top 6 items
                val recents = playHistory.take(6)
                val chunkedRecents = recents.chunked(2) // Pairs of 2 items per column if we were doing vertical grid
                // But request says "2-row Grid (Vertical Grid with 2 columns, or two Rows)".
                // Let's do a 2-column vertical grid style layout manually

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                   for (i in recents.indices step 2) {
                       Row(
                           modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                           horizontalArrangement = Arrangement.spacedBy(8.dp)
                       ) {
                           val item1 = recents[i]
                           RecentCard(
                               title = item1.title,
                               thumbnailUrl = item1.thumbnailUrl,
                               modifier = Modifier.weight(1f),
                               onClick = {
                                   viewModel.downloadAndPlay(
                                       VideoItem(item1.songId, item1.title, "", item1.artist, item1.thumbnailUrl, "")
                                   )
                               }
                           )

                           if (i + 1 < recents.size) {
                               val item2 = recents[i+1]
                               RecentCard(
                                   title = item2.title,
                                   thumbnailUrl = item2.thumbnailUrl,
                                   modifier = Modifier.weight(1f),
                                   onClick = {
                                       viewModel.downloadAndPlay(
                                           VideoItem(item2.songId, item2.title, "", item2.artist, item2.thumbnailUrl, "")
                                       )
                                   }
                               )
                           } else {
                               Spacer(modifier = Modifier.weight(1f))
                           }
                       }
                   }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 3. Made For You (Genre Feeds)
        if (genreFeeds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(genreFeeds) { feed ->
                Column {
                    Text(
                        text = feed.genreName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onNavigateToSearch(feed.genreName) } // Click title to search more
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(feed.songs) { song ->
                            SongCard(
                                song = song,
                                onClick = { viewModel.downloadAndPlay(song) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun RecentCard(
    title: String,
    thumbnailUrl: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = rememberAsyncImagePainter(thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
fun SongCard(
    song: VideoItem,
    onClick: () -> Unit
) {
    // Random "Cyberpunk" color if we need a placeholder or overlay
    // But we usually have thumbnails. Let's make a nice card.

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Image(
                painter = rememberAsyncImagePainter(song.thumbnailUrl),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.uploader,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
