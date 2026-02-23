package com.example.musicdownloader.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.musicdownloader.AppLogger
import com.example.musicdownloader.MusicRepository
import com.example.musicdownloader.data.AppDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class StreamRefresherWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        AppLogger.log("[StreamRefresher] Starting stream refresh job...")

        val db = AppDatabase.getDatabase(applicationContext)
        val streamDao = db.streamSongDao()
        val cacheDao = db.streamCacheDao()
        val currentTime = System.currentTimeMillis() / 1000

        try {
            // 1. Refresh Manual Songs
            // Collect list once (first emission)
            val manualSongs = streamDao.getManualStreamSongs().first()
            AppLogger.log("[StreamRefresher] Checking ${manualSongs.size} manual songs...")

            val refreshJobs = manualSongs.mapNotNull { song ->
                val cached = cacheDao.getStreamCache(song.id)
                // Refresh if missing or < 30 mins left (1800s)
                val needsRefresh = cached == null || (cached.expireTime - currentTime) < 1800

                if (needsRefresh) {
                     async {
                         AppLogger.log("[StreamRefresher] Refreshing stream for: ${song.title}")
                         try {
                             MusicRepository.prefetchStream(applicationContext, song.id)
                             true
                         } catch (e: Exception) {
                             AppLogger.log("[StreamRefresher] Failed to refresh ${song.id}: ${e.message}")
                             false
                         }
                     }
                } else null
            }

            val results = refreshJobs.awaitAll()
            val successCount = results.count { it }
            AppLogger.log("[StreamRefresher] Refreshed $successCount manual songs.")

            // 2. Prune Auto Songs
            val autoSongs = streamDao.getAutoStreamSongs().first()
            AppLogger.log("[StreamRefresher] Checking ${autoSongs.size} auto songs...")

            var prunedCount = 0
            autoSongs.forEach { song ->
                val cached = cacheDao.getStreamCache(song.id)
                // If cache is expired or missing, delete the auto-entry
                // Note: cached.expireTime is in seconds
                val isExpired = cached == null || cached.expireTime < currentTime

                if (isExpired) {
                    // Double check: if it was just added recently (< 1 hour), maybe don't delete yet?
                    // song.dateAdded is in ms.
                    val ageMs = System.currentTimeMillis() - song.dateAdded
                    if (ageMs > 3600_000) { // Older than 1 hour
                        AppLogger.log("[StreamRefresher] Pruning expired auto song: ${song.title}")
                        streamDao.delete(song)
                        prunedCount++
                    }
                }
            }
            AppLogger.log("[StreamRefresher] Pruned $prunedCount auto songs.")

            Result.success()

        } catch (e: Exception) {
            AppLogger.log("[StreamRefresher] Job failed: ${e.message}")
            Result.retry()
        }
    }
}
