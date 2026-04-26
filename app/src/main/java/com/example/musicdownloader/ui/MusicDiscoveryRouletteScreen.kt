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

@Composable
fun MusicDiscoveryRouletteScreen(viewModel: MusicViewModel) {
    val rouletteState by viewModel.rouletteState.collectAsState()

    LaunchedEffect(Unit) {
        if (rouletteState.isEmpty()) {
            viewModel.loadRouletteRecommendations()
        }
    }

    var currentIndex by remember { mutableStateOf(0) }

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
            .background(Color(0xFF121212)),
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
                    SwipeableCard(
                        video = rouletteState[i],
                        isCurrent = isCurrent,
                        onSwipedRight = {
                            viewModel.toggleLike(rouletteState[i])
                            currentIndex++
                        },
                        onSwipedLeft = {
                            currentIndex++
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SwipeableCard(
    video: com.example.musicdownloader.VideoItem,
    isCurrent: Boolean,
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
            .pointerInput(Unit) {
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
