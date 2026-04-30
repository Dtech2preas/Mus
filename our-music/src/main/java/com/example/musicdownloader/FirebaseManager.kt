package com.example.musicdownloader

import android.content.Context
import com.example.musicdownloader.data.Song
import com.example.musicdownloader.data.Playlist
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerStatus(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val position: Long = 0,
    val duration: Long = 0,
    val isPlaying: Boolean = false,
    val lastUpdated: Long = 0,
    val seeker: String = "" // Who changed the position last
)

data class Reaction(
    val id: String = "",
    val type: String = "",
    val timestamp: Long = 0
)

object FirebaseManager {
    private const val DATABASE_URL = "https://music-1d480-default-rtdb.firebaseio.com/"
    private val database = FirebaseDatabase.getInstance(DATABASE_URL)
    private val sessionsRef = database.getReference("sessions")
    private val sharedQueueRef = database.getReference("sharedQueue")

    private val _partnerStatus = MutableStateFlow<PlayerStatus?>(null)
    val partnerStatus: StateFlow<PlayerStatus?> = _partnerStatus.asStateFlow()

    private val _partnerReactions = MutableStateFlow<Reaction?>(null)
    val partnerReactions: StateFlow<Reaction?> = _partnerReactions.asStateFlow()

    private val _syncCommand = MutableStateFlow<Map<String, Any>?>(null)
    val syncCommand: StateFlow<Map<String, Any>?> = _syncCommand.asStateFlow()

    private var partnerName: String = ""
    private var myName: String = ""

    fun initialize(context: Context) {
        val name = UserPreferences.getUserName(context) ?: return
        if (myName == name) return // Already initialized with this name

        myName = name
        partnerName = if (myName == "owami") "jonas" else "owami"

        // Clear previous listeners if any? For simplicity in this LDR app, we assume name doesn't change often.

        // Listen for partner's status
        sessionsRef.child(partnerName).child("currentlyPlaying")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue(PlayerStatus::class.java)
                    _partnerStatus.value = status
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Listen for partner's reactions
        sessionsRef.child(partnerName).child("reactions")
            .limitToLast(1)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lastReaction = snapshot.children.firstOrNull()?.getValue(Reaction::class.java)
                    if (lastReaction != null && lastReaction.timestamp > System.currentTimeMillis() - 5000) {
                        _partnerReactions.value = lastReaction
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Listen for sync commands
        sessionsRef.child(partnerName).child("listenTogether")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val cmd = snapshot.getValue() as? Map<String, Any>
                    if (cmd != null) {
                        val ts = cmd["lastUpdated"] as? Long ?: 0
                        if (ts > System.currentTimeMillis() - 5000) {
                            _syncCommand.value = cmd
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun updateMyStatus(status: PlayerStatus) {
        if (myName.isEmpty()) return
        sessionsRef.child(myName).child("currentlyPlaying").setValue(status)
    }

    fun sendReaction(type: String) {
        if (myName.isEmpty()) return
        val reactionRef = sessionsRef.child(myName).child("reactions").push()
        val reaction = Reaction(id = reactionRef.key ?: "", type = type, timestamp = System.currentTimeMillis())
        reactionRef.setValue(reaction)
    }

    fun addToSharedQueue(song: VideoItem, addedBy: String) {
        val entry = mapOf(
            "id" to song.id,
            "title" to song.title,
            "artist" to song.uploader,
            "thumbnailUrl" to song.thumbnailUrl,
            "addedBy" to addedBy,
            "timestamp" to System.currentTimeMillis()
        )
        sharedQueueRef.push().setValue(entry)
    }

    fun syncListenTogether(mediaId: String, position: Long, isPlaying: Boolean) {
        if (myName.isEmpty()) return
        val update = mapOf(
            "mediaId" to mediaId,
            "position" to position,
            "isPlaying" to isPlaying,
            "lastUpdated" to System.currentTimeMillis(),
            "seeker" to myName
        )
        sessionsRef.child(myName).child("listenTogether").setValue(update)
    }

    fun syncLibrary(playlists: List<Playlist>, likedSongs: List<String>) {
        if (myName.isEmpty()) return
        val library = mapOf(
            "playlists" to playlists.map { mapOf("id" to it.id, "name" to it.name) },
            "likedSongsCount" to likedSongs.size,
            "lastUpdated" to System.currentTimeMillis()
        )
        sessionsRef.child(myName).child("library").setValue(library)
    }

    fun getPartnerLibrary(callback: (Map<String, Any>?) -> Unit) {
        sessionsRef.child(partnerName).child("library").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                callback(snapshot.getValue() as? Map<String, Any>)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
