package com.example.musicdownloader.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.musicdownloader.CookieManager
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.UserPreferences
import com.example.musicdownloader.utils.AdManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onShowLogs: () -> Unit,
    onNavigateToCompression: () -> Unit,
    contentPadding: PaddingValues
) {
    val viewModel: MusicViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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

        // 1. Theme Customization (NEW)
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
             modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
             Column(modifier = Modifier.padding(16.dp)) {
                 Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                 Spacer(modifier = Modifier.height(12.dp))

                 Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                     val colors = listOf(
                         0xFF7D5FFF, // Electric Purple (Default)
                         0xFFE91E63, // Pink
                         0xFF00E5FF, // Cyan
                         0xFF4CAF50, // Green
                         0xFFFFEB3B, // Yellow
                         0xFFFF5722  // Orange
                     )

                     colors.forEach { colorLong ->
                         Box(
                             modifier = Modifier
                                 .size(40.dp)
                                 .clip(CircleShape)
                                 .background(Color(colorLong))
                                 .clickable {
                                     UserPreferences.setThemeColor(context, colorLong)
                                     ThemeManager.updateTheme(colorLong)
                                 }
                         )
                     }
                 }
             }
        }

        // 2. Audio & Library (NEW)
        Text(
            text = "Audio & Library",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
             modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                 // Equalizer
                 Button(
                     onClick = { viewModel.launchEqualizer() },
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Text("Open System Equalizer")
                 }

                 Spacer(modifier = Modifier.height(8.dp))

                 // Import Local
                 val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                     androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                 ) { isGranted ->
                     if (isGranted) {
                         viewModel.importLocalSongs()
                     } else {
                         Toast.makeText(context, "Permission Denied. Cannot import songs.", Toast.LENGTH_SHORT).show()
                     }
                 }

                 Button(
                     onClick = {
                         val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                             android.Manifest.permission.READ_MEDIA_AUDIO
                         } else {
                             android.Manifest.permission.READ_EXTERNAL_STORAGE
                         }

                         if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                             viewModel.importLocalSongs()
                         } else {
                             launcher.launch(permission)
                         }
                     },
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Text("Import Local Songs (Device Storage)")
                 }

                 Spacer(modifier = Modifier.height(8.dp))

                 // Rescan
                 Button(
                    onClick = { viewModel.rescanLibrary() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                     Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Scan / Fix Metadata")
                }

                 Spacer(modifier = Modifier.height(8.dp))

                 // Compression
                 Button(
                     onClick = onNavigateToCompression,
                     modifier = Modifier.fillMaxWidth(),
                     colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                 ) {
                     Text("Audio Compression (Save Space)")
                 }
            }
        }

        // 3. Music Preferences (Genres)
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

        // 4. Support Section
        Text(
            text = "Support",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Telegram
                Button(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/DTECHX24"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Telegram",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Join our Telegram Channel")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Watch Ad
                Button(
                    onClick = { AdManager.showRandomAd(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Support",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Support D-TECH (Watch Ad)")
                }
            }
        }

        // 5. Developer Tools
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
            text = "App Version: 1.1 (DTECH MUSIC PRO)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
