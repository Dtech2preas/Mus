package com.dtech.music.windows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.dtech.music.windows.*
import com.dtech.music.windows.ui.*

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreSelectionScreen(onComplete: () -> Unit, preferences: UserPreferences) {
    var selectedGenres by remember { mutableStateOf(setOf<String>()) }
    var customGenreText by remember { mutableStateOf("") }

    var selectedArtists by remember { mutableStateOf(setOf<String>()) }
    var customArtistText by remember { mutableStateOf("") }

    val genrePresets = listOf("Amapiano", "Hip Hop", "Afro Soul", "Gospel", "R&B", "Deep House", "Pop", "Jazz")

    Scaffold(
        bottomBar = {
            Button(
                onClick = {
                    preferences.setFavoriteGenres(selectedGenres.toList())
                    preferences.setFavoriteArtists(selectedArtists.toList())
                    onComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                enabled = selectedGenres.isNotEmpty() || selectedArtists.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(backgroundColor = NeonBlue)
            ) {
                Text("Start Your Journey", fontSize = 18.sp, color = Color.White)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            TechBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Text(
                            text = "Add your vibe",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonBlue
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        Text(
                            text = "Select your favourite genres and artists to build your unique feed.",
                            fontSize = 18.sp,
                            color = Color.LightGray
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }

                    // --- GENRES SECTION ---
                    item {
                        Text("Favorite Genres", fontSize = 20.sp, color = Color.White)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customGenreText,
                                onValueChange = { customGenreText = it },
                                label = { Text("Add Genre", color = Color.Gray) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White),
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
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            genrePresets.forEach { genre ->
                                val isSelected = selectedGenres.contains(genre)
                                Surface(
                                    modifier = Modifier.clickable {
                                        selectedGenres = if (isSelected) selectedGenres - genre else selectedGenres + genre
                                    },
                                    color = if (isSelected) NeonBlue else Color.DarkGray,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(genre, color = Color.White)
                                    }
                                }
                            }
                            selectedGenres.filter { !genrePresets.contains(it) }.forEach { genre ->
                                Surface(
                                    modifier = Modifier.clickable {
                                        selectedGenres = selectedGenres - genre
                                    },
                                    color = NeonBlue,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(genre, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }

                    // --- ARTISTS SECTION ---
                    item {
                        Text("Favorite Artists", fontSize = 20.sp, color = Color.White)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customArtistText,
                                onValueChange = { customArtistText = it },
                                label = { Text("Add Artist", color = Color.Gray) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (customArtistText.isNotBlank()) {
                                        selectedArtists = selectedArtists + customArtistText.trim()
                                        customArtistText = ""
                                    }
                                },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedArtists.forEach { artist ->
                                Surface(
                                    modifier = Modifier.clickable {
                                        selectedArtists = selectedArtists - artist
                                    },
                                    color = NeonBlue,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(artist, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(56.dp)) }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(repository: MusicRepository, player: DesktopMusicPlayer) {
    val coroutineScope = rememberCoroutineScope()
    val recentlyPlayed by repository.recentlyPlayed.collectAsState()
    val madeForYou by repository.madeForYou.collectAsState()
    val recommendedSongs by repository.recommendedSongs.collectAsState()
    val genreFeeds by repository.genreFeeds.collectAsState()

    LaunchedEffect(Unit) {
        repository.refreshRecentlyPlayed()
        if (madeForYou.isEmpty()) {
             repository.fetchMadeForYou()
        }
        if (recommendedSongs.isEmpty()) {
             repository.refreshRecommendations()
        }
    }

    val displayMadeForYou = remember(madeForYou) { madeForYou.shuffled() }

    val deduplicatedLists = remember(recommendedSongs, recentlyPlayed, genreFeeds, displayMadeForYou) {
        val seenIds = mutableSetOf<String>()

        val recommended = recommendedSongs.filter { seenIds.add(it.id) }
        val history = recentlyPlayed.filter { seenIds.add(it.id) }

        displayMadeForYou.forEach { seenIds.add(it.id) }

        val trendingFeeds = genreFeeds.map { feed ->
            feed.copy(songs = feed.songs.filter { seenIds.add(it.id) })
        }

        Triple(recommended, history, trendingFeeds)
    }

    val displayRecommended = deduplicatedLists.first
    val displayHistory = deduplicatedLists.second
    val deduplicatedTrendingFeeds = deduplicatedLists.third

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item { GreetingHeader() }

        if (displayRecommended.isNotEmpty()) {
            item {
                Text(
                    text = "Recommended For You",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(displayRecommended, key = { it.id }) { song ->
                        MusicCard(
                            title = song.title,
                            subtitle = song.author,
                            thumbnailUrl = song.thumbnailUrl,
                            isDownloaded = false,
                            downloadProgress = null,
                            onClick = {
                                coroutineScope.launch {
                                    val url = repository.getStreamUrl(song.id)
                                    if (url != null) {
                                        player.playUrl(url)
                                        repository.addToHistory(song)
                                    }
                                }
                            },
                            onDownload = { },
                            modifier = Modifier.width(126.dp).height(180.dp)
                        )
                    }
                }
            }
        }

        if (displayHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Recently Played",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(displayHistory, key = { "history_${it.id}" }) { item ->
                        MusicCard(
                            title = item.title,
                            subtitle = item.author,
                            thumbnailUrl = item.thumbnailUrl,
                            isDownloaded = false,
                            downloadProgress = null,
                            onClick = {
                                coroutineScope.launch {
                                    val url = repository.getStreamUrl(item.id)
                                    if (url != null) {
                                        player.playUrl(url)
                                        repository.addToHistory(item)
                                    }
                                }
                            },
                            onDownload = { },
                            modifier = Modifier.width(102.dp).height(136.dp)
                        )
                    }
                }
            }
        }

        if (displayMadeForYou.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Made for You",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val chunks = displayMadeForYou.chunked(10)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(chunks) { chunk ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                chunk.forEach { song ->
                                    MusicCard(
                                        title = song.title,
                                        subtitle = song.author,
                                        thumbnailUrl = song.thumbnailUrl,
                                        isDownloaded = false,
                                        downloadProgress = null,
                                        onClick = {
                                            coroutineScope.launch {
                                                val url = repository.getStreamUrl(song.id)
                                                if (url != null) {
                                                    player.playUrl(url)
                                                    repository.addToHistory(song)
                                                }
                                            }
                                        },
                                        onDownload = { },
                                        modifier = Modifier.width(160.dp).height(220.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (displayHistory.isEmpty() && displayRecommended.isEmpty()) {
            item {
                CircularProgressIndicator(color = NeonBlue, modifier = Modifier.padding(16.dp))
            }
        }

        deduplicatedTrendingFeeds.forEach { feed ->
            val displayTrending = feed.songs
            if (displayTrending.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trending in ",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = feed.genreName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = PremiumGold
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(displayTrending, key = { "trending_${it.id}" }) { song ->
                                MusicCard(
                                    title = song.title,
                                    subtitle = song.author,
                                    thumbnailUrl = song.thumbnailUrl,
                                    isDownloaded = false,
                                    downloadProgress = null,
                                    onClick = {
                                        coroutineScope.launch {
                                            val url = repository.getStreamUrl(song.id)
                                            if (url != null) {
                                                player.playUrl(url)
                                                repository.addToHistory(song)
                                            }
                                        }
                                    },
                                    onDownload = { },
                                    modifier = Modifier.width(160.dp).height(220.dp)
                                )
                            }
                        }
                    }
                }
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
                    MusicRowItem(
                        title = item.title,
                        subtitle = item.author,
                        thumbnailUrl = item.thumbnailUrl,
                        onClick = {
                            coroutineScope.launch {
                                val url = repository.getStreamUrl(item.id)
                                if (url != null) {
                                    player.playUrl(url)
                                    repository.addToHistory(item)
                                }
                            }
                        }
                    )
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
            Tab(selected = currentTab == 0, onClick = { currentTab = 0 }) { Text("All Songs", color = if (currentTab == 0) NeonBlue else Color.Gray, modifier = Modifier.padding(16.dp)) }
            Tab(selected = currentTab == 1, onClick = { currentTab = 1 }) { Text("Liked", color = if (currentTab == 1) NeonBlue else Color.Gray, modifier = Modifier.padding(16.dp)) }
            Tab(selected = currentTab == 2, onClick = { currentTab = 2 }) { Text("Playlists", color = if (currentTab == 2) NeonBlue else Color.Gray, modifier = Modifier.padding(16.dp)) }
            Tab(selected = currentTab == 3, onClick = { currentTab = 3 }) { Text("Artists", color = if (currentTab == 3) NeonBlue else Color.Gray, modifier = Modifier.padding(16.dp)) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (currentTab == 0 && librarySongs.isEmpty()) {
            Spacer(modifier = Modifier.height(64.dp))
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
            Text("Library is empty.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        } else if (currentTab == 0) {
             LazyColumn(modifier = Modifier.fillMaxWidth()) {
                  items(librarySongs) { item ->
                       MusicRowItem(
                           title = item.title,
                           subtitle = item.author,
                           thumbnailUrl = item.thumbnailUrl,
                           onClick = {
                               coroutineScope.launch {
                                   val url = repository.getStreamUrl(item.id)
                                   if (url != null) {
                                       player.playUrl(url)
                                       repository.addToHistory(item)
                                   }
                               }
                           }
                       )
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
                Text("High-End Mode", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Enable instant playback caching. May use more memory/bandwidth.", color = Color.Gray, fontSize = 14.sp)
            }
            Switch(checked = highEndMode, onCheckedChange = { preferences.setHighEndMode(it) }, colors = SwitchDefaults.colors(checkedThumbColor = NeonBlue))
        }
        Divider(color = Color.DarkGray)

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text("Prefetch Limit for Smart Shuffle: $buffer", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Number of recommendations to queue ahead.", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
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
