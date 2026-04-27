package com.example.musicdownloader

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.ui.*
import com.example.musicdownloader.ui.DTechBlue
import com.example.musicdownloader.ui.PremiumGold
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicAppTheme {
                RequestPermissions()
                AppNavigation(viewModel)
            }
        }
    }
}

@Composable
fun RequestPermissions() {
    val context = LocalContext.current

    // Notification Permission (Android 13+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { }
        )
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(permission)
            }
        }
    }

    // Audio Record Permission removed: Visualizer no longer requires it.
    // IdentifyScreen handles its own permission request.
}

@Composable
fun AppNavigation(viewModel: MusicViewModel) {
    val context = LocalContext.current
    var isFirstRun by remember { mutableStateOf(UserPreferences.isFirstRun(context)) }

    if (isFirstRun) {
        GenreSelectionScreen(
            onDone = { genres, artists ->
                UserPreferences.saveGenres(context, genres)
                UserPreferences.saveArtists(context, artists)
                UserPreferences.setFirstRunCompleted(context)
                isFirstRun = false
                viewModel.loadGenreFeeds()
            }
        )
    } else {
        MainScreen(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    // -------------------------------------------------------------
    // NAVIGATION STATE (Custom Back Stack)
    // -------------------------------------------------------------
    val navigationStack = remember { mutableStateListOf<AppScreen>(AppScreen.Home) }

    fun navigateTo(screen: AppScreen) {
        navigationStack.add(screen)
    }

    fun popBackStack(): Boolean {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.size - 1)
            return true
        }
        return false
    }

    val currentScreen = navigationStack.lastOrNull() ?: AppScreen.Home

    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()

    val currentTab = when (currentScreen) {
        is AppScreen.Home -> 0
        is AppScreen.Search -> 1
        is AppScreen.Identify, is AppScreen.Roulette -> 2
        is AppScreen.Library,
        is AppScreen.Playlists,
        is AppScreen.LikedSongs,
        is AppScreen.Artists,
        is AppScreen.PlaylistDetail,
        is AppScreen.ArtistDetail -> 3
        is AppScreen.Settings,
        is AppScreen.Compression,
        is AppScreen.Info -> 4
        is AppScreen.CutAndPaste -> 4
        // ActiveDownloads doesn't belong to a tab, usually sits on top of Search or Home?
        // Let's keep Search tab active if we are in ActiveDownloads for now
        is AppScreen.ActiveDownloads -> 1
        is AppScreen.YouTubePlaylistDetail -> 1
    }

    // -------------------------------------------------------------


    var showIdentifySheet by remember { mutableStateOf(false) }
var isPlayerExpanded by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val showAdPopupEvent by viewModel.showAdPopupEvent.collectAsState()
    val activity = LocalContext.current as? Activity

    // Bottom Sheet Player State
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }

    // Time-based Ad Logic
    var appUsageMinutes by remember { mutableStateOf(0) }
    var showTimeBasedAd by remember { mutableStateOf<String?>(null) }
    var timeBasedAdLinkToOpen by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(60_000)
            appUsageMinutes++
            if (com.example.musicdownloader.utils.AdManager.isAdsEnabled(context)) {
                val threshold = com.example.musicdownloader.utils.AdManager.getTimeThresholdMins(context)
                if (appUsageMinutes >= threshold) {
                    appUsageMinutes = 0
                    com.example.musicdownloader.utils.AdManager.getRandomAdLink(context)?.let { link ->
                        showTimeBasedAd = link
                    }
                }
            }
        }
    }
    val scope = rememberCoroutineScope()

    // Intercept Back Button
    BackHandler(enabled = true) {
        if (popBackStack()) {
            return@BackHandler
        }
        activity?.finish()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(viewModel.toastEvent) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.downloadMessage) {
        uiState.downloadMessage?.let {
             if (it.startsWith("Downloaded") || it.startsWith("Failed")) {
                 Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                 viewModel.clearDownloadMessage()
             }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                val shouldShowMiniPlayer = currentScreen !is AppScreen.Roulette && currentScreen !is AppScreen.CutAndPaste
                if (currentMediaItem != null && shouldShowMiniPlayer) {
                    MiniPlayer(
                        viewModel = viewModel,
                        onClick = { isPlayerExpanded = true }
                    )
                }

                NavigationBar(
                    containerColor = DeepBlue
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PremiumGold,
                        selectedTextColor = PremiumGold,
                        indicatorColor = DTechBlue.copy(alpha = 0.2f),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )

                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Home)
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Search())
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = {
                            showIdentifySheet = true
                        },
                        icon = { Icon(Icons.Default.Info, contentDescription = "Identify") },
                        label = { Text("Identify") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Library)
                        },
                        icon = { Icon(Icons.Default.List, contentDescription = "Library") },
                        label = { Text("Library") },
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Settings)
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = navColors
                    )
                }
            }
        }
    ) { paddingValues ->
        // Animated Content Switcher
        // Using explicit `with` from androidx.compose.animation for safety
        AnimatedContent(
            targetState = currentScreen,
            label = "ScreenTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            transitionSpec = {
                 (fadeIn(animationSpec = tween(300)) +
                  slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it / 4 }))
                 .with(fadeOut(animationSpec = tween(300)))
            }
        ) { targetScreen ->
             Box(modifier = Modifier.fillMaxSize()) {
                when (targetScreen) {
                    is AppScreen.Home -> HomeScreen(
                        viewModel = viewModel,
                        onSongClick = { /* handled locally */ }
                    )
                    is AppScreen.Search -> SearchScreen(
                        viewModel = viewModel,
                        contentPadding = PaddingValues(0.dp),
                        initialQuery = targetScreen.query,
                        onViewDownloads = { navigateTo(AppScreen.ActiveDownloads) },
                        onPlaylistClick = { id, name -> navigateTo(AppScreen.YouTubePlaylistDetail(id, name)) }
                    )
                    is AppScreen.ActiveDownloads -> ActiveDownloadsScreen(
                        activeDownloads = activeDownloads,
                        onBack = { popBackStack() },
                        onPause = { viewModel.pauseDownload(it) },
                        onResume = { viewModel.resumeDownload(it) },
                        onDelete = { viewModel.deleteDownload(it) }
                    )
                                                            is AppScreen.CutAndPaste -> com.example.musicdownloader.ui.CutAndPasteScreen(
                        viewModel = viewModel,
                        onBack = { popBackStack() }
                    )
                    is AppScreen.Roulette -> com.example.musicdownloader.ui.MusicDiscoveryRouletteScreen(viewModel)
                    is AppScreen.Identify -> IdentifyScreen(
                        onSongFound = { songName ->
                            // Navigate to search with the found name
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Search(query = songName))
                        }
                    )
                    is AppScreen.Settings -> SettingsScreen(
                        onShowLogs = { showLogs = true },
                        onNavigateToInfo = { navigateTo(AppScreen.Info) },
                        onNavigateToCompression = { navigateTo(AppScreen.Compression) },
                        onNavigateToCutAndPaste = { navigationStack.add(AppScreen.CutAndPaste) },
                        contentPadding = PaddingValues(0.dp)
                    )
                    is AppScreen.Info -> InfoScreen(
                        onBack = { popBackStack() }
                    )
                    is AppScreen.Compression -> CompressionScreen(
                        viewModel = viewModel,
                        onBack = { popBackStack() }
                    )

                    is AppScreen.Library -> LibraryScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onNavigateToPlaylists = { navigateTo(AppScreen.Playlists) },
                        onNavigateToLiked = { navigateTo(AppScreen.LikedSongs) },
                        onNavigateToArtists = { navigateTo(AppScreen.Artists) }
                    )
                    is AppScreen.Playlists -> PlaylistScreen(
                        viewModel = viewModel,
                        onBack = { popBackStack() },
                        onPlaylistClick = { playlist -> navigateTo(AppScreen.PlaylistDetail(playlist.id, playlist.name)) }
                    )
                    is AppScreen.LikedSongs -> LikedSongsScreen(
                        viewModel = viewModel,
                        onBack = { popBackStack() },
                        onSongClick = { id -> viewModel.playLocalSong(id, "Unknown", "Unknown", "") }
                    )
                    is AppScreen.Artists -> ArtistsScreen(
                        viewModel = viewModel,
                        onNavigateToArtist = { name -> navigateTo(AppScreen.ArtistDetail(name)) },
                        onBack = { popBackStack() }
                    )


                    is AppScreen.YouTubePlaylistDetail -> com.example.musicdownloader.ui.YouTubePlaylistScreen(
                        playlistId = targetScreen.playlistId,
                        playlistName = targetScreen.playlistName,
                        viewModel = viewModel,
                        contentPadding = PaddingValues(bottom = 80.dp),
                        onBack = { popBackStack() }
                    )

                    is AppScreen.ArtistDetail -> ArtistDetailScreen(
                        artistName = targetScreen.name,
                        viewModel = viewModel,
                        onBack = { popBackStack() }
                    )
                    is AppScreen.PlaylistDetail -> PlaylistDetailScreen(
                        viewModel = viewModel,
                        playlistId = targetScreen.id,
                        playlistName = targetScreen.name,
                        onBack = { popBackStack() }
                    )
                }
             }
        }
    }


    if (showIdentifySheet) {
        com.example.musicdownloader.ui.IdentifyBottomSheet(
            onDismiss = { showIdentifySheet = false },
            onSelectShazam = {
                showIdentifySheet = false
                navigationStack.clear()
                navigationStack.add(AppScreen.Identify)
            },
            onSelectRoulette = {
                showIdentifySheet = false
                navigationStack.clear()
                navigationStack.add(AppScreen.Roulette)
            }
        )
    }
    // --- Ad Overlays ---
    if (showAdPopupEvent != null) {
        AdPopup(
            url = showAdPopupEvent!!,
            onDismiss = { viewModel.dismissAdPopup() },
            autoCloseSeconds = 20
        )
    }

    if (timeBasedAdLinkToOpen != null) {
        AdPopup(
            url = timeBasedAdLinkToOpen!!,
            onDismiss = { timeBasedAdLinkToOpen = null },
            autoCloseSeconds = null
        )
    }

    if (showTimeBasedAd != null) {
        // Show "Support Us" Popup instead of jumping straight to webview
        AlertDialog(
            onDismissRequest = { showTimeBasedAd = null },
            title = { Text("Support Us", color = Color(0xFF00A6FF)) },
            text = { Text("Click here to support us and keep the app here!", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    timeBasedAdLinkToOpen = showTimeBasedAd
                    showTimeBasedAd = null
                }) {
                    Text("Support Us", color = Color.Green)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeBasedAd = null }) {
                    Text("No Thanks", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E2A)
        )
    }

    if (isPlayerExpanded) {
        ModalBottomSheet(
            onDismissRequest = { isPlayerExpanded = false },
            sheetState = sheetState,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            dragHandle = null
        ) {
            FullScreenPlayer(
                viewModel = viewModel,
                onCollapse = { isPlayerExpanded = false }
            )
        }
    }

    if (showLogs) {
        LogConsoleOverlay(onClose = { showLogs = false })
    }
}
