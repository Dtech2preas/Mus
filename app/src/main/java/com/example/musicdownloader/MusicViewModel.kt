package com.example.musicdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class MusicUiState(
    val results: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingPlayer: Boolean = false,
    val errorMessage: String? = null,
    val downloadMessage: String? = null
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    // Expose Player State from Manager
    val isPlaying = MusicControllerManager.isPlaying
    val currentMediaItem = MusicControllerManager.currentMediaItem
    val currentPosition = MusicControllerManager.currentPosition
    val duration = MusicControllerManager.duration

    init {
        // Initialize the controller connection
        MusicControllerManager.initialize(application)

        // Polling loop for position updates
        viewModelScope.launch {
            while (isActive) {
                if (isPlaying.value) {
                    MusicControllerManager.updatePosition()
                }
                delay(1000)
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = MusicRepository.searchVideos(getApplication(), query)
            result.onSuccess { videos ->
                _uiState.value = _uiState.value.copy(
                    results = videos,
                    isLoading = false
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown search error"
                )
            }
        }
    }

    fun play(video: VideoItem) {
        AppLogger.log("[ViewModel] play called for ${video.id}")
        // Set loading state to true. We can use downloadMessage to show progress to user.
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            isLoadingPlayer = true,
            downloadMessage = "Preparing ${video.title}..."
        )

        viewModelScope.launch {
             // New flow: Download -> Play
             val result = MusicRepository.downloadAndPlay(getApplication(), video)

             _uiState.value = _uiState.value.copy(
                 isLoadingPlayer = false,
                 downloadMessage = null
             )

             result.onSuccess { file ->
                 AppLogger.log("[ViewModel] File ready: ${file.absolutePath}")

                 val mediaMetadata = MediaMetadata.Builder()
                     .setTitle(video.title)
                     .setArtist(video.uploader)
                     .setArtworkUri(android.net.Uri.parse(video.thumbnailUrl))
                     .build()

                 // Play from local file
                 val mediaItem = MediaItem.Builder()
                     .setUri(android.net.Uri.fromFile(file))
                     .setMediaId(video.id)
                     .setMediaMetadata(mediaMetadata)
                     .build()

                 MusicControllerManager.playMedia(mediaItem)
             }.onFailure { e ->
                 AppLogger.log("[ViewModel] Failed to download/play: ${e.message}")
                 _uiState.value = _uiState.value.copy(
                     errorMessage = "Failed to play: ${e.message}"
                 )
             }
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            MusicControllerManager.pause()
        } else {
            MusicControllerManager.play()
        }
    }

    fun seekTo(position: Long) {
        MusicControllerManager.seekTo(position)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearDownloadMessage() {
        _uiState.value = _uiState.value.copy(downloadMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        // We typically don't release the controller here because the service might still be running
        // and we want to reconnect if the user comes back.
        // But for strict cleanup, we could.
    }
}
