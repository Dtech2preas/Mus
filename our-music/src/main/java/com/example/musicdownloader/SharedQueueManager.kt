package com.example.musicdownloader

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _queueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val queueItems: StateFlow<List<QueueItem>> = _queueItems.asStateFlow()

    init {
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
    }

    fun removeFromQueue(firebaseKey: String) {
        queueRef.child(firebaseKey).removeValue()
    }
}
