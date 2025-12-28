package com.example.musicdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.ui.GenreSelectionScreen
import com.example.musicdownloader.ui.HomeScreen
import com.example.musicdownloader.ui.LibraryScreen
import com.example.musicdownloader.ui.LikedSongsScreen
import com.example.musicdownloader.ui.MusicAppTheme
import com.example.musicdownloader.ui.PlaylistDetailScreen
import com.example.musicdownloader.ui.PlaylistScreen
import com.example.musicdownloader.ui.SearchScreen
import com.example.musicdownloader.ui.SettingsScreen
import com.example.musicdownloader.ui.DeepBlue
import com.example.musicdownloader.utils.AdManager
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

enum class MainTab {
    Home, Search, Library, Settings
}

// Sub-navigation for Library
sealed class LibraryRoute {
    object Main : LibraryRoute()
    object Playlists : LibraryRoute()
    object LikedSongs : LibraryRoute()
    data class PlaylistDetail(val id: Int, val name: String) : LibraryRoute()
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

    // Bottom Sheet Player State
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Library Navigation State
    var libraryRoute by remember { mutableStateOf<LibraryRoute>(LibraryRoute.Main) }

    // --- Ad System Integration ---
    var showAdDialog by remember { mutableStateOf(false) }
    var adDialogMessage by remember { mutableStateOf("Please watch a short ad to keep this app free.") }

    LaunchedEffect(Unit) {
        // Check Trigger on App Start
        AdManager.checkSmartTrigger(context)

        // Observe Ad Dialog Requests
        AdManager.showAdDialogEvent.collect {
            adDialogMessage = "Please watch a short ad to keep this app free."
            showAdDialog = true
        }
    }

    // Lifecycle Observer for Ad Timer Logic
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (AdManager.lastAdClickTime > 0) {
                    val diff = System.currentTimeMillis() - AdManager.lastAdClickTime
                    if (diff < 7000) {
                        // User returned too quickly (< 7 seconds)
                        adDialogMessage = "Please view the ad for at least 7 seconds before closing."
                        showAdDialog = true
                    }
                    // Reset timer so next click is fresh
                    AdManager.lastAdClickTime = 0
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showAdDialog) {
        AlertDialog(
            onDismissRequest = { /* No-op to prevent dismissal */ },
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
                        AdManager.showRandomAd(context)
                    }
                ) {
                    Text("Support")
                }
            }
            // dismissedButton removed to force support
        )
    }
    // -----------------------------

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
                         // But if we need global click handling:
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
                            onNavigateToLiked = { libraryRoute = LibraryRoute.LikedSongs }
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
