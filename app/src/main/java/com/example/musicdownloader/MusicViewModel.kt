package com.example.musicdownloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicdownloader.data.AppDatabase
import com.example.musicdownloader.data.CompressionManager
import com.example.musicdownloader.data.CompressionQuality
import com.example.musicdownloader.data.PlayHistory
import com.example.musicdownloader.data.Playlist
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.utils.DnaAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption {
    NEWEST_FIRST,
    A_Z,
    Z_A
}

data class DnaStats(
    val topArtist: String = "Unknown",
    val topArtistPlays: Int = 0,
    val totalPlays: Int = 0,
    val favoriteGenre: String = "Various",
    val personalityType: String = "The Newcomer",
    val personalityDescription: String = "Just starting your musical journey."
)

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

    // Toast Events Channel
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    // Compression State
    private val _isCompressing = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing.asStateFlow()

    private val _compressionProgress = MutableStateFlow("")
    val compressionProgress: StateFlow<String> = _compressionProgress.asStateFlow()

    // Expose Player State from Manager
    val isPlaying = MusicControllerManager.isPlaying
    val currentMediaItem = MusicControllerManager.currentMediaItem
    val currentPosition = MusicControllerManager.currentPosition
    val duration = MusicControllerManager.duration
    val shuffleModeEnabled = MusicControllerManager.shuffleModeEnabled
    val repeatMode = MusicControllerManager.repeatMode
    val audioSessionId = MusicControllerManager.audioSessionId

    // Download Progress Flow (Global)
    val downloadProgress = YoutubeClient.downloadProgress

    // Active Downloads List (Derived)
    // Filter out completed ones (100f)
    val activeDownloads: StateFlow<List<DownloadStatus>> = downloadProgress
        .map { it.values.toList().filter { status -> status.progress < 100f } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Initializing Downloads (Waiting for start)
    private val _initializingDownloads = MutableStateFlow<Set<String>>(emptySet())
    val initializingDownloads: StateFlow<Set<String>> = _initializingDownloads.asStateFlow()

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

    // D-TECH DNA Stats Flow
    val dnaStats: StateFlow<DnaStats> = combine(
        MusicRepository.getTopArtist(application),
        MusicRepository.getTotalPlayCount(application)
    ) { topArtist, totalPlays ->
        val genres = UserPreferences.getGenres(application)
        val favGenre = genres.firstOrNull() ?: "Various"

        val (type, desc) = DnaAnalyzer.calculatePersonality(topArtist, totalPlays, genres.size)

        DnaStats(
            topArtist = topArtist?.artist ?: "None Yet",
            topArtistPlays = topArtist?.playCount ?: 0,
            totalPlays = totalPlays,
            favoriteGenre = favGenre,
            personalityType = type,
            personalityDescription = desc
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DnaStats())

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

        // Monitor download progress to clear initializing state
        viewModelScope.launch {
            downloadProgress.collect { progressMap ->
                val currentInitializing = _initializingDownloads.value
                if (currentInitializing.isNotEmpty()) {
                    // Remove IDs that have started downloading (progress > 0)
                    val newInitializing = currentInitializing.filter { id ->
                        val status = progressMap[id]
                        status == null || status.progress <= 0f
                    }.toSet()

                    if (newInitializing.size != currentInitializing.size) {
                        _initializingDownloads.value = newInitializing
                    }
                }
            }
        }
    }

    fun loadGenreFeeds() {
        val genres = UserPreferences.getGenres(getApplication())
        if (genres.isEmpty()) return

        // Show loading via state if desired, but we want Skeleton behavior which checks empty/loading.
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            val feeds = MusicRepository.fetchGenreFeeds(getApplication(), genres)
            _uiState.value = _uiState.value.copy(genreFeeds = feeds, isLoading = false)
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MusicRepository.addToHistory(getApplication(), video)
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Error adding to history: ${e.message}")
            }
        }

        // --- NEW LOGIC ---
        // We will perform the file check on IO thread
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getApplication<Application>().filesDir, "music_downloads/${video.id}")
            val outputDir = File(getApplication<Application>().filesDir, "music_downloads")
            val existingFiles = outputDir.listFiles { _, name -> name.startsWith(video.id) }
            val targetFile = existingFiles?.firstOrNull() ?: file

            if (targetFile.exists()) {
                 // Play local file immediately
                 withContext(Dispatchers.Main) {
                     playSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                 }
                 return@launch // Don't download again
            }

            // Not found locally, start parallel tasks
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(downloadMessage = "Downloading ${video.title}...")
                _initializingDownloads.value += video.id
                YoutubeClient.initializeDownloadStatus(video)
            }

            // 1. Start Download (Background)
            // We use a separate launch so it doesn't block streaming if download takes time to queue (it shouldn't, but safe)
            launch { // Inherits Dispatchers.IO
                try {
                    _toastEvent.emit("Download started for ${video.title}")
                    // Enqueue download via WorkManager
                    val result = MusicRepository.downloadSong(getApplication(), video)

                    result.onSuccess { msg ->
                        withContext(Dispatchers.Main) {
                             _uiState.value = _uiState.value.copy(downloadMessage = msg)
                        }

                        // If file already exists, we are done, remove from initializing
                        if (msg == "File already exists") {
                            withContext(Dispatchers.Main) {
                                _initializingDownloads.value -= video.id
                            }
                            // Fallback: Check if playing. If not, play local.
                            val currentId = currentMediaItem.value?.mediaId
                            if (currentId != video.id) {
                                withContext(Dispatchers.Main) {
                                    playSong(video.id, video.title, video.uploader, video.thumbnailUrl)
                                }
                            }
                        }
                    }.onFailure { e ->
                        // If failed, remove from initializing
                        withContext(Dispatchers.Main) {
                            _initializingDownloads.value -= video.id
                            _toastEvent.emit("Download failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _initializingDownloads.value -= video.id
                        _toastEvent.emit("Error starting download: ${e.message}")
                    }
                    AppLogger.log("[ViewModel] Download Error: ${e.message}")
                }
            }

            // 2. Start Streaming (Instant Play)
            launch { // Inherits Dispatchers.IO
                 withContext(Dispatchers.Main) {
                     _uiState.value = _uiState.value.copy(isLoadingPlayer = true)
                 }
                 try {
                     val streamInfo = YoutubeClient.getStreamUrl(getApplication(), video.webUrl)
                     if (streamInfo.url.isNotBlank()) {
                          // Check if local playback started (race condition from fast download/exist)
                          val currentId = currentMediaItem.value?.mediaId
                          if (currentId == video.id) {
                              // Already playing (likely local file), skip streaming
                              return@launch
                          }

                          val mediaMetadata = MediaMetadata.Builder()
                              .setTitle(video.title)
                              .setArtist(video.uploader)
                              .setArtworkUri(android.net.Uri.parse(video.thumbnailUrl))
                              .build()

                          val mediaItem = MediaItem.Builder()
                              .setUri(streamInfo.url)
                              .setMediaId(video.id)
                              .setMediaMetadata(mediaMetadata)
                              .build()

                          withContext(Dispatchers.Main) {
                               MusicControllerManager.playMedia(mediaItem)
                               _toastEvent.emit("Streaming ${video.title}...")
                          }
                     }
                 } catch (e: Exception) {
                     AppLogger.log("Streaming failed: ${e.message}")
                     _toastEvent.emit("Streaming failed, downloading in background...")
                 } finally {
                     withContext(Dispatchers.Main) {
                         _uiState.value = _uiState.value.copy(isLoadingPlayer = false)
                     }
                 }
            }
        }
    }

    fun pauseDownload(videoId: String) {
        viewModelScope.launch {
            MusicRepository.pauseDownload(getApplication(), videoId)
            _toastEvent.emit("Download paused")
        }
    }

    fun resumeDownload(videoId: String) {
        // Find the original VideoItem from status
        val status = downloadProgress.value[videoId]
        if (status?.videoItem != null) {
            viewModelScope.launch {
                // Re-enqueue
                // We should probably check if it's already running? status.isPaused should be true.
                _toastEvent.emit("Resuming download...")
                val result = withContext(Dispatchers.IO) {
                    MusicRepository.downloadSong(getApplication(), status.videoItem)
                }
                result.onSuccess {
                    // Update status to not paused (YoutubeClient logic might need to be refreshed or wait for worker)
                    // The worker will start and call updateProgress which overwrites status, effectively unpausing it.
                }.onFailure {
                    _toastEvent.emit("Failed to resume")
                }
            }
        } else {
            // Should not happen if we initialized correctly
            viewModelScope.launch {
                _toastEvent.emit("Cannot resume: Metadata lost")
            }
        }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                MusicRepository.deleteDownload(getApplication(), videoId)
            }
            _initializingDownloads.value -= videoId
            _toastEvent.emit("Download cancelled and deleted")
        }
    }

    fun playSong(id: String, title: String, artist: String, thumbnailUrl: String, contextQueue: List<Song>? = null) {
        // Track history
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MusicRepository.addToHistory(getApplication(),
                    VideoItem(id, title, "", artist, thumbnailUrl, "")
                )
            } catch (e: Exception) {
                 e.printStackTrace()
            }
        }

        // Use the contextQueue if provided, otherwise default to Library (All Songs)
        val queueToUse = contextQueue ?: librarySongs.value
        val index = queueToUse.indexOfFirst { it.id == id }

        AppLogger.log("[ViewModel] Playing song $title from list (${queueToUse.size} items)")

        if (index != -1) {
            MusicControllerManager.playPlaylist(queueToUse, index)
        } else {
            // Fallback for non-library play (e.g. search result not in library yet)
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

    @Deprecated("Use playSong instead")
    fun playLocalSong(id: String, title: String, artist: String, thumbnailUrl: String) {
        playSong(id, title, artist, thumbnailUrl, null)
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    suspend fun addToQueue(song: Song): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                MusicControllerManager.addToQueue(song)
                // _toastEvent.emit("Added to queue: ${song.title}") // UI handles success message usually
                true
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Error adding to queue: ${e.message}")
                _toastEvent.emit("Failed to add to queue")
                false
            }
        }
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

    // Deprecated: used old logic, but now mapped to deleteDownload for consistency if called
    fun cancelDownload(videoId: String) {
        deleteDownload(videoId)
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MusicRepository.setLikeStatus(getApplication(), songId, !isLiked)
                val msg = if (isLiked) "Removed from Liked Songs" else "Added to Liked Songs"
                _toastEvent.emit(msg)
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Error toggling like: ${e.message}")
                _toastEvent.emit("Failed to update favorites")
            }
        }
    }

    // --- Playlist Logic ---
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            MusicRepository.createPlaylist(getApplication(), name)
        }
    }

    fun addSongToPlaylist(playlist: Playlist, songs: List<Song>) {
        viewModelScope.launch {
             // In the future, batch add. For now, loop.
             songs.forEach { song ->
                  MusicRepository.addSongToPlaylist(getApplication(), playlist.id, song.id)
             }
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

    fun rescanLibrary() {
        viewModelScope.launch {
            _toastEvent.emit("Starting library scan...")
            try {
                MusicRepository.rescanLibrary(getApplication())
                _toastEvent.emit("Scan complete. Metadata updated.")
            } catch (e: Exception) {
                AppLogger.log("[ViewModel] Scan failed: ${e.message}")
                _toastEvent.emit("Scan failed: ${e.message}")
            }
        }
    }

    // New Features
    fun importLocalSongs() {
        viewModelScope.launch {
            _toastEvent.emit("Scanning local files...")
            try {
                val count = MusicRepository.importLocalSongs(getApplication())
                _toastEvent.emit("Imported $count songs.")
            } catch (e: Exception) {
                _toastEvent.emit("Import failed: ${e.message}")
            }
        }
    }

    fun updateSongMetadata(song: Song, newTitle: String, newArtist: String, newAlbum: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(getApplication()).songDao().updateMetadata(
                    id = song.id,
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum
                )
                _toastEvent.emit("Metadata updated")
            } catch (e: Exception) {
                _toastEvent.emit("Update failed: ${e.message}")
            }
        }
    }

    fun launchEqualizer() {
        MusicControllerManager.launchEqualizer(getApplication())
    }

    fun compressSongs(songs: List<Song>, quality: CompressionQuality) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCompressing.value = true
            var successCount = 0
            var failCount = 0
            val total = songs.size

            songs.forEachIndexed { index, song ->
                _compressionProgress.value = "Compressing ${index + 1} of $total...\n${song.title}"

                // Optional: Check if already compressed? No easy way unless we store bitrate metadata.
                // We assume user knows what they are doing.

                val resultFile = CompressionManager.compressSong(getApplication(), song, quality)
                if (resultFile != null) {
                    try {
                        MusicRepository.replaceSongFile(getApplication(), song, resultFile)
                        successCount++
                    } catch (e: Exception) {
                        AppLogger.log("[ViewModel] Replace failed: ${e.message}")
                        failCount++
                    }
                } else {
                    failCount++
                }
            }

            _isCompressing.value = false
            _compressionProgress.value = ""
            _toastEvent.emit("Compression complete. Success: $successCount, Failed: $failCount")
        }
    }
}
