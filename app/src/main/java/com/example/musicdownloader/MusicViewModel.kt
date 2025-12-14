package com.example.musicdownloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class MusicUiState(
    val results: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val downloadMessage: String? = null,
    val currentPlayingUrl: String? = null
)

class MusicViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = repository.searchVideos(query)
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

    fun download(video: VideoItem, outputDir: File) {
        _uiState.value = _uiState.value.copy(downloadMessage = "Downloading ${video.title}...")

        viewModelScope.launch {
            val result = repository.downloadAudio(video.webUrl, outputDir)
            result.onSuccess { file ->
                _uiState.value = _uiState.value.copy(
                    downloadMessage = "Downloaded: ${video.title}"
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    downloadMessage = "Failed: ${e.message}"
                )
            }
        }
    }

    fun play(video: VideoItem) {
        // Clear previous error
        _uiState.value = _uiState.value.copy(errorMessage = null)

        viewModelScope.launch {
             // For streaming, we need the direct URL
             val result = repository.getStreamUrl(video.webUrl)
             result.onSuccess { streamUrl ->
                 _uiState.value = _uiState.value.copy(currentPlayingUrl = streamUrl)
             }.onFailure { e ->
                 _uiState.value = _uiState.value.copy(
                     errorMessage = "Failed to play: ${e.message}"
                 )
             }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearDownloadMessage() {
        _uiState.value = _uiState.value.copy(downloadMessage = null)
    }
}

class MusicViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            val youtubeClient = YoutubeClient()
            val repository = MusicRepository(youtubeClient)
            return MusicViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
