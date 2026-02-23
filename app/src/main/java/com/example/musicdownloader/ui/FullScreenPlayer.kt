package com.example.musicdownloader.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
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
import com.example.musicdownloader.data.VideoItem
import com.example.musicdownloader.ui.AddToPlaylistSheet
import com.example.musicdownloader.utils.HapticUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Theme Colors (Approximate D-Tech)
private val DTechBlue = Color(0xFF2962FF)
private val DeepBlack = Color(0xFF121212)
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.7f)

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
    val smartShuffleEnabled by viewModel.isSmartShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val likedSongs by viewModel.likedSongIds.collectAsState()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val audioSessionId by viewModel.audioSessionId.collectAsState()

    val context = LocalContext.current
    val currentSongId = currentMediaItem?.mediaId
    val isLiked = currentSongId != null && likedSongs.contains(currentSongId)

    // Check if saved to library
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

    // Determine Status Text
    val uri = currentMediaItem?.localConfiguration?.uri
    val statusText = remember(uri) {
        when {
            uri?.scheme == "file" -> "Playing Offline"
            uri?.scheme == "dtech" -> "Streaming • High Quality"
            else -> "Streaming"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        // 1. Background Gradient (Immersive)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.6f),
                            DeepBlack
                        )
                    )
                )
        )

        // 2. Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                    letterSpacing = 2.sp
                )
                IconButton(onClick = { /* More Options? */ }) {
                    // Placeholder for alignment
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Album Art
            Card(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(artworkUri),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Title & Artist
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }

                // Add to Library (Toggle)
                IconButton(onClick = {
                    if (!isSavedToLibrary && currentSongId != null) {
                         // Construct minimal VideoItem for saving
                         // We need the ID. We can assume we have it.
                         // For full metadata, we rely on currentMediaItem
                         val videoItem = VideoItem(
                             id = currentSongId,
                             title = title,
                             uploader = artist,
                             duration = "", // Not critical
                             thumbnailUrl = artworkUri?.toString() ?: "",
                             webUrl = "https://youtube.com/watch?v=$currentSongId"
                         )
                         viewModel.addToLibrary(videoItem)
                    } else {
                        // TODO: Remove from library logic if desired, but user only asked for "Add"
                        // Usually "Add" implies toggle, but removing is destructive.
                        // I'll leave it as "Added" state visual only for now or implement remove if needed.
                        // User said "Toggle for either to show...", that was for filtering list.
                        // Here "Add to library button"
                    }
                }) {
                    Icon(
                        imageVector = if (isSavedToLibrary) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                        contentDescription = "Add to Library",
                        tint = if (isSavedToLibrary) DTechBlue else TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Row: Playlist, Like
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                 IconButton(onClick = { showAddToPlaylistDialog = true }) {
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         Icon(Icons.Default.PlaylistAdd, contentDescription = "Playlist", tint = TextSecondary)
                         // Text("Playlist", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                     }
                 }

                 IconButton(onClick = {
                     if (currentSongId != null) {
                         val videoItem = VideoItem(
                             id = currentSongId,
                             title = title,
                             uploader = artist,
                             duration = "",
                             thumbnailUrl = artworkUri?.toString() ?: "",
                             webUrl = "https://youtube.com/watch?v=$currentSongId"
                         )
                         viewModel.toggleLike(videoItem)
                     }
                 }) {
                      Icon(
                          imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                          contentDescription = "Like",
                          tint = if (isLiked) DTechBlue else TextSecondary
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
                    // Status Text
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = DTechBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle (Combined Smart/Normal logic or separate?)
                // User said: "smart shuffle, normal shuffle"
                // We can toggle between Off -> Normal -> Smart -> Off?
                // Or two buttons? User said "all this buttons... smart shuffle, normal shuffle".
                // I'll put Smart Shuffle on left, Normal on right? Or stack?
                // Let's use standard Shuffle icon for Normal, and AutoAwesome for Smart.

                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleModeEnabled) DTechBlue else TextSecondary
                    )
                }

                IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // Play/Pause
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(TextPrimary)
                        .clickable { viewModel.togglePlayPause() },
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

                IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(48.dp)) {
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

            // Smart Shuffle Button (Standalone as requested)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                 Button(
                     onClick = { viewModel.toggleSmartShuffle() },
                     colors = ButtonDefaults.buttonColors(
                         containerColor = if (smartShuffleEnabled) DTechBlue else Color.Transparent,
                         contentColor = if (smartShuffleEnabled) TextPrimary else TextSecondary
                     ),
                     border = if (!smartShuffleEnabled) androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha=0.3f)) else null,
                     contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                 ) {
                     Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                     Spacer(modifier = Modifier.width(8.dp))
                     Text(if (smartShuffleEnabled) "Smart Shuffle On" else "Smart Shuffle")
                 }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Visualizer or bottom space
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Sheets
    if (showAddToPlaylistDialog) {
        val currentSong = com.example.musicdownloader.data.Song(
            id = currentSongId ?: "",
            title = title,
            artist = artist,
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
                if (currentSongId != null) {
                    val videoItem = VideoItem(
                        id = currentSongId,
                        title = title,
                        uploader = artist,
                        duration = "",
                        thumbnailUrl = artworkUri?.toString() ?: "",
                        webUrl = "https://youtube.com/watch?v=$currentSongId"
                    )
                    viewModel.addSongToPlaylist(playlist.id.toInt(), videoItem)
                }
                showAddToPlaylistDialog = false
            }
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
