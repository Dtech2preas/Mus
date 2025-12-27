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

    // Softer Look: More padding, rounded background if selected (not implemented here but structure supports it)
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
                .padding(horizontal = 16.dp, vertical = 8.dp) // Increased padding
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with Smart Overlay
            Box(
                modifier = Modifier
                    .size(56.dp) // Slightly larger
                    .clip(RoundedCornerShape(8.dp)) // Softer corners
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Progress Overlay
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

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge, // Larger text
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
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

                // Progress Text
                if (downloadProgress != null && downloadProgress > 0f && downloadProgress < 100f) {
                     Text(
                        text = "${downloadProgress.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricPurple
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Smart Action Icon
            if (isLibrary) {
                // In Library, usually just More options.
                IconButton(onClick = onOptionClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.Gray
                    )
                }
            } else {
                // In Home/Search: Smart Logic
                // If downloaded -> Play Icon (or nothing, just click row)
                // If downloading -> Progress (handled in thumb/text, but maybe show nothing here or cancel)
                // If not downloaded -> Download Icon

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
                            imageVector = Icons.Default.ArrowDropDown, // Using ArrowDropDown as Download
                            contentDescription = "Download",
                            tint = ElectricPurple
                        )
                    }
                }
            }
        }
    }
}
