package com.example.musicdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.PlayHistory
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption {
    NEWEST_FIRST,
    A_Z,
    Z_A
}

data class MusicUiState(
    val results: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingPlayer: Boolean = false,
    val errorMessage: String? = null,
    val downloadMessage: String? = null,
    val genreFeeds: List<GenreFeed> = emptyList()
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    // Expose Player State from Manager
    val isPlaying = MusicControllerManager.isPlaying
    val currentMediaItem = MusicControllerManager.currentMediaItem
    val currentPosition = MusicControllerManager.currentPosition
    val duration = MusicControllerManager.duration
    val shuffleModeEnabled = MusicControllerManager.shuffleModeEnabled
    val repeatMode = MusicControllerManager.repeatMode

    private val _sortOption = MutableStateFlow(SortOption.NEWEST_FIRST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // Library Flow
    private val _rawSongs = AppDatabase.getDatabase(application).songDao().getAll()

    val librarySongs: StateFlow<List<Song>> = combine(_rawSongs, _sortOption) { songs, sort ->
        when (sort) {
            SortOption.NEWEST_FIRST -> songs.reversed()
            SortOption.A_Z -> songs.sortedBy { it.title.lowercase() }
            SortOption.Z_A -> songs.sortedByDescending { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History Flow
    val playHistory: StateFlow<List<PlayHistory>> = MusicRepository.getRecentHistory(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites Flow
    val likedSongIds: StateFlow<List<String>> = MusicRepository.getLikedSongIds(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Playlists Flow
    val playlists: StateFlow<List<Playlist>> = MusicRepository.getPlaylists(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize the controller connection
        MusicControllerManager.initialize(application)

        // Sync files on startup
        viewModelScope.launch {
            MusicRepository.syncFilesWithDatabase(application)
        }

        // Load Genre Feeds
        loadGenreFeeds()

        // Polling loop for position updates
        viewModelScope.launch {
            while (true) { // Use true with delay
                if (isPlaying.value) {
                    MusicControllerManager.updatePosition()
                }
                delay(1000)
            }
        }
    }

    fun loadGenreFeeds() {
        val genres = UserPreferences.getGenres(getApplication())
        if (genres.isEmpty()) return

        viewModelScope.launch {
            // We can show loading if we want, but let's just update quietly
            val feeds = MusicRepository.fetchGenreFeeds(getApplication(), genres)
            _uiState.value = _uiState.value.copy(genreFeeds = feeds)
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

        // Track history immediately
        viewModelScope.launch {
            MusicRepository.addToHistory(getApplication(), video)
        }

        _uiState.value = _uiState.value.copy(downloadMessage = "Downloading ${video.title}...")

        viewModelScope.launch {
             // Enqueue download via WorkManager
             val result = MusicRepository.downloadSong(getApplication(), video)

             result.onSuccess { msg ->
                 _uiState.value = _uiState.value.copy(downloadMessage = msg)

                 // If file already exists, play it now
                 if (msg == "File already exists") {
                    playLocalSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                 }
             }
        }
    }

    fun playLocalSong(id: String, title: String, artist: String, thumbnailUrl: String) {
        // Track history
        viewModelScope.launch {
            MusicRepository.addToHistory(getApplication(),
                VideoItem(id, title, "", artist, thumbnailUrl, "")
            )
        }

        // Use the Library Playlist feature instead of single track
        val allSongs = librarySongs.value
        val index = allSongs.indexOfFirst { it.id == id }

        AppLogger.log("[ViewModel] Playing song $title from sorted list (${allSongs.size} items)")

        if (index != -1) {
            MusicControllerManager.playPlaylist(allSongs, index)
        } else {
            // Fallback for non-library play
             val file = File(getApplication<Application>().filesDir, "music_downloads/$id")
             val outputDir = File(getApplication<Application>().filesDir, "music_downloads")
             val existingFiles = outputDir.listFiles { _, name -> name.startsWith(id) }
             val targetFile = existingFiles?.firstOrNull() ?: file

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
    }

    // Specifically play a song from a Genre Feed (which is online, not downloaded yet)
    // Actually, "downloadAndPlay" covers this.
    // But if we want to stream without downloading?
    // The requirement says "A Spotify-Style Streaming Experience" but previously "Playback workflow is Download-to-Play".
    // I will stick to "downloadAndPlay" behavior for everything to match existing architecture.
    // But I will rename the exposed method or just use downloadAndPlay.

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun addToQueue(song: Song) {
        MusicControllerManager.addToQueue(song)
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            // 1. Remove from DB
            AppDatabase.getDatabase(getApplication()).songDao().deleteById(song.id)

            // 2. Move file to "trash" (rename to .deleted)
            withContext(Dispatchers.IO) {
                val file = File(song.filePath)
                if (file.exists()) {
                    file.renameTo(File(file.absolutePath + ".deleted"))
                }
            }
        }
    }

    fun restoreSong(song: Song) {
        viewModelScope.launch {
            // 1. Restore file from "trash"
            withContext(Dispatchers.IO) {
                val deletedFile = File(song.filePath + ".deleted")
                if (deletedFile.exists()) {
                    deletedFile.renameTo(File(song.filePath))
                }
            }

            // 2. Re-insert into DB
            AppDatabase.getDatabase(getApplication()).songDao().insert(song)
        }
    }

    fun finalizeDelete(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val deletedFile = File(song.filePath + ".deleted")
            if (deletedFile.exists()) {
                AppLogger.log("[ViewModel] Finalizing delete for ${song.title}")
                deletedFile.delete()
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

    fun toggleShuffle() {
        MusicControllerManager.toggleShuffleMode()
    }

    fun toggleRepeatMode() {
        MusicControllerManager.toggleRepeatMode()
    }

    fun skipToPrevious() {
        MusicControllerManager.skipToPrevious()
    }

    fun skipToNext() {
        MusicControllerManager.skipToNext()
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

    // Genre Management
    fun addGenre(genre: String) {
        UserPreferences.addGenre(getApplication(), genre)
        loadGenreFeeds()
    }

    fun removeGenre(genre: String) {
        UserPreferences.removeGenre(getApplication(), genre)
        loadGenreFeeds()
    }

    // --- Favorites Logic ---
    fun toggleLike(songId: String) {
        val isLiked = likedSongIds.value.contains(songId)
        viewModelScope.launch {
            MusicRepository.setLikeStatus(getApplication(), songId, !isLiked)
        }
    }

    // --- Playlist Logic ---
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            MusicRepository.createPlaylist(getApplication(), name)
        }
    }

    fun addSongToPlaylist(playlistId: Int, songId: String) {
        viewModelScope.launch {
            MusicRepository.addSongToPlaylist(getApplication(), playlistId, songId)
        }
    }

    fun getSongsForPlaylist(playlistId: Int): kotlinx.coroutines.flow.Flow<List<Song>> {
        return AppDatabase.getDatabase(getApplication()).playlistDao().getSongsForPlaylist(playlistId)
    }
}
