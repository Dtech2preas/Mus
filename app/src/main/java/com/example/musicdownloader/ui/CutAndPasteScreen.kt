package com.example.musicdownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutAndPasteScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val librarySongs by viewModel.librarySongs.collectAsState(initial = emptyList())
    var mixName by remember { mutableStateOf("") }
    var selectedSongs by remember { mutableStateOf(listOf<CutSegment>()) }
    var showSongPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                text = "Create Custom Mix",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (mixName.isNotBlank() && selectedSongs.isNotEmpty()) {
                        viewModel.saveCustomMix(mixName, selectedSongs)
                        onBack()
                    }
                },
                enabled = mixName.isNotBlank() && selectedSongs.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A6FF))
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = mixName,
            onValueChange = { mixName = it },
            label = { Text("Mix Name") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00A6FF),
                focusedLabelColor = Color(0xFF00A6FF),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Mix Segments",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(selectedSongs) { segment ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(segment.song.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(segment.song.artist, color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Start: ${formatMixTime(segment.startMs)}", color = Color(0xFF00A6FF))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("End: ${formatMixTime(segment.endMs)}", color = Color(0xFF00A6FF))
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { showSongPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A35))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Song Segment")
                    Spacer(Modifier.width(8.dp))
                    Text("Add Song Segment")
                }
            }
        }
    }

    if (showSongPicker) {
        SongPickerSheet(
            songs = librarySongs,
            onDismiss = { showSongPicker = false },
            onSongSelected = { song, start, end ->
                selectedSongs = selectedSongs + CutSegment(song, start, end)
                showSongPicker = false
            },
            viewModel = viewModel
        )
    }
}

data class CutSegment(
    val song: Song,
    val startMs: Long,
    val endMs: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerSheet(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSongSelected: (Song, Long, Long) -> Unit,
    viewModel: MusicViewModel
) {
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var startRange by remember { mutableStateOf(0f) }
    var endRange by remember { mutableStateOf(100f) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C26)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            if (selectedSong == null) {
                Text(
                    "Select a Song",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(songs) { song ->
                        ListItem(
                            headlineContent = { Text(song.title, color = Color.White) },
                            supportingContent = { Text(song.artist, color = Color.Gray) },
                            modifier = Modifier.clickable { selectedSong = song },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            } else {
                Text(
                    "Select Segment",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    selectedSong!!.title,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Simple range slider mock (since RangeSlider is sometimes tricky with Material3 without exact imports)
                val durationMs = parseDurationToMs(selectedSong!!.duration)

                // Real RangeSlider
                RangeSlider(
                    value = startRange..endRange,
                    onValueChange = { range ->
                        startRange = range.start
                        endRange = range.endInclusive
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00A6FF),
                        activeTrackColor = Color(0xFF00A6FF)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMixTime(startRange.toLong()), color = Color.White)
                    Text(formatMixTime(endRange.toLong()), color = Color.White)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            viewModel.playSegmentPreview(selectedSong!!, startRange.toLong(), endRange.toLong())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A35))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Segment", tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Preview", color = Color.White)
                    }

                    Row {
                        TextButton(onClick = { selectedSong = null }) {
                            Text("Back", color = Color.Gray)
                        }
                        Button(
                            onClick = { onSongSelected(selectedSong!!, startRange.toLong(), endRange.toLong()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A6FF))
                        ) {
                            Text("Add Segment")
                        }
                    }
                }
            }
        }
    }
}

fun parseDurationToMs(duration: String): Long {
    try {
        val parts = duration.split(":")
        if (parts.size == 2) {
            return (parts[0].toLong() * 60 + parts[1].toLong()) * 1000
        } else if (parts.size == 3) {
            return (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
        }
    } catch (e: Exception) {}
    return 180000L // Default 3 mins
}

fun formatMixTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
