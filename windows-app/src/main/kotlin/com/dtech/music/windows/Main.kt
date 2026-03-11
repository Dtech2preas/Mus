package com.dtech.music.windows

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dtech.music.windows.data.DatabaseInitializer
import com.dtech.music.windows.ui.MainScreen
import com.dtech.music.windows.workers.StreamRefresherBackground

val TechBlack = Color(0xFF121212)
val NeonBlue = Color(0xFF00A6FF)

fun main() {
    System.setProperty("skiko.renderApi", "SOFTWARE")

    // Initialize standard logic
    DatabaseInitializer.init()
    CookieManager.checkAndLogCookies()

    val preferences = UserPreferences()
    val repository = MusicRepository(preferences)
    val player = DesktopMusicPlayer()
    val refresher = StreamRefresherBackground(repository)

    refresher.start()

    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

        // Setup Window
        Window(
            onCloseRequest = {
                player.release()
                refresher.stop()
                exitApplication()
            },
            title = "DTECH MUSIC // PREASX24",
            state = windowState
        ) {
            MaterialTheme(
                colors = darkColors(
                    primary = NeonBlue,
                    background = TechBlack,
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                MainScreen(repository = repository, player = player, preferences = preferences)
            }
        }
    }
}
