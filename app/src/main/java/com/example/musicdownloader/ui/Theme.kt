package com.example.musicdownloader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeepBlue = Color(0xFF0F0F13)
val SurfaceBlue = Color(0xFF1C1C26)
val ElectricPurple = Color(0xFF7D5FFF)
val CyanAccent = Color(0xFF00E5FF)
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFFB0B0B0)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    secondary = CyanAccent,
    background = DeepBlue,
    surface = SurfaceBlue,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = TextWhite,
    onSurface = TextWhite,
    surfaceVariant = SurfaceBlue, // For cards/miniplayer
    onSurfaceVariant = TextWhite
)

// We force dark theme as requested ("Modern Dark Theme")
@Composable
fun MusicAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
