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
import android.widget.Toast
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
    val myName = remember { UserPreferences.getUserName(context) ?: "" }.lowercase()

    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showMyLibrary by remember { mutableStateOf(false) }
    var showPartnerLibrary by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    // Sync Logic
    LaunchedEffect(session.isConnected, session.playbackMediaId, session.playbackState) {
        if (session.isConnected) {
            val playbackMediaId = session.playbackMediaId
            if (playbackMediaId.isNotEmpty()) {
                val currentId = currentMediaItem?.mediaId
                if (currentId != playbackMediaId) {
                    // Fetch the stream URL immediately when ID changes, but don't play yet if state is IDLE
                    val item = items.find { it.id == playbackMediaId }
                    if (item != null && viewModel.uiState.value.isLoadingPlayer == false) {
                        viewModel.playStream(VideoItem(item.id, item.title, "", item.artist, item.thumbnailUrl, "https://youtube.com/watch?v=${item.id}"))
                        // After calling playStream, it will auto-play locally. But if the partner isn't ready or state is IDLE, we pause.
                        // Actually, playStream automatically plays. We handle pausing below when it loads.
                    }
                } else if (currentId == playbackMediaId) {
                    // It's loaded, make sure playback matches state
                    if (session.playbackState == "PLAYING" && !isPlaying) {
                        // viewModel.togglePlayPause() - handle this via LaunchedEffect to avoid recomposition loops
                    } else if ((session.playbackState == "PAUSED" || session.playbackState == "IDLE") && isPlaying) {
                        // viewModel.togglePlayPause() - handle this via LaunchedEffect
                    }
                }
            }
        }
    }

    // Auto-remove finished song
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    // When my playback finishes, mark myself as finished
    LaunchedEffect(currentPosition, duration) {
        if (session.isConnected && session.playbackMediaId.isNotEmpty() && duration > 0 && currentPosition >= duration - 1000) {
            val myFinished = if (myName == "owami") session.owamiFinished else session.jonasFinished
            if (!myFinished) {
                SharedQueueManager.setFinished(true)
                // Optionally pause if we're waiting for the other
                if (isPlaying) viewModel.togglePlayPause()
            }
        }
    }

    // When both are finished, advance the queue
    LaunchedEffect(session.owamiFinished, session.jonasFinished) {
        if (session.isConnected && session.owamiFinished && session.jonasFinished) {
            // Only one person should trigger the queue advance to avoid race conditions.
            // Let's use lastTurn or whoever added the song. Or just arbitrarily pick 'owami' as the coordinator for advancing.
            // Wait, we can just say if it's the current song, let's just advance it.
            if (session.playbackMediaId == items.firstOrNull()?.id) {
                // Since this runs on both clients, let's make only the person whose turn it is NOT (meaning the one who added the song) advance it.
                // Actually, an easier way is just one explicit coordinator:
                if (myName == "owami" || (!session.owamiConnected && myName == "jonas")) {
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
    }

    // Auto-Ready logic
    LaunchedEffect(currentMediaItem, viewModel.uiState.collectAsState().value.isLoadingPlayer) {
        if (session.isConnected && currentMediaItem?.mediaId == session.playbackMediaId && !viewModel.uiState.value.isLoadingPlayer) {
            SharedQueueManager.setReady(true)
        } else {
            SharedQueueManager.setReady(false)
        }
    }

    // Playback state sync effect
    LaunchedEffect(session.playbackState, isPlaying, session.playbackMediaId, currentMediaItem?.mediaId) {
        if (session.isConnected && currentMediaItem?.mediaId == session.playbackMediaId) {
            if (session.playbackState == "PLAYING" && !isPlaying) {
                viewModel.togglePlayPause()
            } else if ((session.playbackState == "PAUSED" || session.playbackState == "IDLE") && isPlaying) {
                viewModel.togglePlayPause()
            }
        }
    }

    val isMyTurn = session.lastTurn.lowercase() != myName

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
                    val myConnected = if (myName == "owami") session.owamiConnected else session.jonasConnected
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Text(if (myConnected) "Connected" else "Connect", color = if (myConnected) Color.Green else Color.Gray, fontSize = 12.sp)
                        Checkbox(
                            checked = myConnected,
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
            // Section 1: Play Station
            if (session.isConnected) {
                SharedPlaybackControls(session, myName, viewModel)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
                ) {
                    Text(
                        "Waiting for partner to connect...",
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
                    )
                }
            }

            // Section 2: Choosing a Song
            Text(
                "Choose a Song",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceButton("My Library", Icons.Default.LibraryMusic, Modifier.weight(1f), enabled = isMyTurn) { showMyLibrary = true }
                ChoiceButton("Partner's", Icons.Default.Favorite, Modifier.weight(1f), enabled = isMyTurn) { showPartnerLibrary = true }
                ChoiceButton("Search", Icons.Default.Search, Modifier.weight(1f), enabled = isMyTurn) { showSearch = true }
            }

            if (!isMyTurn) {
                Text(
                    "Waiting for partner to choose a song...",
                    color = PremiumGold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Section 3: Queue Section
            Text(
                "Shared Queue",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            if (items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Queue is empty.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { item ->
                        SharedQueueItemRow(
                            item = item,
                            onPlay = {
                                if (session.isConnected) {
                                    SharedQueueManager.updatePlayback(item.id, "IDLE", 0)
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

    // Mini Pop-ups
    if (showMyLibrary) {
        LdrSongSelectorPopup(
            title = "My Library",
            songs = viewModel.librarySongs.collectAsState().value.map {
                VideoItem(
                    id = it.id,
                    title = it.title,
                    uploader = it.artist,
                    duration = it.duration,
                    thumbnailUrl = it.thumbnailUrl,
                    webUrl = "https://youtube.com/watch?v=${it.id}"
                )
            },
            onDismiss = { showMyLibrary = false },
            onAdd = { SharedQueueManager.addToQueue(it, myName); showMyLibrary = false }
        )
    }

    if (showPartnerLibrary) {
        var partnerSongs by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
        LaunchedEffect(Unit) {
            FirebaseManager.getPartnerLibrary { data ->
                val allSongs = data?.get("allSongs") as? List<Map<String, Any>> ?: emptyList()
                partnerSongs = allSongs.map {
                    VideoItem(
                        id = it["id"] as? String ?: "",
                        title = it["title"] as? String ?: "",
                        uploader = it["artist"] as? String ?: "",
                        duration = "",
                        thumbnailUrl = it["thumbnailUrl"] as? String ?: "",
                        webUrl = "https://youtube.com/watch?v=${it["id"]}"
                    )
                }
            }
        }
        LdrSongSelectorPopup(
            title = "Partner's Library",
            songs = partnerSongs,
            onDismiss = { showPartnerLibrary = false },
            onAdd = { SharedQueueManager.addToQueue(it, myName); showPartnerLibrary = false }
        )
    }

    if (showSearch) {
        LdrSearchPopup(
            viewModel = viewModel,
            onDismiss = { showSearch = false },
            onAdd = { SharedQueueManager.addToQueue(it, myName); showSearch = false }
        )
    }
}

@Composable
fun ChoiceButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E1E2A),
            contentColor = if (enabled) Color.White else Color.Gray,
            disabledContainerColor = Color(0xFF1E1E2A).copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LdrSongSelectorPopup(title: String, songs: List<VideoItem>, onDismiss: () -> Unit, onAdd: (VideoItem) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DeepBlue) {
        Column(modifier = Modifier.fillMaxHeight(0.8f).padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            if (songs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No songs found", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(songs) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E2A)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(song.thumbnailUrl),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(song.uploader, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { onAdd(song) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = PremiumGold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LdrSearchPopup(viewModel: MusicViewModel, onDismiss: () -> Unit, onAdd: (VideoItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.uiState.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DeepBlue) {
        Column(modifier = Modifier.fillMaxHeight(0.8f).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search songs...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (results.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = PremiumGold)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results.results) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E2A)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(song.thumbnailUrl),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                Text(song.uploader, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { onAdd(song) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = PremiumGold)
                            }
                        }
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

    val myReady = if (myName == "owami") session.streamerOwamiReady else session.streamerJonasReady
    val partnerReady = if (myName == "owami") session.streamerJonasReady else session.streamerOwamiReady

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(ready = myReady, label = "You")
                Spacer(modifier = Modifier.width(16.dp))
                StatusDot(ready = partnerReady, label = "Partner")
            }

            if (session.playbackMediaId.isNotEmpty() && !partnerReady) {
                Text("Waiting for partner to get stream URL...", color = PremiumGold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            } else if (session.playbackMediaId.isNotEmpty() && !myReady) {
                Text("Getting stream URL...", color = PremiumGold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White)
                }

                val context = LocalContext.current
                IconButton(
                    onClick = {
                        if (!partnerReady && session.playbackState != "PLAYING") {
                            Toast.makeText(context, "Partner is not ready", Toast.LENGTH_SHORT).show()
                        } else {
                            val newState = if (session.playbackState == "PLAYING") "PAUSED" else "PLAYING"
                            SharedQueueManager.updatePlayback(session.playbackMediaId, newState, 0)
                        }
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
