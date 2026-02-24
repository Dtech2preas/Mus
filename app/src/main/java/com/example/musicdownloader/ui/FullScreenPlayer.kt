package com.example.musicdownloader.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.musicdownloader.VideoItem
import com.example.musicdownloader.ui.AddToPlaylistSheet
import com.example.musicdownloader.utils.HapticUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Theme Colors (Approximate D-Tech)
private val DeepBlack = Color(0xFF121212)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.7f)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val smartShuffleEnabled by viewModel.isSmartShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val likedSongs by viewModel.likedSongIds.collectAsState()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val currentSongId = currentMediaItem?.mediaId
    val isLiked = currentSongId != null && likedSongs.contains(currentSongId)

    // Check if saved to library (to show download status)
    val isSavedToLibrary by remember(currentSongId) {
        viewModel.isSavedToLibrary(currentSongId ?: "")
    }.collectAsState(initial = false)

    // Dynamic Background Color
    var dominantColor by remember { mutableStateOf(DTechBlue) }
    val animatedColor by animateColorAsState(targetValue = dominantColor, animationSpec = tween(1000), label = "color")

    // Add to Playlist Sheet
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    if (currentMediaItem == null) return

    val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri
    val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title"
    val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"

    // Extract Palette
    LaunchedEffect(artworkUri) {
        if (artworkUri != null) {
            withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val p = Palette.from(bitmap).generate()
                    val colorInt = p.getVibrantColor(
                        p.getDarkVibrantColor(DTechBlue.toArgb())
                    )
                    dominantColor = Color(colorInt)
                }
            }
        }
    }

    // Breathing Animation for Album Art
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f, // Very subtle breathing
        animationSpec = infiniteRepeatable(
            animation = tween(4000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Smart Shuffle Sparkle Animation
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle"
    )

    // Helper for VideoItem
    val videoItem = remember(currentSongId, title, artist, artworkUri) {
        if (currentSongId != null) {
            VideoItem(
                id = currentSongId,
                title = title,
                duration = formatTime(duration),
                uploader = artist,
                thumbnailUrl = artworkUri?.toString() ?: "",
                webUrl = "https://youtube.com/watch?v=$currentSongId"
            )
        } else null
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        // 1. Background (Blurred & Dimmed)
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = rememberAsyncImagePainter(artworkUri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.6f)
                    .blur(50.dp),
                contentScale = ContentScale.Crop
            )
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                DeepBlack.copy(alpha = 0.8f),
                                DeepBlack
                            )
                        )
                    )
            )
        }

        // 2. Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = TextPrimary)
                }
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { /* More Options */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Album Art
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = if (isPlaying) scale else 1f
                        scaleY = if (isPlaying) scale else 1f
                    },
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(artworkUri),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Info & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Text Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            // Action Row (Playlist, Like, Download)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 IconButton(onClick = { showAddToPlaylistDialog = true }) {
                     Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to Playlist", tint = TextPrimary)
                 }

                 IconButton(onClick = {
                     videoItem?.let { viewModel.toggleLike(it) }
                 }) {
                      Icon(
                          imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                          contentDescription = "Like",
                          tint = if (isLiked) DTechBlue else TextPrimary
                      )
                 }

                 IconButton(onClick = {
                     videoItem?.let { viewModel.downloadSong(it) }
                 }) {
                     // Check if local file exists to show "Done"
                     val isDownloaded = currentMediaItem?.localConfiguration?.uri?.scheme == "file"
                     Icon(
                         imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                         contentDescription = "Download",
                         tint = if (isDownloaded) DTechBlue else TextPrimary
                     )
                 }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Smart Shuffle Pill
            Surface(
                onClick = { viewModel.toggleSmartShuffle() },
                shape = CircleShape,
                color = if (smartShuffleEnabled) DTechBlue else Color.Transparent,
                border = if (!smartShuffleEnabled) androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha=0.3f)) else null,
                modifier = Modifier.height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                     Icon(
                         Icons.Default.AutoAwesome,
                         contentDescription = null,
                         modifier = Modifier
                             .size(16.dp)
                             .graphicsLayer {
                                 if (smartShuffleEnabled) {
                                     alpha = sparkleAlpha
                                     scaleX = 1.1f
                                     scaleY = 1.1f
                                 }
                             },
                         tint = if (smartShuffleEnabled) TextPrimary else TextSecondary
                     )
                     Spacer(modifier = Modifier.width(8.dp))
                     Text(
                         text = "Smart Shuffle",
                         style = MaterialTheme.typography.labelMedium,
                         color = if (smartShuffleEnabled) TextPrimary else TextSecondary
                     )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Section
            Column(modifier = Modifier.fillMaxWidth()) {
                var sliderPosition by remember { mutableFloatStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }

                LaunchedEffect(currentPosition, duration) {
                    if (!isDragging && duration > 0) {
                        sliderPosition = currentPosition.toFloat() / duration.toFloat()
                    }
                }

                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo((sliderPosition * duration).toLong())
                        isDragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.height(20.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle (Normal)
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleModeEnabled) DTechBlue else TextSecondary
                    )
                }

                // Previous
                ScaleIconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // Play/Pause
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val playButtonScale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "scale")

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(playButtonScale)
                        .shadow(elevation = 10.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(TextPrimary)
                        .clickable(interactionSource = interactionSource, indication = null) { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoadingPlayer) {
                        CircularProgressIndicator(color = DeepBlack, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = DeepBlack,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Next
                ScaleIconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // Repeat
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    Icon(
                        imageVector = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) DTechBlue else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Sheets
    if (showAddToPlaylistDialog && videoItem != null) {
        val currentSong = com.example.musicdownloader.data.Song(
            id = videoItem.id,
            title = videoItem.title,
            artist = videoItem.uploader,
            thumbnailUrl = videoItem.thumbnailUrl,
            filePath = "",
            duration = videoItem.duration
        )
        AddToPlaylistSheet(
            playlists = playlists,
            songs = listOf(currentSong),
            onDismiss = { showAddToPlaylistDialog = false },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onAddToPlaylist = { playlist, _ ->
                viewModel.addSongToPlaylist(playlist.id.toInt(), videoItem)
                showAddToPlaylistDialog = false
            }
        )
    }
}

@Composable
fun ScaleIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.8f else 1f, label = "scale")

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
