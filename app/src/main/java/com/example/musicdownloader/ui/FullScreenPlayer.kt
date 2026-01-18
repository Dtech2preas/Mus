package com.example.musicdownloader.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.ui.AddToPlaylistSheet
import com.example.musicdownloader.ui.DeepBlue
import com.example.musicdownloader.ui.ElectricPurple
import com.example.musicdownloader.utils.HapticUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val likedSongs by viewModel.likedSongIds.collectAsState()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val currentSongId = currentMediaItem?.mediaId
    val isLiked = currentSongId != null && likedSongs.contains(currentSongId)

    // Dynamic Background State
    var dominantColor by remember { mutableStateOf(DeepBlue) }

    // Add to Playlist State
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }

    if (currentMediaItem == null) return

    val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri

    // Extract Palette & Update Theme temporarily if needed,
    // but here we use it for the gradient background.
    LaunchedEffect(artworkUri) {
        if (artworkUri != null) {
            withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .allowHardware(false) // Required for Palette
                    .build()
                val result = loader.execute(req)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val p = Palette.from(bitmap).generate()
                    // Try to get a vibrant color, or fallback
                    val colorInt = p.getVibrantColor(
                        p.getDarkVibrantColor(DeepBlue.toArgb())
                    )
                    dominantColor = Color(colorInt)
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent, // Handle BG manually for immersion
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 2.sp
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
                    IconButton(onClick = { showEffectsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Audio Settings",
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
        ) {
            // 1. IMMERSIVE BACKGROUND LAYER
            // Blurred Artwork or Color Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dominantColor) // Fallback base color
            ) {
                 // Overlay a large, blurred version of the artwork
                 Image(
                     painter = rememberAsyncImagePainter(artworkUri),
                     contentDescription = null,
                     modifier = Modifier
                         .fillMaxSize()
                         .alpha(0.3f), // Dim it down
                     contentScale = ContentScale.Crop
                 )

                 // Add a Gradient scrim on top to ensure text readability
                 Box(
                     modifier = Modifier
                         .fillMaxSize()
                         .background(
                             Brush.verticalGradient(
                                 colors = listOf(
                                     Color.Black.copy(alpha = 0.3f),
                                     Color(0xFF0F0F13).copy(alpha = 0.9f) // Fade to nearly black at bottom
                                 )
                             )
                         )
                 )
            }

            // 2. CONTENT LAYER
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.1f))

                // Big Artwork with Shadow and Rounded Corners
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(32.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.DarkGray)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(artworkUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Title, Artist, and Add/Like buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Button (Left)
                    IconButton(onClick = { showAddToPlaylistDialog = true }) {
                         Icon(
                             imageVector = Icons.Default.Add,
                             contentDescription = "Add to Playlist",
                             tint = Color.White,
                             modifier = Modifier.size(28.dp)
                         )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    // Like Button (Right)
                    IconButton(onClick = {
                        HapticUtils.performHapticFeedback(context)
                        currentSongId?.let { viewModel.toggleLike(it) }
                    }) {
                         Icon(
                             imageVector = Icons.Default.ThumbUp,
                             contentDescription = "Like",
                             tint = if (isLiked) ElectricPurple else Color.White,
                             modifier = Modifier.size(28.dp)
                         )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Seek Bar
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { newValue ->
                        val newPos = (newValue * duration.toFloat()).toLong()
                        viewModel.seekTo(newPos)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Shuffle",
                            tint = if (shuffleModeEnabled) ElectricPurple else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous
                    IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play/Pause (Big White Circle)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                HapticUtils.performHapticFeedback(context)
                                viewModel.togglePlayPause()
                            }
                    ) {
                        if (uiState.isLoadingPlayer) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = DeepBlue
                            )
                        } else {
                            if (isPlaying) {
                                // Pause Icon
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp, 24.dp).background(DeepBlue, RoundedCornerShape(2.dp)))
                                    Box(modifier = Modifier.size(8.dp, 24.dp).background(DeepBlue, RoundedCornerShape(2.dp)))
                                }
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
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat
                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh, // Recycle icon as repeat
                            contentDescription = "Repeat",
                            tint = when (repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_ONE,
                                androidx.media3.common.Player.REPEAT_MODE_ALL -> ElectricPurple
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Cyberpunk Visualizer (Footer)
                CyberpunkVisualizer(isPlaying = isPlaying)

                Spacer(modifier = Modifier.weight(0.1f))
            }
        }
    }

    // Add to Playlist Sheet Logic
    if (showAddToPlaylistDialog) {
        val songTitle = currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
        val songId = currentSongId ?: ""
        val currentSong = com.example.musicdownloader.data.Song(
            id = songId,
            title = songTitle,
            artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "",
            thumbnailUrl = artworkUri?.toString() ?: "",
            filePath = "",
            duration = ""
        )

        AddToPlaylistSheet(
            playlists = playlists,
            songs = listOf(currentSong),
            onDismiss = { showAddToPlaylistDialog = false },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onAddToPlaylist = { playlist, _ ->
                viewModel.addSongToPlaylist(playlist.id.toInt(), songId)
                showAddToPlaylistDialog = false
            }
        )
    }

    if (showEffectsSheet) {
        PlayerEffectsSheet(
            viewModel = viewModel,
            onDismiss = { showEffectsSheet = false }
        )
    }
}

@Composable
fun CyberpunkVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(24) {
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
    val color = remember { if (Random.nextBoolean()) ElectricPurple else Color(0xFF00E5FF) }

    Box(
        modifier = Modifier
            .width(4.dp)
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
