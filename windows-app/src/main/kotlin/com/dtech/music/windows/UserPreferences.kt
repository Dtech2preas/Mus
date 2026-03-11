package com.dtech.music.windows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

class UserPreferences {

    private val prefs = Preferences.userRoot().node("com.dtech.music.windows.prefs")

    private val _highEndMode = MutableStateFlow(prefs.getBoolean("highEndMode", false))
    val highEndMode: StateFlow<Boolean> = _highEndMode.asStateFlow()

    private val _smartShuffleBuffer = MutableStateFlow(prefs.getInt("smartShuffleBuffer", 5))
    val smartShuffleBuffer: StateFlow<Int> = _smartShuffleBuffer.asStateFlow()

    private val _favoriteGenres = MutableStateFlow(prefs.get("favoriteGenres", "").split(",").filter { it.isNotBlank() })
    val favoriteGenres: StateFlow<List<String>> = _favoriteGenres.asStateFlow()

    private val _firstTimeSetupComplete = MutableStateFlow(prefs.getBoolean("firstTimeSetupComplete", false))
    val firstTimeSetupComplete: StateFlow<Boolean> = _firstTimeSetupComplete.asStateFlow()

    fun setHighEndMode(enabled: Boolean) {
        prefs.putBoolean("highEndMode", enabled)
        _highEndMode.value = enabled
    }

    fun setSmartShuffleBuffer(buffer: Int) {
        prefs.putInt("smartShuffleBuffer", buffer)
        _smartShuffleBuffer.value = buffer
    }

    fun setFavoriteGenres(genres: List<String>) {
        val joined = genres.joinToString(",")
        prefs.put("favoriteGenres", joined)
        _favoriteGenres.value = genres
    }

    fun setFirstTimeSetupComplete(complete: Boolean) {
        prefs.putBoolean("firstTimeSetupComplete", complete)
        _firstTimeSetupComplete.value = complete
    }
}
