package com.example.musicdownloader

import android.Manifest
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.musicdownloader.ui.GenreSelectionScreen
import com.example.musicdownloader.ui.HomeScreen
import com.example.musicdownloader.ui.LibraryScreen
import com.example.musicdownloader.ui.LikedSongsScreen
import com.example.musicdownloader.ui.MusicAppTheme
import com.example.musicdownloader.ui.PlaylistDetailScreen
import com.example.musicdownloader.ui.PlaylistScreen
import com.example.musicdownloader.ui.ArtistsScreen
import com.example.musicdownloader.ui.ArtistDetailScreen
import com.example.musicdownloader.ui.SearchScreen
import com.example.musicdownloader.ui.SettingsScreen
import com.example.musicdownloader.ui.DeepBlue
import com.example.musicdownloader.utils.AdManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.Activity
import com.example.musicdownloader.AppLogger
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context

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

enum class MainTab {
    Home, Search, Library, Settings
}

// Sub-navigation for Library
sealed class LibraryRoute {
    object Main : LibraryRoute()
    object Playlists : LibraryRoute()
    object LikedSongs : LibraryRoute()
    object Artists : LibraryRoute()
    data class PlaylistDetail(val id: Int, val name: String) : LibraryRoute()
    data class ArtistDetail(val name: String) : LibraryRoute()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    var currentTab by remember { mutableStateOf(MainTab.Home) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Bottom Sheet Player State
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Library Navigation State
    var libraryRoute by remember { mutableStateOf<LibraryRoute>(LibraryRoute.Main) }

    // --- Monetag 2.0 Logic ---

    // 1. Exit Ad Logic
    var hasShownExitAd by remember { mutableStateOf(false) }

    // Logic: Only intercept back if we are at the "root" of navigation (Home, or other tabs base state)
    // AND if we are not in a sub-route of Library.
    // If Library is in sub-route, LibraryScreen handles back (implicitly or explicitly).
    // But since we use a variable 'libraryRoute', 'Back' should conceptually go up the stack.
    // We need to handle that first.

    // We want to handle "App Exit" only when there's nowhere else to go.
    // So if currentTab is Home, Back -> Exit Ad logic.
    // If currentTab is Library, Back -> Go to Main Library -> Go to Home -> Exit.

    // Let's implement a unified Back Handler for the MainScreen structure.
    BackHandler(enabled = true) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
            return@BackHandler
        }

        if (showLogs) {
            showLogs = false
            return@BackHandler
        }

        if (currentTab == MainTab.Library && libraryRoute !is LibraryRoute.Main) {
             // Handle Library Back Navigation
             when (libraryRoute) {
                 is LibraryRoute.PlaylistDetail -> libraryRoute = LibraryRoute.Playlists
                 is LibraryRoute.ArtistDetail -> libraryRoute = LibraryRoute.Artists
                 else -> libraryRoute = LibraryRoute.Main
             }
             return@BackHandler
        }

        if (currentTab != MainTab.Home) {
            // Go back to Home first
            currentTab = MainTab.Home
            return@BackHandler
        }

