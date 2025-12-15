package com.example.musicdownloader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(viewModel: MusicViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Calculate padding for list based on whether mini player is visible
        val bottomPadding = if (currentMediaItem != null) 80.dp else 0.dp

        MusicDownloaderScreen(
            viewModel = viewModel,
            contentPadding = PaddingValues(bottom = bottomPadding),
            onShowLogs = { showLogs = true }
        )

        // Mini Player anchored to bottom
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            MiniPlayer(
                viewModel = viewModel,
                onClick = { isPlayerExpanded = true }
            )
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
        // Log Console Overlay (z-index top)
        LogConsoleOverlay(onClose = { showLogs = false })
    }
}
