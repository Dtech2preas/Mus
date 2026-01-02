package com.example.musicdownloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.musicdownloader.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

val DeepBlue = Color(0xFF0F0F13)
val SurfaceBlue = Color(0xFF1C1C26)
// Default Purple (will be overridden dynamically)
val ElectricPurple = Color(0xFF7D5FFF)
val CyanAccent = Color(0xFF00E5FF)
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFFB0B0B0)

// Helper to observe theme changes
object ThemeManager {
    private val _themeColor = MutableStateFlow(ElectricPurple)
    val themeColor = _themeColor.asStateFlow()

    fun updateTheme(color: Long) {
        _themeColor.value = Color(color)
    }
}

@Composable
fun MusicAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColor = ThemeManager.themeColor.collectAsState()

    LaunchedEffect(Unit) {
        // Load saved theme on startup
        val savedColor = UserPreferences.getThemeColor(context)
        ThemeManager.updateTheme(savedColor)
    }

    val colorScheme = remember(dynamicColor.value) {
        darkColorScheme(
            primary = dynamicColor.value,
            secondary = CyanAccent,
            background = DeepBlue,
            surface = SurfaceBlue,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = TextWhite,
            onSurface = TextWhite,
            surfaceVariant = SurfaceBlue,
            onSurfaceVariant = TextWhite
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
