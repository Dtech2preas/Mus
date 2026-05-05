package com.example.musicdownloader.workers

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.musicdownloader.AppLogger
import com.example.musicdownloader.YoutubeClient
import java.io.File

class ExternalDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("videoId") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown Title"

        val context = applicationContext

        // Save to public Downloads directory
        val outputDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicDownloader")
        if (!outputDir.exists()) outputDir.mkdirs()

        AppLogger.log("[ExternalWorker] Starting download for $title ($videoId) to public folder")

        try {
            // Update initial progress
            YoutubeClient.updateProgress(videoId, title, 0f, "Starting...", "", "")

            val resultFile = YoutubeClient.downloadAudio(context, videoId, title, outputDir)

            if (resultFile.exists()) {
                AppLogger.log("[ExternalWorker] Download success: ${resultFile.absolutePath}")
                YoutubeClient.updateProgress(videoId, title, 100f, "Complete", "", "")
                return Result.success(workDataOf("filePath" to resultFile.absolutePath))
            } else {
                AppLogger.log("[ExternalWorker] Download failed (file missing) for $videoId")
                YoutubeClient.updateProgress(videoId, title, 0f, "Failed", "", "")
                return Result.failure()
            }
        } catch (e: Exception) {
            AppLogger.log("[ExternalWorker] Error downloading $videoId: ${e.message}")
            YoutubeClient.updateProgress(videoId, title, 0f, "Error", "", "")
            return Result.failure()
        }
    }
}
