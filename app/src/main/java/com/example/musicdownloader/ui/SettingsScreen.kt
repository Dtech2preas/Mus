package com.example.musicdownloader.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.musicdownloader.CookieManager

@Composable
fun SettingsScreen(
    onShowLogs: () -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    var showCookieDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Help Section
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "How to Use",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Swipe Right: Add song to Queue", style = MaterialTheme.typography.bodyMedium)
                Text("• Trash Icon: Delete song permanently", style = MaterialTheme.typography.bodyMedium)
                Text("• Background: Downloads continue even if you close the app.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Logs
        Button(
            onClick = onShowLogs,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show Debug Logs")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cookies
        Button(
            onClick = { showCookieDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set YouTube Cookies")
        }

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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "App Version: 1.0 (Debug)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
