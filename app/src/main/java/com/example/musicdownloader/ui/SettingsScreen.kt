package com.example.musicdownloader.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.musicdownloader.CookieDialog
import com.example.musicdownloader.CookieManager
import com.example.musicdownloader.UserPreferences

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onShowLogs: () -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    var showCookieDialog by remember { mutableStateOf(false) }

    // Manage Genres State
    var savedGenres by remember { mutableStateOf(UserPreferences.getGenres(context)) }
    var newGenreText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 1. Music Preferences (Genres)
        Text(
            text = "Music Preferences",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Your 'Made For You' Feed Genres:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedGenres.sorted().forEach { genre ->
                        InputChip(
                            selected = true,
                            onClick = {
                                UserPreferences.removeGenre(context, genre)
                                savedGenres = UserPreferences.getGenres(context) // Refresh
                            },
                            label = { Text(genre) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Add Genre
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGenreText,
                        onValueChange = { newGenreText = it },
                        label = { Text("Add Genre") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newGenreText.isNotBlank()) {
                                UserPreferences.addGenre(context, newGenreText.trim())
                                savedGenres = UserPreferences.getGenres(context) // Refresh
                                newGenreText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            }
        }

        // 2. Help Section
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "How to Use",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Swipe Right: Add song to Queue", style = MaterialTheme.typography.bodyMedium)
                Text("• Trash Icon: Delete song permanently", style = MaterialTheme.typography.bodyMedium)
                Text("• Background: Downloads continue even if you close the app.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // 3. Developer Tools
        Text(
            text = "Developer Tools",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Button(
            onClick = onShowLogs,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show Debug Logs")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { showCookieDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set YouTube Cookies")
        }

        if (showCookieDialog) {
            CookieDialog(
                onDismiss = { showCookieDialog = false },
                onSave = { cookie ->
                    CookieManager.saveCookie(context, cookie)
                    showCookieDialog = false
                    Toast.makeText(context, "Cookie Saved", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "App Version: 1.0 (DTECH MUSIC)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
