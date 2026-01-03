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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.ui.*
import com.example.musicdownloader.ui.FullScreenPlayer
import com.example.musicdownloader.utils.AdManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicAppTheme {
                RequestNotificationPermission()
                AppNavigation(viewModel)
            }
        }
    }
}

@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (!isGranted) {
                    // User denied permission, maybe show a rationale or just proceed
                }
            }
        )

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(permission)
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MusicViewModel) {
    val context = LocalContext.current
    var isFirstRun by remember { mutableStateOf(UserPreferences.isFirstRun(context)) }

    if (isFirstRun) {
        GenreSelectionScreen(
            onDone = { genres ->
                UserPreferences.saveGenres(context, genres)
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

    val currentTab = when (currentScreen) {
        is AppScreen.Home -> 0
        is AppScreen.Search -> 1
        is AppScreen.Library,
        is AppScreen.Playlists,
        is AppScreen.LikedSongs,
        is AppScreen.Artists,
        is AppScreen.PlaylistDetail,
        is AppScreen.ArtistDetail -> 2
        is AppScreen.Settings -> 3
    }

    // -------------------------------------------------------------

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = LocalContext.current as? Activity

    // Bottom Sheet Player State
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Ad System Integration ---
    var showAdDialog by remember { mutableStateOf(false) }
    var adDialogMessage by remember { mutableStateOf("Please watch a short ad to keep this app free.") }
    var hasShownExitAd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AdManager.checkSmartTrigger(context)
        AdManager.showAdDialogEvent.collect {
            adDialogMessage = "Please watch a short ad to keep this app free."
            showAdDialog = true
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var activeTimerJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                activeTimerJob?.cancel()
                activeTimerJob = scope.launch {
                    delay(30 * 60 * 1000L)
                    AdManager.showRandomAd(context, thresholdMs = 5000L)
                }
                if (AdManager.lastAdClickTime > 0 && AdManager.shouldCheckDuration) {
                    val diff = System.currentTimeMillis() - AdManager.lastAdClickTime
                    if (diff < AdManager.currentAdThresholdMs) {
                        val seconds = AdManager.currentAdThresholdMs / 1000
                        adDialogMessage = "Please view the ad for at least $seconds seconds before closing."
                        showAdDialog = true
                    }
                    AdManager.lastAdClickTime = 0
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                activeTimerJob?.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeTimerJob?.cancel()
        }
    }

    // Intercept Back Button
    BackHandler(enabled = true) {
        if (popBackStack()) {
            return@BackHandler
        }
        if (!hasShownExitAd) {
            hasShownExitAd = true
            AdManager.showRandomAd(context, thresholdMs = 0L, checkDuration = false)
        } else {
            activity?.finish()
        }
    }

    if (showAdDialog) {
        AlertDialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text("Support D-TECH") },
            text = { Text(adDialogMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showAdDialog = false
                        AdManager.showRandomAd(context, thresholdMs = AdManager.currentAdThresholdMs, checkDuration = true)
                    }
                ) {
                    Text("Support")
                }
            }
        )
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
                if (currentMediaItem != null) {
                    MiniPlayer(
                        viewModel = viewModel,
                        onClick = { isPlayerExpanded = true }
                    )
                }

                NavigationBar(
                    containerColor = DeepBlue
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Home)
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Search)
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Library)
                        },
                        icon = { Icon(Icons.Default.List, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = {
                            navigationStack.clear()
                            navigationStack.add(AppScreen.Settings)
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
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
                    is AppScreen.Search -> SearchScreen(viewModel = viewModel, contentPadding = PaddingValues(0.dp))
                    is AppScreen.Settings -> SettingsScreen(onShowLogs = { showLogs = true }, contentPadding = PaddingValues(0.dp))

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
