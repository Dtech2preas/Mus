package com.dtech.music.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// Colors mimicking the tech vibe
val TechBlack = Color(0xFF121212)
val NeonBlue = Color(0xFF00A6FF)

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "DTECH MUSIC // PREASX24",
        state = windowState
    ) {
        MaterialTheme(
            colors = darkColors(
                primary = NeonBlue,
                background = TechBlack,
                surface = Color(0xFF1E1E1E)
            )
        ) {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            BottomNavigation(
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.primary
            ) {
                BottomNavigationItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    selectedContentColor = NeonBlue,
                    unselectedContentColor = Color.Gray
                )
                BottomNavigationItem(
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                    label = { Text("Library") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    selectedContentColor = NeonBlue,
                    unselectedContentColor = Color.Gray
                )
                BottomNavigationItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    selectedContentColor = NeonBlue,
                    unselectedContentColor = Color.Gray
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> LibraryScreen()
                2 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "HOME",
            color = NeonBlue,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Made for You",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Mock Grid placeholder
        LazyColumn {
            items(10) { index ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.DarkGray)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Song Title $index", color = Color.White)
                            Text("Artist $index", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LibraryMusic,
            contentDescription = null,
            tint = NeonBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "YOUR LIBRARY",
            color = NeonBlue,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tracks will appear here soon.",
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "SETTINGS",
            color = NeonBlue,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingItem("High-End Mode", "Enable instant playback caching")
        SettingItem("Smart Shuffle Buffer", "Configure queue buffering")
        SettingItem("Favorite Genres", "Select to improve recommendations")
    }
}

@Composable
fun SettingItem(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Text(subtitle, color = Color.Gray, fontSize = 14.sp)
        Divider(color = Color.DarkGray, modifier = Modifier.padding(top = 12.dp))
    }
}
