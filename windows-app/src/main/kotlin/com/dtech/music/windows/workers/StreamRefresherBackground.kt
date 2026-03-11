package com.dtech.music.windows.workers

import com.dtech.music.windows.MusicRepository
import com.dtech.music.windows.data.StreamCacheTable
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction

class StreamRefresherBackground(private val repository: MusicRepository) {

    private var job: Job? = null

    fun start() {
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // 1. Clear Expired Cache
                    val currentTime = System.currentTimeMillis()
                    val deletedRows = transaction {
                        StreamCacheTable.deleteWhere {
                            StreamCacheTable.expiryTimestamp less currentTime
                        }
                    }
                    if (deletedRows > 0) {
                        println("StreamRefresherBackground: Cleared $deletedRows expired cache entries.")
                    }

                    // 2. Pre-fetch URLs for Library items (High-End Mode logic)
                    // Fetch library songs that don't have valid stream cache and fetch them via YoutubeClient
                    // Placeholder for full logic to prevent long blocking API calls
                    delay(15 * 60 * 1000L) // Run every 15 minutes to mimic WorkManager
                } catch (e: Exception) {
                    println("StreamRefresherBackground Error: ${e.message}")
                    delay(60 * 1000L) // Retry after 1 min on failure
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
