package com.example.musicdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    // Library Flow
    val librarySongs: StateFlow<List<Song>> = AppDatabase.getDatabase(application).songDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize the controller connection
        MusicControllerManager.initialize(application)

        // Sync files on startup
        viewModelScope.launch {
            MusicRepository.syncFilesWithDatabase(application)
        }

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

    fun downloadAndPlay(video: VideoItem) {
        AppLogger.log("[ViewModel] downloadAndPlay called for ${video.id}")

        _uiState.value = _uiState.value.copy(downloadMessage = "Downloading ${video.title}...")

        viewModelScope.launch {
             // Enqueue download via WorkManager
             // Note: This just starts the download.
             // Ideally we would play immediately if possible, but now we are async.
             val result = MusicRepository.downloadSong(getApplication(), video)

             result.onSuccess { msg ->
                 _uiState.value = _uiState.value.copy(downloadMessage = msg)

                 // If file already exists, play it now
                 if (msg == "File already exists") {
                    playLocalSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                 }
                 // If queued, user will see it in Library when done.
                 // We could listen to WorkManager to auto-play, but simple is better for now.
             }
        }
    }

    fun playLocalSong(id: String, title: String, artist: String, thumbnailUrl: String) {
         val file = File(getApplication<Application>().filesDir, "music_downloads/$id") // Fallback assumption
         // Ideally we get path from DB, but ID mapping is reliable.

         // We might need to handle the case where the file extension is different?
         // Our repo logic just checks startsWith(id).
         val outputDir = File(getApplication<Application>().filesDir, "music_downloads")
         val existingFiles = outputDir.listFiles { _, name -> name.startsWith(id) }

         val targetFile = existingFiles?.firstOrNull() ?: file // fallback

         val mediaMetadata = MediaMetadata.Builder()
             .setTitle(title)
             .setArtist(artist)
             .setArtworkUri(android.net.Uri.parse(thumbnailUrl))
             .build()

         val mediaItem = MediaItem.Builder()
             .setUri(android.net.Uri.fromFile(targetFile))
             .setMediaId(id)
             .setMediaMetadata(mediaMetadata)
             .build()

         MusicControllerManager.playMedia(mediaItem)
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
}
