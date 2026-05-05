package com.example.musicdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.musicdownloader.MusicViewModel
import com.example.musicdownloader.utils.PermissionUtils
import kotlin.random.Random
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubePlaylistScreen(
    playlistId: String,
    playlistName: String,
    viewModel: MusicViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val initializingDownloads by viewModel.initializingDownloads.collectAsStateWithLifecycle()
    val isExternalMode by viewModel.externalDownloadMode.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val downloadedIds = remember(librarySongs) { librarySongs.map { it.id }.toSet() }

    val adUrls = listOf(
        "https://omg10.com/4/10250311",
        "https://omg10.com/4/10358600",
        "https://omg10.com/4/9515888",
        "https://omg10.com/4/10205357"
    )

    var pendingDownloadVideo by remember { mutableStateOf<com.example.musicdownloader.VideoItem?>(null) }
    var pendingDownloadAllVideos by remember { mutableStateOf<List<com.example.musicdownloader.VideoItem>?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pendingDownloadVideo?.let {
                viewModel.downloadSongExternally(it)
                pendingDownloadVideo = null
            }
            pendingDownloadAllVideos?.let {
                viewModel.downloadAllExternally(it)
                pendingDownloadAllVideos = null
            }
        } else {
            Toast.makeText(context, "Storage permission is required to save to device", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAdAndDownload(video: com.example.musicdownloader.VideoItem) {
        val url = adUrls[Random.nextInt(adUrls.size)]
        val customTabsIntent = CustomTabsIntent.Builder().build()
        try {
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
        if (PermissionUtils.hasStoragePermission(context)) {
            viewModel.downloadSongExternally(video)
        } else {
            pendingDownloadVideo = video
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun openAdAndDownloadAll(videos: List<com.example.musicdownloader.VideoItem>) {
        val url = adUrls[Random.nextInt(adUrls.size)]
        val customTabsIntent = CustomTabsIntent.Builder().build()
        try {
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
        if (PermissionUtils.hasStoragePermission(context)) {
            viewModel.downloadAllExternally(videos)
        } else {
            pendingDownloadAllVideos = videos
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // Load videos when screen opens
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylistVideos(playlistId)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F13))) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // App Bar
            TopAppBar(
                title = {
                    Text(
                        text = playlistName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // Content
            if (uiState.isLoading && uiState.playlistVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricPurple)
                }
            } else if (uiState.errorMessage != null && uiState.playlistVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${uiState.errorMessage}", color = Color.Red)
                }
            } else {
                // Header Options
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.playlistVideos.size} songs",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )

                    if (uiState.playlistVideos.isNotEmpty()) {
                        if (isExternalMode) {
                            Button(
                                onClick = {
                                    openAdAndDownloadAll(uiState.playlistVideos)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = "Download All to Device", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download All to Device")
                            }
                        } else {
                            Row {
                                Button(
                                    onClick = {
                                        viewModel.addAllToLibrary(uiState.playlistVideos, playlistName)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2A)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add All", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add All")
                                }

                                Button(
                                    onClick = {
                                        viewModel.downloadAll(uiState.playlistVideos, playlistName)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Download All", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))

                // List
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.playlistVideos) { video ->
                        MusicRowItem(
                            title = video.title,
                            subtitle = video.uploader,
                            thumbnailUrl = video.thumbnailUrl,
                            isLibrary = false,
                            isDownloaded = downloadedIds.contains(video.id),
                            downloadProgress = downloadProgress[video.id]?.progress,
                            isWaiting = initializingDownloads.contains(video.id),
                            isCached = false,
                            onClick = {
                                if (downloadedIds.contains(video.id)) {
                                    viewModel.playSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                                } else {
                                    viewModel.playStream(video)
                                }
                            },
                            onDownloadClick = { viewModel.downloadSong(video) },
                            showAddButton = !isExternalMode,
                            onAddClick = { viewModel.addToLibraryOnly(video) },
                            showDownloadButton = !isExternalMode,
                            showExternalDownloadButton = isExternalMode,
                            onExternalDownloadClick = { openAdAndDownload(video) }
                        )
                    }
                }
            }
        }
    }
}
