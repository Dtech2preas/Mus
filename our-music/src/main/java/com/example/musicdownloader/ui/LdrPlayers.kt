package com.example.musicdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.SharedQueueManager
import com.example.musicdownloader.SharedSession
import com.example.musicdownloader.QueueItem

@Composable
fun LdrMiniPlayer(
    session: SharedSession,
    currentItem: QueueItem?,
    onClick: () -> Unit
) {
    if (currentItem == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF1E1E2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(currentItem.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(currentItem.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(currentItem.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }

        IconButton(onClick = { SharedQueueManager.toggleSyncPlayPause() }) {
            Icon(
                if (session.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
fun LdrFullScreenPlayer(
    session: SharedSession,
    currentItem: QueueItem?,
    onCollapse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlue)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White)
        }

        Spacer(modifier = Modifier.height(48.dp))

        Image(
            painter = rememberAsyncImagePainter(currentItem?.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(currentItem?.title ?: "No Song", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(currentItem?.artist ?: "Unknown Artist", color = Color.Gray, fontSize = 18.sp)

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Implement Skip Previous in SharedQueueManager if needed */ }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }

            IconButton(
                onClick = { SharedQueueManager.toggleSyncPlayPause() },
                modifier = Modifier.size(80.dp).background(PremiumGold, CircleShape)
            ) {
                Icon(
                    if (session.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(48.dp)
                )
            }

            IconButton(onClick = { SharedQueueManager.removeFirst() }) {
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
