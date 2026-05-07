package com.example.musicdownloader

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val jonasConnected: Boolean = false
)

data class QueueItem(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val addedBy: String = "",
    val timestamp: Long = 0,
    val firebaseKey: String = ""
)

object SharedQueueManager {
    private const val DATABASE_URL = "https://music-1d480-default-rtdb.firebaseio.com/"
    private val database = FirebaseDatabase.getInstance(DATABASE_URL)
    private val queueRef = database.getReference("sharedQueue")
    private val sessionRef = database.getReference("sharedSession")

    private val _queueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val queueItems: StateFlow<List<QueueItem>> = _queueItems.asStateFlow()

    private val _session = MutableStateFlow(SharedSession())
    val session: StateFlow<SharedSession> = _session.asStateFlow()

    private var myName = ""

    fun initialize(context: Context) {
        myName = UserPreferences.getUserName(context)?.lowercase() ?: ""

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

        // Logic for the first one to connect getting the first turn
        sessionRef.get().addOnSuccessListener { snapshot ->
            val s = snapshot.getValue(SharedSession::class.java) ?: return@addOnSuccessListener

            // Update global isConnected
            val overallConnected = s.owamiConnected || s.jonasConnected
            if (overallConnected != s.isConnected) {
                sessionRef.child("isConnected").setValue(overallConnected)
            }

            if (connected && s.lastTurn.isEmpty()) {
                // If I'm the first one connecting, I don't necessarily get the first turn if turn is based on adding
                // But the request says "the first to click connect gets the first tick of a song"
                // meaning the first one to connect gets to add a song first.
                // In our logic, 'lastTurn' is the person who ADDED the last song.
                // So if lastTurn is empty, and I connect, I should be able to add.
                // If I want to EXPLICITLY set who's turn it is, I can use a 'currentTurn' field.
                // Let's use 'lastTurn' to mean "the person who's turn it IS NOT".
                // So if lastTurn is Jonas, it is Owami's turn.
                // If lastTurn is empty, we can set it to the partner's name so it becomes my turn.
                val partner = if (myName == "owami") "jonas" else "owami"
                sessionRef.child("lastTurn").setValue(partner)
            }
        }
    }

    fun addToQueue(song: VideoItem, addedBy: String) {
        val item = QueueItem(
            id = song.id,
            title = song.title,
            artist = song.uploader,
            thumbnailUrl = song.thumbnailUrl,
            addedBy = addedBy,
            timestamp = System.currentTimeMillis()
        )
        queueRef.push().setValue(item)
        updateTurn(addedBy)
    }

    fun removeFromQueue(firebaseKey: String) {
        queueRef.child(firebaseKey).removeValue()
    }

    fun updatePlayback(mediaId: String, state: String, position: Long) {
        val updates = mapOf(
            "playbackMediaId" to mediaId,
            "playbackState" to state,
            "playbackPosition" to position,
            "playbackTimestamp" to System.currentTimeMillis(),
            "streamerOwamiReady" to false,
            "streamerJonasReady" to false
        )
        sessionRef.updateChildren(updates)
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
