package com.dtech.music.windows

import com.dtech.music.windows.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

data class VideoItem(
    val id: String,
    val title: String,
    val author: String,
    val duration: String,
    val thumbnailUrl: String
)

class MusicRepository(private val preferences: UserPreferences) {

    private val _madeForYou = MutableStateFlow<List<VideoItem>>(emptyList())
    val madeForYou: StateFlow<List<VideoItem>> = _madeForYou.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<VideoItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<VideoItem>> = _recentlyPlayed.asStateFlow()

    private val _recommendedSongs = MutableStateFlow<List<VideoItem>>(emptyList())
    val recommendedSongs: StateFlow<List<VideoItem>> = _recommendedSongs.asStateFlow()

    data class GenreFeed(val genreName: String, val songs: List<VideoItem>)
    private val _genreFeeds = MutableStateFlow<List<GenreFeed>>(emptyList())
    val genreFeeds: StateFlow<List<GenreFeed>> = _genreFeeds.asStateFlow()

    init {
        refreshRecentlyPlayed()
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            InnerTubeClient.search(query).map { androidItem ->
                VideoItem(
                    id = androidItem.id,
                    title = androidItem.title,
                    author = androidItem.author,
                    duration = androidItem.duration,
                    thumbnailUrl = androidItem.thumbnailUrl
                )
            }
        } catch (e: Exception) {
            println("Search failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val cachedUrl = transaction {
            StreamCacheTable.select { StreamCacheTable.videoId eq videoId }
                .map { row -> row[StreamCacheTable.streamUrl] }
                .singleOrNull()
        }

        if (cachedUrl != null) return@withContext cachedUrl

        try {
            val url = YoutubeClient.getStreamUrl(videoId)
            if (url != null) {
                transaction {
                    StreamCacheTable.insert {
                        it[StreamCacheTable.videoId] = videoId
                        it[StreamCacheTable.streamUrl] = url
                        it[StreamCacheTable.expiryTimestamp] = System.currentTimeMillis() + (1000 * 60 * 60 * 5)
                    }
                }
            }
            url
        } catch (e: Exception) {
            null
        }
    }

    fun refreshRecentlyPlayed() {
        val history = transaction {
            PlayHistoryTable.selectAll()
                .orderBy(PlayHistoryTable.timestamp to SortOrder.DESC)
                .limit(20)
                .map { row ->
                    VideoItem(
                        id = row[PlayHistoryTable.songId],
                        title = row[PlayHistoryTable.title],
                        author = row[PlayHistoryTable.artist],
                        duration = row[PlayHistoryTable.duration],
                        thumbnailUrl = row[PlayHistoryTable.thumbnailUrl]
                    )
                }
        }
        _recentlyPlayed.value = history
    }

    fun addToHistory(item: VideoItem) {
        transaction {
            PlayHistoryTable.insert {
                it[PlayHistoryTable.songId] = item.id
                it[PlayHistoryTable.title] = item.title
                it[PlayHistoryTable.artist] = item.author
                it[PlayHistoryTable.duration] = item.duration
                it[PlayHistoryTable.thumbnailUrl] = item.thumbnailUrl
                it[PlayHistoryTable.timestamp] = System.currentTimeMillis()
            }
        }
        refreshRecentlyPlayed()
    }

    suspend fun refreshRecommendations() {
        // Implement simple recommendation fetch based on artists or recent
        val artists = preferences.favoriteArtists.value
        if (artists.isNotEmpty()) {
            val results = search("${artists.random()} music mix").take(15)
            _recommendedSongs.value = results
        } else if (preferences.favoriteGenres.value.isNotEmpty()) {
            val results = search("${preferences.favoriteGenres.value.random()} mix").take(15)
            _recommendedSongs.value = results
        }
    }

    suspend fun fetchMadeForYou() {
        val genres = preferences.favoriteGenres.value
        if (genres.isEmpty()) return

        val shuffledGenres = genres.shuffled()
        val results = mutableListOf<VideoItem>()
        val feeds = mutableListOf<GenreFeed>()
        for (genre in shuffledGenres.take(5)) {
             try {
                 val searchResults = search("$genre music")
                 results.addAll(searchResults.take(10))
                 feeds.add(GenreFeed(genre, searchResults.take(10)))
             } catch (e: Exception) {
                 println("Failed fetching genre $genre")
             }
        }
        _madeForYou.value = results.shuffled().distinctBy { it.id }
        _genreFeeds.value = feeds
    }

    fun getAllLibrarySongs(): Flow<List<VideoItem>> = flow {
         val songs = transaction {
             StreamSongsTable.select { StreamSongsTable.isLibrary eq true }
                 .map { row ->
                     VideoItem(
                         id = row[StreamSongsTable.id],
                         title = row[StreamSongsTable.title],
                         author = row[StreamSongsTable.artist],
                         duration = row[StreamSongsTable.duration],
                         thumbnailUrl = row[StreamSongsTable.thumbnailUrl]
                     )
                 }
         }
         emit(songs)
    }
}
