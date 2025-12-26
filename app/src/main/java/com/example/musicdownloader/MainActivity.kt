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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.musicdownloader.ui.MusicRowItem

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RequestNotificationPermission()
                MusicPlayerScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
fun MusicDownloaderScreen(
    viewModel: MusicViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onShowLogs: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var isLibraryVisible by remember { mutableStateOf(false) } // State to toggle Search vs Library
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        // Top Bar: Search and Library Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isLibraryVisible) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Song") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.search(query) },
                    enabled = !uiState.isLoading
                ) {
                    Text("Search")
                }
            } else {
                Text(
                    text = "My Library",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Toggle Button
            Button(onClick = { isLibraryVisible = !isLibraryVisible }) {
                Text(if (isLibraryVisible) "Search" else "Lib")
            }
        }

        // Action Buttons (Logs / Cookies)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
             Button(onClick = { onShowLogs() }) { Text("Logs") }

             var showCookieDialog by remember { mutableStateOf(false) }
             Button(onClick = { showCookieDialog = true }) { Text("Cookies") }

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
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading || uiState.isLoadingPlayer) {
             Column(
                 modifier = Modifier.fillMaxSize(),
                 horizontalAlignment = Alignment.CenterHorizontally,
                 verticalArrangement = Arrangement.Center
             ) {
                CircularProgressIndicator()
                if (uiState.downloadMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(uiState.downloadMessage!!)
                }
            }
        } else {
            if (isLibraryVisible) {
                LibraryScreen(viewModel = viewModel, contentPadding = contentPadding)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = contentPadding
                ) {
                    items(uiState.results) { video ->
                        MusicRowItem(
                            title = video.title,
                            artist = video.uploader,
                            thumbnailUrl = video.thumbnailUrl,
                            durationOrStatus = video.duration,
                            onClick = { viewModel.downloadAndPlay(video) }
                        )
                    }
                }
            }
        }
    }
}

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
