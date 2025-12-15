package com.example.musicdownloader

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogConsoleScreen(
    onDismiss: () -> Unit
) {
    val logs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when logs update
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Console") },
                actions = {
                    IconButton(onClick = {
                        val file = AppLogger.export(context)
                        if (file != null) {
                            Toast.makeText(context, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                            // Copy path to clipboard
                            clipboardManager.setText(AnnotatedString(file.absolutePath))
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Export")
                    }
                    IconButton(onClick = onDismiss) {
                        Text("X")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            state = listState,
            contentPadding = PaddingValues(8.dp)
        ) {
            items(logs) { log ->
                Text(
                    text = log.formatted(),
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
