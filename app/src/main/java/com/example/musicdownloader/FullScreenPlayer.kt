package com.example.musicdownloader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.ui.DeepBlue
import com.example.musicdownloader.ui.ElectricPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayer(
    viewModel: MusicViewModel,
    onCollapse: () -> Unit
) {
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    if (currentMediaItem == null) return

    Scaffold(
        containerColor = DeepBlue,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DeepBlue,
                            Color(0xFF2A2A35) // Slightly lighter at bottom
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.1f))

                // Big Artwork with Shadow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(24.dp, shape = RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(currentMediaItem?.mediaMetadata?.artworkUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Simulated Blur / Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                    startY = 0.0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Title & Artist
                Text(
                    text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(0.1f))

                // Seek Bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { newValue ->
                        val newPos = (newValue * duration).toLong()
                        viewModel.seekTo(newPos)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricPurple,
                        activeTrackColor = ElectricPurple,
                        inactiveTrackColor = Color.DarkGray
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Text(formatTime(duration), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Previous not implemented */ }, modifier = Modifier.size(48.dp)) {
                        Text("⏮", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }

                    // Play/Pause (Bigger & Purple)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ElectricPurple)
                            .clickable { viewModel.togglePlayPause() }
                    ) {
                        if (uiState.isLoadingPlayer) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = Color.White
                            )
                        } else {
                            if (isPlaying) {
                                Text("⏸", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    IconButton(onClick = { /* Next not implemented */ }, modifier = Modifier.size(48.dp)) {
                        Text("⏭", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.weight(0.2f))
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
