package com.example.musicdownloader

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val tag: String,
    val message: String
) {
    fun formatted(): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return "${format.format(date)} [$tag] $message"
    }
}

object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private var idCounter = 0L

    fun log(tag: String, message: String) {
        // Simple synchronized block for the counter to prevent duplicates if needed,
        // though strictly not critical for display.
        val id = synchronized(this) { idCounter++ }

        val entry = LogEntry(
            id = id,
            timestamp = System.currentTimeMillis(),
            tag = tag,
            message = message
        )
        // Atomically update the list
        _logs.update { currentList ->
            currentList + entry
        }
    }

    fun export(context: Context): File? {
        return try {
            val file = File(context.getExternalFilesDir(null), "app_logs_${System.currentTimeMillis()}.txt")
            val content = _logs.value.joinToString("\n") { it.formatted() }
            file.writeText(content)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
