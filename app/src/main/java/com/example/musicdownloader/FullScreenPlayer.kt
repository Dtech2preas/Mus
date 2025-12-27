package com.example.musicdownloader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.media3.common.Player
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.ui.DeepBlue
import com.example.musicdownloader.ui.ElectricPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

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
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    if (currentMediaItem == null) return

    Scaffold(
        containerColor = DeepBlue,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PLAYING FROM LIBRARY",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = androidx.compose.ui.unit.sp(2)
                        )
                        Text(
                            "Liked Songs", // Placeholder or Dynamic Playlist Name
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                     IconButton(onClick = { /* Menu */ }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_more),
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
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
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Title & Artist Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { /* Like Logic */ }) {
                         Icon(
                             imageVector = androidx.compose.material.icons.Icons.Default.ThumbUp,
                             contentDescription = "Like",
                             tint = Color.White,
                             modifier = Modifier.size(32.dp)
                         )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Shuffle",
                            tint = if (shuffleModeEnabled) ElectricPurple else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Previous
                    IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause (Big White Circle)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { viewModel.togglePlayPause() }
                    ) {
                        if (uiState.isLoadingPlayer) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = DeepBlue
                            )
                        } else {
                            if (isPlaying) {
                                Text("⏸", style = MaterialTheme.typography.headlineLarge, color = DeepBlue, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = DeepBlue,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    // Next
                    IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Repeat
                    IconButton(onClick = { viewModel.toggleRepeat() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Repeat",
                            tint = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> ElectricPurple
                                Player.REPEAT_MODE_ALL -> ElectricPurple
                                else -> Color.White // or Gray
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Cyberpunk Visualizer (Footer)
                CyberpunkVisualizer(isPlaying = isPlaying)

                Spacer(modifier = Modifier.weight(0.1f))
            }
        }
    }
}

@Composable
fun CyberpunkVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp), // Slightly shorter for footer look
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(24) { // More bars
            VisualizerBar(isPlaying = isPlaying)
        }
    }
}

@Composable
fun VisualizerBar(isPlaying: Boolean) {
    var targetHeight by remember { mutableStateOf(0.1f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                targetHeight = Random.nextFloat().coerceIn(0.1f, 1f)
                delay(80 + Random.nextLong(0, 100))
            }
        } else {
            targetHeight = 0.1f
        }
    }

    val animatedHeight by animateFloatAsState(targetValue = targetHeight, label = "barHeight")
    // Electric Purple or Cyan
    val color = remember { if (Random.nextBoolean()) ElectricPurple else Color(0xFF00E5FF) }

    Box(
        modifier = Modifier
            .width(4.dp) // Thinner bars
            .fillMaxHeight(animatedHeight)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.8f))
    )
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
