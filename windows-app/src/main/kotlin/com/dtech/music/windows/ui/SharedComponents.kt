package com.dtech.music.windows.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dtech.music.windows.VideoItem
import java.util.Calendar

val DeepBlue = Color(0xFF050510) // Midnight Black
val SurfaceBlue = Color(0xFF12121A) // Dark Glass
val DTechBlue = Color(0xFF2962FF) // Royal Blue
val PremiumGold = DTechBlue // Replaced Gold with Blue as requested
val ElectricPurple = DTechBlue // Alias for backward compatibility, but now Blue
val CyanAccent = PremiumGold // Update accent to Gold
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFFB0B0B0)

@Composable
fun TechBackground() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val primaryColor = Color(0xFF2962FF) // AccentBlue

        // Draw grid lines
        val lineCount = 10
        val stepX = width / lineCount
        val stepY = height / lineCount

        for (i in 0..lineCount) {
             drawLine(
                 color = primaryColor.copy(alpha = 0.05f),
                 start = Offset(i * stepX, 0f),
                 end = Offset(i * stepX, height),
                 strokeWidth = 1.dp.toPx()
             )
             drawLine(
                 color = primaryColor.copy(alpha = 0.05f),
                 start = Offset(0f, i * stepY),
                 end = Offset(width, i * stepY),
                 strokeWidth = 1.dp.toPx()
             )
        }

        // Draw glowing circles
        drawCircle(
             color = primaryColor.copy(alpha = 0.08f),
             radius = 150.dp.toPx(),
             center = Offset(width * 0.85f, height * 0.15f)
        )
         drawCircle(
             color = primaryColor.copy(alpha = 0.08f),
             radius = 120.dp.toPx(),
             center = Offset(width * 0.15f, height * 0.85f)
        )
    }
}

@Composable
fun GreetingHeader() {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Morning"
        in 12..17 -> "Afternoon"
        else -> "Evening"
    }

    // Animated Gradient
    val infiniteTransition = rememberInfiniteTransition(label = "header_gradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    // Blue and Black Gradient as requested
    val brush = Brush.linearGradient(
        colors = listOf(DTechBlue, Color.Black, DTechBlue),
        start = Offset(offset, 0f),
        end = Offset(offset + 500f, 100f),
        tileMode = TileMode.Mirror
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        // D-Tech Logo
        Image(
            painter = painterResource("drawable/dtech_logo.jpg"),
            contentDescription = "DTECH Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "$greeting from DTECH",
            style = TextStyle(
                brush = brush,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun MusicRowItem(
    title: String,
    subtitle: String, // Changed from artist to subtitle to support "Artist • Album"
    thumbnailUrl: String,
    isLibrary: Boolean = true,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    isWaiting: Boolean = false,
    isCached: Boolean = false,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onOptionClick: () -> Unit = {}
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceBlue)
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isCached && !isDownloaded && !isWaiting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(16.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Instant Play",
                            tint = PremiumGold,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                if (isWaiting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = PremiumGold,
                            strokeWidth = 2.dp
                        )
                    }
                } else if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = downloadProgress / 100f,
                            modifier = Modifier.size(24.dp),
                            color = PremiumGold,
                            backgroundColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Bold, // Bold as requested
                    maxLines = 1, // Max 1 line as requested
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isWaiting) {
                    Text(
                        text = "Preparing...",
                        style = MaterialTheme.typography.overline,
                        color = PremiumGold
                    )
                } else if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                     Text(
                        text = "${downloadProgress.toInt()}%",
                        style = MaterialTheme.typography.overline,
                        color = PremiumGold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isLibrary) {
                // Show download button for stream songs in library
                if (!isDownloaded && !isWaiting && (downloadProgress == null || downloadProgress == 0f)) {
                    IconButton(onClick = {
                        onDownloadClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Download",
                            tint = ElectricPurple
                        )
                    }
                }

                IconButton(onClick = onOptionClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.Gray
                    )
                }
            } else {
                if (isDownloaded) {
                     Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (!isWaiting && (downloadProgress == null || downloadProgress == 0f)) {
                    IconButton(onClick = {
                        onDownloadClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Download",
                            tint = ElectricPurple
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MusicCard(
    title: String,
    subtitle: String, // Changed from artist to subtitle
    thumbnailUrl: String,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    isWaiting: Boolean = false,
    isCached: Boolean = false,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
        .width(160.dp)
        .height(220.dp)
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = SurfaceBlue,
        onClick = onClick
    ) {
        Column {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isCached && !isDownloaded && !isWaiting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Instant Play",
                            tint = PremiumGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (isWaiting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = PremiumGold,
                            strokeWidth = 3.dp
                        )
                    }
                } else if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             CircularProgressIndicator(
                                 progress = downloadProgress / 100f,
                                 modifier = Modifier.size(32.dp),
                                 color = PremiumGold,
                                 backgroundColor = Color.White.copy(alpha = 0.3f),
                             )
                             Spacer(modifier = Modifier.height(4.dp))
                             Text("${downloadProgress.toInt()}%", color = Color.White, fontSize = 12.sp)
                         }
                    }
                } else if (isDownloaded) {
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
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 14.sp)
                Text(subtitle, color = Color.Gray, maxLines = 1, fontSize = 12.sp)
            }
        }
    }
}
