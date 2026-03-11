package com.dtech.music.windows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.dtech.music.windows.*

@Composable
fun MainScreen(
    repository: MusicRepository,
    player: DesktopMusicPlayer,
    preferences: UserPreferences
) {
    val navController = remember { NavController() }
    val isFirstTime by preferences.firstTimeSetupComplete.collectAsState()

    if (!isFirstTime) {
        GenreSelectionScreen(
            onComplete = {
                preferences.setFirstTimeSetupComplete(true)
                navController.navigate(Screen.HOME)
            },
            preferences = preferences
        )
    } else {
        Scaffold(
            bottomBar = {
                BottomNavigation(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.primary
                ) {
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = navController.currentScreen == Screen.HOME,
                        onClick = { navController.navigate(Screen.HOME) },
                        selectedContentColor = NeonBlue,
                        unselectedContentColor = Color.Gray
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        selected = navController.currentScreen == Screen.SEARCH,
                        onClick = { navController.navigate(Screen.SEARCH) },
                        selectedContentColor = NeonBlue,
                        unselectedContentColor = Color.Gray
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Identify") },
                        label = { Text("Identify") },
                        selected = navController.currentScreen == Screen.IDENTIFY,
                        onClick = { navController.navigate(Screen.IDENTIFY) },
                        selectedContentColor = NeonBlue,
                        unselectedContentColor = Color.Gray
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library") },
                        selected = navController.currentScreen == Screen.LIBRARY,
                        onClick = { navController.navigate(Screen.LIBRARY) },
                        selectedContentColor = NeonBlue,
                        unselectedContentColor = Color.Gray
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = navController.currentScreen == Screen.SETTINGS,
                        onClick = { navController.navigate(Screen.SETTINGS) },
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
                when (navController.currentScreen) {
                    Screen.HOME -> HomeScreen(repository, player)
                    Screen.SEARCH -> SearchScreen(repository, player)
                    Screen.IDENTIFY -> IdentifyScreen()
                    Screen.LIBRARY -> LibraryScreen(repository, player)
                    Screen.SETTINGS -> SettingsScreen(preferences)
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun GenreSelectionScreen(onComplete: () -> Unit, preferences: UserPreferences) {
    var genresText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to DTECH MUSIC", color = NeonBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter your favorite genres (comma separated):", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = genresText,
            onValueChange = { genresText = it },
            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White),
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val list = genresText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                preferences.setFavoriteGenres(list)
                onComplete()
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = NeonBlue)
        ) {
            Text("Let's Go", color = Color.White)
        }
    }
}

@Composable
fun HomeScreen(repository: MusicRepository, player: DesktopMusicPlayer) {
    val coroutineScope = rememberCoroutineScope()
    val recentlyPlayed by repository.recentlyPlayed.collectAsState()
    val madeForYou by repository.madeForYou.collectAsState()

    LaunchedEffect(Unit) {
        repository.refreshRecentlyPlayed()
        if (madeForYou.isEmpty()) {
             repository.fetchMadeForYou()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("HOME", color = NeonBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        }

        if (recentlyPlayed.isNotEmpty()) {
            item {
                Text("Recently Played", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(recentlyPlayed.take(5)) { item ->
                VideoRowItem(item, onClick = {
                    coroutineScope.launch {
                        val url = repository.getStreamUrl(item.id)
                        if (url != null) {
                            player.playUrl(url)
                            repository.addToHistory(item)
                        }
                    }
                })
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (madeForYou.isNotEmpty()) {
             item {
                 Text("Made for You", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
             }
             items(madeForYou) { item ->
                  VideoRowItem(item, onClick = {
                     coroutineScope.launch {
                         val url = repository.getStreamUrl(item.id)
                         if (url != null) {
                             player.playUrl(url)
                             repository.addToHistory(item)
                         }
                     }
                 })
             }
        } else {
            item {
                CircularProgressIndicator(color = NeonBlue, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun SearchScreen(repository: MusicRepository, player: DesktopMusicPlayer) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White),
            placeholder = { Text("Search InnerTube...", color = Color.Gray) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    if (query.isNotBlank()) {
                        isLoading = true
                        coroutineScope.launch {
                            results = repository.search(query)
                            isLoading = false
                        }
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonBlue)
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(color = NeonBlue, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn {
                items(results) { item ->
                    VideoRowItem(item, onClick = {
                        coroutineScope.launch {
                            val url = repository.getStreamUrl(item.id)
                            if (url != null) {
                                player.playUrl(url)
                                repository.addToHistory(item)
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(repository: MusicRepository, player: DesktopMusicPlayer) {
    val coroutineScope = rememberCoroutineScope()
    var librarySongs by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var currentTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        repository.getAllLibrarySongs().collect { songs ->
            librarySongs = songs
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("YOUR LIBRARY", color = NeonBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = currentTab, backgroundColor = Color.Transparent, contentColor = NeonBlue) {
            Tab(selected = currentTab == 0, onClick = { currentTab = 0 }) { Text("All Songs", modifier = Modifier.padding(16.dp)) }
            Tab(selected = currentTab == 1, onClick = { currentTab = 1 }) { Text("Liked", modifier = Modifier.padding(16.dp)) }
            Tab(selected = currentTab == 2, onClick = { currentTab = 2 }) { Text("Playlists", modifier = Modifier.padding(16.dp)) }
            Tab(selected = currentTab == 3, onClick = { currentTab = 3 }) { Text("Artists", modifier = Modifier.padding(16.dp)) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentTab == 0 && librarySongs.isEmpty()) {
            Spacer(modifier = Modifier.height(64.dp))
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
            Text("Library is empty.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        } else if (currentTab == 0) {
             LazyColumn(modifier = Modifier.fillMaxWidth()) {
                  items(librarySongs) { item ->
                       VideoRowItem(item, onClick = {
                           coroutineScope.launch {
                               val url = repository.getStreamUrl(item.id)
                               if (url != null) {
                                   player.playUrl(url)
                                   repository.addToHistory(item)
                               }
                           }
                       })
                  }
             }
        } else {
            Spacer(modifier = Modifier.height(64.dp))
            Text("Feature coming soon.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SettingsScreen(preferences: UserPreferences) {
    val highEndMode by preferences.highEndMode.collectAsState()
    val buffer by preferences.smartShuffleBuffer.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("SETTINGS", color = NeonBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("High-End Mode", color = Color.White, fontSize = 16.sp)
                Text("Enable instant playback caching", color = Color.Gray, fontSize = 14.sp)
            }
            Switch(checked = highEndMode, onCheckedChange = { preferences.setHighEndMode(it) }, colors = SwitchDefaults.colors(checkedThumbColor = NeonBlue))
        }
        Divider(color = Color.DarkGray)

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text("Smart Shuffle Buffer: $buffer", color = Color.White, fontSize = 16.sp)
            Slider(
                value = buffer.toFloat(),
                onValueChange = { preferences.setSmartShuffleBuffer(it.toInt()) },
                valueRange = 1f..20f,
                steps = 19,
                colors = SliderDefaults.colors(thumbColor = NeonBlue, activeTrackColor = NeonBlue)
            )
        }
        Divider(color = Color.DarkGray)
    }
}

@Composable
fun IdentifyScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("IDENTIFY", color = NeonBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Listening for music...", color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator(color = NeonBlue)
        Spacer(modifier = Modifier.height(16.dp))
        Text("This feature relies on external APIs (like Shazam Web) on Android.", color = Color.Gray, fontSize = 12.sp)
        Text("A desktop port for audio recording is under development.", color = Color.Gray, fontSize = 12.sp)
    }
}
