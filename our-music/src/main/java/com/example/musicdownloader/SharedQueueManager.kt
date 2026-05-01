package com.example.musicdownloader

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SharedSession(
    val isConnected: Boolean = false,
    val lastTurn: String = "",
    val playbackMediaId: String = "",
    val playbackState: String = "IDLE", // PLAYING, PAUSED, IDLE
    val playbackPosition: Long = 0,
    val playbackTimestamp: Long = 0,
    val streamerOwamiReady: Boolean = false,
    val streamerJonasReady: Boolean = false,
    val owamiConnected: Boolean = false,
    val jonasConnected: Boolean = false,
    // Universal Sync Flags
    val isPlaying: Boolean = false
)

data class QueueItem(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val addedBy: String = "",
    val timestamp: Long = 0,
    val firebaseKey: String = "",
    val streamUrl: String = "" // Pre-fetched stream URL
)

object SharedQueueManager {
    private const val DATABASE_URL = "https://music-1d480-default-rtdb.firebaseio.com/"
    private val database = FirebaseDatabase.getInstance(DATABASE_URL)
    private val queueRef = database.getReference("sharedQueue")
    private val sessionRef = database.getReference("sharedSession")
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _queueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val queueItems: StateFlow<List<QueueItem>> = _queueItems.asStateFlow()

    private val _session = MutableStateFlow(SharedSession())
    val session: StateFlow<SharedSession> = _session.asStateFlow()

    private var myName = ""
    private var context: Context? = null

    fun initialize(ctx: Context) {
        context = ctx.applicationContext
        myName = UserPreferences.getUserName(ctx) ?: ""

        queueRef.orderByChild("timestamp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = mutableListOf<QueueItem>()
                for (child in snapshot.children) {
                    val item = child.getValue(QueueItem::class.java)?.copy(firebaseKey = child.key ?: "")
                    if (item != null) {
                        items.add(item)
                    }
                }
                _queueItems.value = items
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        sessionRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val s = snapshot.getValue(SharedSession::class.java)
                if (s != null) {
                    _session.value = s
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun connect(connected: Boolean) {
        if (myName.isEmpty()) return
        val key = if (myName == "owami") "owamiConnected" else "jonasConnected"
        sessionRef.child(key).setValue(connected)

        sessionRef.get().addOnSuccessListener { snapshot ->
            val s = snapshot.getValue(SharedSession::class.java) ?: return@addOnSuccessListener
            val overallConnected = s.owamiConnected || s.jonasConnected
            if (overallConnected != s.isConnected) {
                sessionRef.child("isConnected").setValue(overallConnected)
            }
            if (connected && s.lastTurn.isEmpty()) {
                val partner = if (myName == "owami") "jonas" else "owami"
                sessionRef.child("lastTurn").setValue(partner)
            }
        }
    }

    fun addToQueue(song: VideoItem, addedBy: String) {
        managerScope.launch {
            // Hardcore logic: Fetch stream URL immediately when added to queue
            val streamUrl = try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val info = YoutubeClient.getStreamUrl(context!!, song.webUrl)
                    info.url
                }
            } catch (e: Exception) {
                Log.e("SharedQueueManager", "Failed to pre-fetch stream URL for ${song.title}", e)
                ""
            }

            val item = QueueItem(
                id = song.id,
                title = song.title,
                artist = song.uploader,
                thumbnailUrl = song.thumbnailUrl,
                addedBy = addedBy,
                timestamp = System.currentTimeMillis(),
                streamUrl = streamUrl
            )
            queueRef.push().setValue(item)
            updateTurn(addedBy)
        }
    }

    fun removeFromQueue(firebaseKey: String) {
        queueRef.child(firebaseKey).removeValue()
    }

    fun updatePlayback(mediaId: String, isPlaying: Boolean, position: Long) {
        val updates = mutableMapOf<String, Any>(
            "playbackMediaId" to mediaId,
            "isPlaying" to isPlaying,
            "playbackState" to (if (isPlaying) "PLAYING" else "PAUSED"),
            "playbackPosition" to position,
            "playbackTimestamp" to System.currentTimeMillis(),
            "streamerOwamiReady" to false,
            "streamerJonasReady" to false
        )
        sessionRef.updateChildren(updates)
    }

    fun toggleSyncPlayPause() {
        val current = _session.value.isPlaying
        sessionRef.child("isPlaying").setValue(!current)
        sessionRef.child("playbackState").setValue(if (!current) "PLAYING" else "PAUSED")
    }

    fun setReady(ready: Boolean) {
        if (myName.isEmpty()) return
        val key = if (myName == "owami") "streamerOwamiReady" else "streamerJonasReady"
        sessionRef.child(key).setValue(ready)
    }

    fun updateTurn(user: String) {
        sessionRef.child("lastTurn").setValue(user)
    }

    fun removeFirst() {
        _queueItems.value.firstOrNull()?.let {
            removeFromQueue(it.firebaseKey)
        }
    }

    fun clearQueue() {
        queueRef.removeValue()
    }
}
