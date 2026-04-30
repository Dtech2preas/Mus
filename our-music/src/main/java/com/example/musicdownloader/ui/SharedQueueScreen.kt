package com.example.musicdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.*
import com.example.musicdownloader.ui.DeepBlue
import com.example.musicdownloader.ui.PremiumGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedQueueScreen(onBack: () -> Unit, viewModel: MusicViewModel) {
    val items by SharedQueueManager.queueItems.collectAsState()
    val session by SharedQueueManager.session.collectAsState()
    val context = LocalContext.current
    val myName = remember { UserPreferences.getUserName(context) ?: "" }

    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    // Sync Logic
    LaunchedEffect(session) {
        if (session.isConnected) {
            val playbackMediaId = session.playbackMediaId
            if (playbackMediaId.isNotEmpty()) {
                val currentId = currentMediaItem?.mediaId
                if (currentId != playbackMediaId) {
                    val item = items.find { it.id == playbackMediaId }
                    if (item != null) {
                        viewModel.playStream(VideoItem(item.id, item.title, "", item.artist, item.thumbnailUrl, "https://youtube.com/watch?v=${item.id}"))
                    }
                }

                // Wait for both to be ready before playing
                if (session.streamerOwamiReady && session.streamerJonasReady) {
                    if (session.playbackState == "PLAYING" && !isPlaying) {
                        viewModel.togglePlayPause()
                    } else if (session.playbackState == "PAUSED" && isPlaying) {
                        viewModel.togglePlayPause()
                    }
                }
            }
        }
    }

    // Auto-remove finished song
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    LaunchedEffect(currentPosition, duration) {
        if (session.isConnected && session.playbackMediaId.isNotEmpty() && duration > 0 && currentPosition >= duration - 1000) {
            // Song finished, remove it and potentially play next
            if (session.playbackMediaId == items.firstOrNull()?.id) {
                SharedQueueManager.removeFirst()
                val next = items.getOrNull(1)
                if (next != null) {
                    SharedQueueManager.updatePlayback(next.id, "PLAYING", 0)
                } else {
                    SharedQueueManager.updatePlayback("", "IDLE", 0)
                }
            }
        }
    }

    // Auto-Ready logic
    LaunchedEffect(currentMediaItem, viewModel.uiState.collectAsState().value.isLoadingPlayer) {
        if (session.isConnected && currentMediaItem?.mediaId == session.playbackMediaId && !viewModel.uiState.value.isLoadingPlayer) {
            SharedQueueManager.setReady(true)
        } else {
            SharedQueueManager.setReady(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared LDR Queue", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { SharedQueueManager.clearQueue() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Queue", tint = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (session.isConnected) "Connected" else "Connect", color = if (session.isConnected) Color.Green else Color.Gray, fontSize = 12.sp)
                        Checkbox(
                            checked = session.isConnected,
                            onCheckedChange = { SharedQueueManager.connect(it) },
                            colors = CheckboxDefaults.colors(checkedColor = Color.Green)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlue)
            )
        },
        containerColor = DeepBlue
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (session.isConnected) {
                SharedPlaybackControls(session, myName, viewModel)
            }

            Text(
                text = if (session.lastTurn == myName) "Partner's Turn" else "Your Turn",
                color = PremiumGold,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold
            )

            if (items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Queue is empty. Add some songs!", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { item ->
                        SharedQueueItemRow(
                            item = item,
                            onPlay = {
                                if (session.isConnected) {
                                    SharedQueueManager.updatePlayback(item.id, "PLAYING", 0)
                                } else {
                                    viewModel.playStream(VideoItem(item.id, item.title, "", item.artist, item.thumbnailUrl, "https://youtube.com/watch?v=${item.id}"))
                                }
                            },
                            onDelete = { SharedQueueManager.removeFromQueue(item.firebaseKey) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharedPlaybackControls(session: SharedSession, myName: String, viewModel: MusicViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoading by viewModel.uiState.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(ready = session.streamerOwamiReady, label = "Owami")
                Spacer(modifier = Modifier.width(16.dp))
                StatusDot(ready = session.streamerJonasReady, label = "Jonas")
            }

            if (session.playbackMediaId.isNotEmpty() && (!session.streamerOwamiReady || !session.streamerJonasReady)) {
                Text("Waiting for partner to get stream URL...", color = PremiumGold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White)
                }

                IconButton(
                    onClick = {
                        val newState = if (session.playbackState == "PLAYING") "PAUSED" else "PLAYING"
                        SharedQueueManager.updatePlayback(session.playbackMediaId, newState, 0)
                    },
                    modifier = Modifier.size(64.dp).background(PremiumGold, CircleShape)
                ) {
                    Icon(
                        if (session.playbackState == "PLAYING") Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black
                    )
                }

                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun StatusDot(ready: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(if (ready) Color.Green else Color.Red, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
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
