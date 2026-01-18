package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import com.example.musicdownloader.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEffectsSheet(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val bassStrength by viewModel.bassStrength.collectAsState()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsState()
    val isMono by viewModel.isMono.collectAsState()
    val abStart by viewModel.abStart.collectAsState()
    val abEnd by viewModel.abEnd.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "Audio Tuning",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 1. Playback Speed
            Text("Playback Speed", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                items(speeds) { speed ->
                    FilterChip(
                        selected = playbackSpeed == speed,
                        onClick = { viewModel.setPlaybackSpeed(speed) },
                        label = { Text("${speed}x") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. A-B Repeat
            Text("A-B Loop", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Set A
                Button(
                    onClick = {
                        if (abStart == C.TIME_UNSET) {
                            viewModel.setABPoints(currentPosition, abEnd)
                        } else {
                            // Clear A (and B if A is cleared usually, or just clear all)
                             viewModel.setABPoints(C.TIME_UNSET, abEnd)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (abStart != C.TIME_UNSET) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (abStart != C.TIME_UNSET) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(if (abStart != C.TIME_UNSET) "A: ${formatTime(abStart)}" else "Set A")
                }

                // Set B
                Button(
                    onClick = {
                        if (abEnd == C.TIME_UNSET) {
                             // Only set B if it's after A
                             if (abStart != C.TIME_UNSET && currentPosition > abStart) {
                                 viewModel.setABPoints(abStart, currentPosition)
                             } else if (abStart == C.TIME_UNSET) {
                                 // Auto-set A to 0 if B is set first? No, usually require A.
                                 // Or just set B and let loop imply 0->B?
                                 // Let's enforce A must be set first.
                             }
                        } else {
                            viewModel.setABPoints(abStart, C.TIME_UNSET)
                        }
                    },
                    enabled = abStart != C.TIME_UNSET,
                    modifier = Modifier.weight(1f),
                     colors = ButtonDefaults.buttonColors(
                        containerColor = if (abEnd != C.TIME_UNSET) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (abEnd != C.TIME_UNSET) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(if (abEnd != C.TIME_UNSET) "B: ${formatTime(abEnd)}" else "Set B")
                }
            }
            if (abStart != C.TIME_UNSET || abEnd != C.TIME_UNSET) {
                OutlinedButton(
                    onClick = { viewModel.clearABLoop() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Clear Loop")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Bass Boost
            Text("Bass Boost: ${(bassStrength / 10)}%", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = bassStrength.toFloat(),
                onValueChange = { viewModel.setBassStrength(it.toInt()) },
                valueRange = 0f..1000f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Virtualizer
            Text("Surround Sound: ${(virtualizerStrength / 10)}%", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = virtualizerStrength.toFloat(),
                onValueChange = { viewModel.setVirtualizerStrength(it.toInt()) },
                valueRange = 0f..1000f,
                steps = 10,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "--:--"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
