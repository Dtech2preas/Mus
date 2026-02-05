package com.example.musicdownloader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
// Explicitly import icons that were failing resolution
import androidx.compose.material.icons.filled.DateRange // Fallback for History (DateRange is core)
import androidx.compose.material.icons.filled.AccountCircle // Fallback for GraphicEq
// Attempt standard imports again, but if they fail we swap.
// CI indicated GraphicEq and Timer (rounded) were unresolved.
// Let's check commonly available icons.
// Actually, let's use standard filled icons which are more reliable across versions if rounded are missing.
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.musicdownloader.SmartDashboardState
import com.example.musicdownloader.data.Song

@Composable
fun SmartLibraryDashboard(
    state: SmartDashboardState,
    onNavigateToLiked: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToArtists: () -> Unit,
    onNavigateToHistory: () -> Unit, // New
    onPlaySongs: (List<Song>) -> Unit // For dynamic playlists
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // --- Row 1: Insights & Liked ---
        Row(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Insights Card (Large)
            InsightCard(
                state = state,
                modifier = Modifier.weight(2f)
            )

            // Liked Songs (Small, vertical verticality)
            NavTile(
                title = "Liked",
                icon = Icons.Default.Favorite,
                color = ElectricPurple,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = onNavigateToLiked
            )
        }

        // --- Row 2: Dynamic Playlists ---
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickPlaylistCard(
                title = "On Repeat",
                subtitle = "Your top tracks",
                songs = state.onRepeat,
                color = Color(0xFF00E5FF),
                modifier = Modifier.weight(1f),
                onClick = { if (state.onRepeat.isNotEmpty()) onPlaySongs(state.onRepeat) }
            )

            QuickPlaylistCard(
                title = "New Arrivals",
                subtitle = "Just added",
                songs = state.newArrivals,
                color = Color(0xFFFFAB40),
                modifier = Modifier.weight(1f),
                onClick = { if (state.newArrivals.isNotEmpty()) onPlaySongs(state.newArrivals) }
            )
        }

        // --- Row 3: Navigation Tiles ---
        Row(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NavTile(
                title = "Playlists",
                icon = Icons.Default.List,
                color = Color(0xFF64B5F6),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToPlaylists
            )
            NavTile(
                title = "Artists",
                icon = Icons.Default.Person,
                color = Color(0xFF81C784),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToArtists
            )
            NavTile(
                title = "History",
                icon = Icons.Default.DateRange, // Fallback as History is missing in core
                color = Color(0xFFE57373),
                modifier = Modifier.weight(1f),
                onClick = onNavigateToHistory
            )
        }
    }
}

@Composable
fun InsightCard(
    state: SmartDashboardState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2C2C3E),
            Color(0xFF3F3F54),
            Color(0xFF2C2C3E)
        ),
        start = androidx.compose.ui.geometry.Offset(offset, offset),
        end = androidx.compose.ui.geometry.Offset(offset + 500f, offset + 500f)
    )

    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            // Background Image (Top Artist)
            if (state.topArtistImage != null) {
                AsyncImage(
                    model = state.topArtistImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().alpha(0.3f),
                    contentScale = ContentScale.Crop
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person, // Fallback to Person/AccountCircle as GraphicEq is likely extended set
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "INSIGHTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 2.sp
                    )
                }

                Column {
                    Text(
                        text = state.topArtistName.ifBlank { "Start Listening" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "Most played artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DateRange, // Fallback to DateRange
                        contentDescription = null,
                        tint = ElectricPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = state.totalTimeListened,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "listened",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun QuickPlaylistCard(
    title: String,
    subtitle: String,
    songs: List<Song>,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26))
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Optional: Show tiny thumbnails of first 3 songs in background or row?
            // For now, clean premium look.

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Icon / Play Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (songs.isNotEmpty()) "${songs.size} Songs" else subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun NavTile(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

// Extension to help with alpha modifiers on AsyncImage
private fun Modifier.alpha(alpha: Float): Modifier = this.then(androidx.compose.ui.Modifier.alpha(alpha))
