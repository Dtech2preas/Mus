package com.example.musicdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.QueueItem
import com.example.musicdownloader.SharedQueueManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedQueueScreen(onBack: () -> Unit, onPlayItem: (QueueItem) -> Unit) {
    val items by SharedQueueManager.queueItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared LDR Queue", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlue)
            )
        },
        containerColor = DeepBlue
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                SharedQueueItemRow(
                    item = item,
                    onPlay = { onPlayItem(item) },
                    onDelete = { SharedQueueManager.removeFromQueue(item.firebaseKey) }
                )
            }
        }
    }
}

@Composable
fun SharedQueueItemRow(item: QueueItem, onPlay: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E2A))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(item.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(item.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            Text("Added by ${item.addedBy}", color = PremiumGold, fontSize = 10.sp)
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
        }
    }
}
