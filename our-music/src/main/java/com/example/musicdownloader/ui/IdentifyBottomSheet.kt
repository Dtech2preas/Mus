package com.example.musicdownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyBottomSheet(
    onDismiss: () -> Unit,
    onSelectShazam: () -> Unit,
    onSelectRoulette: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Discover Music",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ListItem(
                headlineContent = { Text("Music Discovery Roulette") },
                supportingContent = { Text("Swipe through highlights of new songs recommended for you") },
                leadingContent = {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                },
                modifier = Modifier.clickable { onSelectRoulette() }
            )

            ListItem(
                headlineContent = { Text("Identify Song (Shazam)") },
                supportingContent = { Text("Listen and identify the song playing around you") },
                leadingContent = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                modifier = Modifier.clickable { onSelectShazam() }
            )
        }
    }
}
