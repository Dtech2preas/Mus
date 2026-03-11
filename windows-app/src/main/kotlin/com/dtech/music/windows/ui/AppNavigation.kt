package com.dtech.music.windows.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

enum class Screen {
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS,
    GENRE_SELECTION,
    IDENTIFY
}

class NavController {
    var currentScreen by mutableStateOf(Screen.HOME)
    private val backStack = mutableListOf<Screen>()

    fun navigate(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun popBackStack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeLast()
        }
    }
}