        // We are at Home. Trigger Exit Ad Logic.
        if (!hasShownExitAd && isOnline(context)) {
            hasShownExitAd = true
            AdManager.openRandomAd(context)
            // After showing ad, user stays on Home. Next back press closes app.
        } else {
            activity?.finish()
        }
    }

    // 2. 30-Minute Active Timer
    DisposableEffect(lifecycleOwner) {
        var timerJob: kotlinx.coroutines.Job? = null

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Start Timer
                timerJob = scope.launch {
                    AppLogger.log("[AdTimer] Starting 30-minute timer")
                    delay(30 * 60 * 1000L) // 30 minutes
                    if (isOnline(context)) {
                        AppLogger.log("[AdTimer] Timer finished. Opening Ad.")
                        AdManager.openRandomAd(context)
                    }
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                // Cancel Timer
                AppLogger.log("[AdTimer] App paused. Cancelling timer.")
                timerJob?.cancel()
                timerJob = null
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            timerJob?.cancel()
        }
    }

    // -------------------------

    // Error/Message Toasts
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Single Toast Event Channel
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
                // MiniPlayer sits exactly on top of the BottomBar if a song is playing
                if (currentMediaItem != null) {
                    MiniPlayer(
                        viewModel = viewModel,
                        onClick = { isPlayerExpanded = true }
                    )
                }

                NavigationBar(
                    containerColor = DeepBlue // Match theme
                ) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.Home,
                        onClick = { currentTab = MainTab.Home },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.Search,
                        onClick = { currentTab = MainTab.Search },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.Library,
                        onClick = {
                            if (currentTab == MainTab.Library) {
                                // Reset library stack if tapped again
                                libraryRoute = LibraryRoute.Main
                            } else {
                                currentTab = MainTab.Library
                            }
                        },
                        icon = { Icon(Icons.Default.List, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.Settings,
                        onClick = { currentTab = MainTab.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { paddingValues ->
        // Content Area
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            when (currentTab) {
                MainTab.Home -> HomeScreen(
                    viewModel = viewModel,
                    onSongClick = { id ->
                         // Handled in HomeScreen now via playLocalSong/downloadAndPlay
                    }
                )
                MainTab.Search -> SearchScreen(viewModel = viewModel, contentPadding = PaddingValues(0.dp))
                MainTab.Library -> {
                    // Nested Library Navigation
                    when (val route = libraryRoute) {
                        LibraryRoute.Main -> LibraryScreen(
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState,
                            onNavigateToPlaylists = { libraryRoute = LibraryRoute.Playlists },
                            onNavigateToLiked = { libraryRoute = LibraryRoute.LikedSongs },
                            onNavigateToArtists = { libraryRoute = LibraryRoute.Artists }
                        )
                        LibraryRoute.Playlists -> PlaylistScreen(
                            viewModel = viewModel,
                            onBack = { libraryRoute = LibraryRoute.Main },
                            onPlaylistClick = { playlist -> libraryRoute = LibraryRoute.PlaylistDetail(playlist.id, playlist.name) }
                        )
                        LibraryRoute.LikedSongs -> LikedSongsScreen(
                            viewModel = viewModel,
                            onBack = { libraryRoute = LibraryRoute.Main },
                            onSongClick = { id -> viewModel.playLocalSong(id, "Unknown", "Unknown", "") } // Re-fetch info or just play
                        )
                        LibraryRoute.Artists -> ArtistsScreen(
                            viewModel = viewModel,
                            onNavigateToArtist = { name -> libraryRoute = LibraryRoute.ArtistDetail(name) },
                            onBack = { libraryRoute = LibraryRoute.Main }
                        )
                        is LibraryRoute.ArtistDetail -> ArtistDetailScreen(
                            artistName = route.name,
                            viewModel = viewModel,
                            onBack = { libraryRoute = LibraryRoute.Artists }
                        )
                        is LibraryRoute.PlaylistDetail -> PlaylistDetailScreen(
                            viewModel = viewModel,
                            playlistId = route.id,
                            playlistName = route.name,
                            onBack = { libraryRoute = LibraryRoute.Playlists }
                        )
                    }
                }
                MainTab.Settings -> SettingsScreen(onShowLogs = { showLogs = true }, contentPadding = PaddingValues(0.dp))
            }
        }
    }

    // Full Screen Player Sheet
    if (isPlayerExpanded) {
        ModalBottomSheet(
            onDismissRequest = { isPlayerExpanded = false },
            sheetState = sheetState,
            containerColor = androidx.compose.ui.graphics.Color.Transparent, // Let Player handle bg
            dragHandle = null // Custom handle or none
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

// Duplicate helper here because MainActivity can't see AdManager's private one,
// and AdManager's is private. Or we can use AdManager's if we make it public?
// AdManager.kt is in a different package (utils).
// I'll just use a local helper or make AdManager's public.
// I made AdManager's isOnline private in previous step.
// I'll just implement it locally for MainScreen logic or assume AdManager handles it?
// The requirement: "Exit Ad... IF !hasShownExitAd AND isOnline...".
// So MainScreen needs to know if online.
// I will replicate the check here to avoid changing AdManager again or make it public.

fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
