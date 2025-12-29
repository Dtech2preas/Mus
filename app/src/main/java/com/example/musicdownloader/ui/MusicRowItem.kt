package com.example.musicdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.ui.ElectricPurple
import com.example.musicdownloader.utils.HapticUtils

@Composable
fun MusicRowItem(
    title: String,
    artist: String,
    thumbnailUrl: String,
    isLibrary: Boolean = true,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onOptionClick: () -> Unit = {}
) {
    val context = LocalContext.current

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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(24.dp),
                            color = ElectricPurple,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                     Text(
                        text = "${downloadProgress.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricPurple
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isLibrary) {
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
                } else if (downloadProgress == null || downloadProgress == 0f) {
                    IconButton(onClick = {
                        HapticUtils.performHapticFeedback(context)
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

@Composable
fun MusicCard(
    title: String,
    artist: String,
    thumbnailUrl: String,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
        .width(160.dp)
        .height(220.dp)
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C26)),
        onClick = onClick
    ) {
        Column {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Image(
                    painter = rememberAsyncImagePainter(thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             CircularProgressIndicator(
                                 progress = { downloadProgress / 100f },
                                 modifier = Modifier.size(32.dp),
                                 color = ElectricPurple,
                                 trackColor = Color.White.copy(alpha = 0.3f),
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
                Text(artist, color = Color.Gray, maxLines = 1, fontSize = 12.sp)
            }
        }
    }
}
