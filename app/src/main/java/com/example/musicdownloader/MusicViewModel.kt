package com.example.musicdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
        AppLogger.log("ViewModel", "Initiating search for: $query")

        viewModelScope.launch {
            val result = MusicRepository.searchVideos(query)
            result.onSuccess { videos ->
                AppLogger.log("ViewModel", "Search successful, ${videos.size} results")
                _uiState.value = _uiState.value.copy(
                    results = videos,
                    isLoading = false
                )
            }.onFailure { e ->
                AppLogger.log("ViewModel", "Search failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unknown search error"
                )
            }
        }
    }

    fun download(video: VideoItem, outputDir: File) {
        _uiState.value = _uiState.value.copy(downloadMessage = "Downloading ${video.title}...")
        AppLogger.log("ViewModel", "Initiating download for: ${video.title}")

        viewModelScope.launch {
            val result = MusicRepository.downloadAudio(video.webUrl, outputDir)
            result.onSuccess { file ->
                AppLogger.log("ViewModel", "Download complete")
                _uiState.value = _uiState.value.copy(
                    downloadMessage = "Downloaded: ${video.title}"
                )
            }.onFailure { e ->
                AppLogger.log("ViewModel", "Download error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    downloadMessage = "Failed: ${e.message}"
                )
            }
        }
    }

    fun play(video: VideoItem) {
        _uiState.value = _uiState.value.copy(errorMessage = null, isLoadingPlayer = true)
        AppLogger.log("ViewModel", "Play requested for: ${video.title}")

        viewModelScope.launch {
             // For streaming, we need the direct URL
             val result = MusicRepository.getStreamUrl(video.webUrl)
             _uiState.value = _uiState.value.copy(isLoadingPlayer = false)

             result.onSuccess { streamUrl ->
                 AppLogger.log("ViewModel", "Stream URL obtained, preparing player")
                 val mediaMetadata = MediaMetadata.Builder()
                     .setTitle(video.title)
                     .setArtist(video.uploader)
                     .setArtworkUri(android.net.Uri.parse(video.thumbnailUrl))
                     .build()

                 val mediaItem = MediaItem.Builder()
                     .setUri(streamUrl)
                     .setMediaId(video.id)
                     .setMediaMetadata(mediaMetadata)
                     .build()

                 MusicControllerManager.playMedia(mediaItem)
             }.onFailure { e ->
                 AppLogger.log("ViewModel", "Failed to get stream URL: ${e.message}")
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
