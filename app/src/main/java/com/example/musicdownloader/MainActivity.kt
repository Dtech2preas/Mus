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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.musicdownloader.ui.MusicAppTheme
import com.example.musicdownloader.ui.SearchScreen
import com.example.musicdownloader.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicAppTheme {
                RequestNotificationPermission()
                MainScreen(viewModel)
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
    Search, Library, Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    var currentTab by remember { mutableStateOf(MainTab.Search) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Error/Message Toasts
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
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
        bottomBar = {
            Column {
                // MiniPlayer sits exactly on top of the BottomBar if a song is playing
                if (currentMediaItem != null) {
                    MiniPlayer(
                        viewModel = viewModel,
                        onClick = { isPlayerExpanded = true }
                    )
                }

                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == MainTab.Search,
                        onClick = { currentTab = MainTab.Search },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.Library,
                        onClick = { currentTab = MainTab.Library },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
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
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentTab) {
                MainTab.Search -> SearchScreen(viewModel = viewModel, contentPadding = paddingValues)
                MainTab.Library -> LibraryScreen(viewModel = viewModel, contentPadding = paddingValues)
                MainTab.Settings -> SettingsScreen(onShowLogs = { showLogs = true }, contentPadding = paddingValues)
            }
        }
    }

    // Full Screen Player Sheet
    if (isPlayerExpanded) {
        ModalBottomSheet(
            onDismissRequest = { isPlayerExpanded = false },
            sheetState = sheetState
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

// CookieDialog moved to ui/SettingsScreen.kt or kept here if needed for others.
// It is now used in SettingsScreen, so we can duplicate or make it public in a common place.
// Since it's small, I'll just leave the copy in SettingsScreen and remove it from here if no longer used.
// But wait, the original CookieDialog code was in MainActivity.kt.
// I should make sure it is accessible.
// I'll define it here as a public function if I need to share it, or better, keep it in SettingsScreen.
// I will keep a copy in SettingsScreen (already done) and remove it from here.

@Composable
fun CookieDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var cookieText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set YouTube Cookie") },
        text = {
            Column {
                Text("Paste your cookie string here to bypass 403 errors:")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = cookieText,
                    onValueChange = { cookieText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(cookieText) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
