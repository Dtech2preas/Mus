package com.example.musicdownloader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.offset

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.musicdownloader.MusicViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext

@Composable
fun MusicDiscoveryRouletteScreen(viewModel: MusicViewModel) {
    val rouletteState by viewModel.rouletteState.collectAsState()
    val currentIndex by viewModel.rouletteIndex.collectAsState()
    val prefetchedCount by viewModel.prefetchedRouletteCount.collectAsState()
    val hasSeenHelp by viewModel.hasSeenRouletteHelp.collectAsState()

    LaunchedEffect(Unit) {
        if (rouletteState.isEmpty()) {
            viewModel.loadRouletteRecommendations()
        }
    }

    // Play the current preview when index changes
    LaunchedEffect(currentIndex, rouletteState) {
        if (currentIndex < rouletteState.size) {
            viewModel.playRoulettePreview(rouletteState[currentIndex])
        }
    }

    // Auto fetch when approaching the end of the list
    LaunchedEffect(currentIndex, rouletteState) {
        if (rouletteState.isNotEmpty() && currentIndex >= rouletteState.size - 2) {
            viewModel.loadRouletteRecommendations()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Status text overlay at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                .zIndex(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "D-TECH DISCOVER",
                    color = Color(0xFF00A6FF), // Neon Blue
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                // Subtract 1 from prefetchedCount as requested, ensure it doesn't go below 0
                val displayCount = (prefetchedCount - 1).coerceAtLeast(0)
                Text(
                    text = "NEXT $displayCount SONGS AVAILABLE LOADING MORE..",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (rouletteState.isEmpty()) {
                CircularProgressIndicator(color = Color(0xFF00A6FF))
            } else if (currentIndex >= rouletteState.size) {
                CircularProgressIndicator(color = Color(0xFF00A6FF)) // Just show loading when fetching new ones automatically
            } else {
                // Render from back to front
                for (i in (rouletteState.size - 1) downTo currentIndex) {
                    if (i <= currentIndex + 2) {
                        val isCurrent = i == currentIndex
                        var isCached by remember(rouletteState[i].id) { mutableStateOf(false) }
                        val context = LocalContext.current
                        LaunchedEffect(rouletteState[i].id) {
                            isCached = com.example.musicdownloader.MusicRepository.isStreamCached(context, rouletteState[i].id)
                            if (!isCached) {
                                // Keep polling every second if it's the current one until it's cached
                                while(!isCached) {
                                    kotlinx.coroutines.delay(1000)
                                    isCached = com.example.musicdownloader.MusicRepository.isStreamCached(context, rouletteState[i].id)
                                }
                            }
                        }

                        SwipeableCard(
                            video = rouletteState[i],
                            isCurrent = isCurrent,
                            isLoadingStream = isCurrent && !isCached,
                            onSwipedRight = {
                                viewModel.toggleLike(rouletteState[i])
                                viewModel.setRouletteIndex(currentIndex + 1)
                            },
                            onSwipedLeft = {
                                viewModel.setRouletteIndex(currentIndex + 1)
                            }
                        )
                    }
                }
            }
        }

        // Help Overlay
        if (!hasSeenHelp) {
            val infiniteTransition = rememberInfiniteTransition(label = "SwipeAnimation")

            val rightOffsetX by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "RightSwipe"
            )

            val leftOffsetX by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "LeftSwipe"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xD9000000), Color(0xF2000000))
                        )
                    )
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color(0xFF1E1E2A), shape = RoundedCornerShape(24.dp))
                        .border(2.dp, Color(0xFF00A6FF).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .padding(32.dp)
                ) {
                    Text(
                        text = "MUSIC DISCOVERY",
                        color = Color(0xFF00A6FF),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Find your new vibe.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    // Swipe Right Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .offset(x = rightOffsetX.dp)
                                .background(Color.Green.copy(alpha = 0.2f), shape = RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(text = "SWIPE RIGHT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(text = "Like & Save to Library", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    // Swipe Left Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .offset(x = leftOffsetX.dp)
                                .background(Color.Red.copy(alpha = 0.2f), shape = RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(text = "SWIPE LEFT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(text = "Skip to next song", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    androidx.compose.material3.Button(
                        onClick = { viewModel.markRouletteHelpSeen() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00A6FF)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GOT IT", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeableCard(
    video: com.example.musicdownloader.VideoItem,
    isCurrent: Boolean,
    isLoadingStream: Boolean = false,
    onSwipedRight: () -> Unit,
    onSwipedLeft: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val rotation = offsetX / 20f

    Card(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(0.7f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer(
                rotationZ = rotation,
                alpha = if (isCurrent) 1f else 0.8f,
                scaleX = if (isCurrent) 1f else 0.95f,
                scaleY = if (isCurrent) 1f else 0.95f
            )
            .border(2.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF00A6FF), Color(0xFFFF007F))), RoundedCornerShape(24.dp))
            .pointerInput(isCurrent) {
                if (isCurrent) {
                    detectDragGestures(
                        onDragEnd = {
                            if (offsetX > 300) {
                                onSwipedRight()
                            } else if (offsetX < -300) {
                                onSwipedLeft()
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 300f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = video.uploader,
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Loading Overlay
            if (isLoadingStream) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00A6FF), modifier = Modifier.size(64.dp))
                }
            }

            // Overlay Icons on Drag
            if (offsetX > 50) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Like",
                    tint = Color.Green,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(100.dp)
                        .graphicsLayer(alpha = (offsetX / 300).coerceIn(0f, 1f))
                )
            } else if (offsetX < -50) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Skip",
                    tint = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(100.dp)
                        .graphicsLayer(alpha = (abs(offsetX) / 300).coerceIn(0f, 1f))
                )
            }
        }
    }
}
