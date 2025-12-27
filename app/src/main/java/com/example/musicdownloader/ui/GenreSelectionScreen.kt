package com.example.musicdownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreSelectionScreen(
    onDone: (Set<String>) -> Unit
) {
    var selectedGenres by remember { mutableStateOf(setOf<String>()) }
    var customGenreText by remember { mutableStateOf("") }

    val presets = listOf("Amapiano", "Hip Hop", "Afro Soul", "Gospel", "R&B", "Deep House", "Pop", "Jazz")

    Scaffold(
        bottomBar = {
            Button(
                onClick = { onDone(selectedGenres) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                enabled = selectedGenres.isNotEmpty()
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Pick Your Vibe",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "What do you like to listen to? We'll build a feed just for you.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Custom Input
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customGenreText,
                    onValueChange = { customGenreText = it },
                    label = { Text("Add your own (e.g. Lo-Fi)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (customGenreText.isNotBlank()) {
                            selectedGenres = selectedGenres + customGenreText.trim()
                            customGenreText = ""
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { genre ->
                    val isSelected = selectedGenres.contains(genre)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedGenres = if (isSelected) {
                                selectedGenres - genre
                            } else {
                                selectedGenres + genre
                            }
                        },
                        label = { Text(genre) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }

                // Also display added custom genres as chips
                selectedGenres.filter { !presets.contains(it) }.forEach { genre ->
                    FilterChip(
                        selected = true,
                        onClick = { selectedGenres = selectedGenres - genre },
                        label = { Text(genre) },
                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                    )
                }
            }
        }
    }
}
