package com.example.musicdownloader.workers

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.musicdownloader.AppLogger
import com.example.musicdownloader.YoutubeClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

class ExternalDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val videoId = inputData.getString("videoId") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown Title"

        val context = applicationContext

        // Step 1: Download to app's private cache directory
        val cacheDir = File(context.cacheDir, "MusicDownloaderTemp")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        AppLogger.log("[ExternalWorker] Starting download for $title ($videoId) to temp cache")

        try {
            val tempResultFile = YoutubeClient.downloadAudioExternal(context, videoId, title, cacheDir)

            if (tempResultFile.exists()) {
                AppLogger.log("[ExternalWorker] Temp download success: ${tempResultFile.absolutePath}")

                // Step 2: Copy to public MediaStore/Music
                val finalPath = copyToPublicMusic(context, tempResultFile, title)

                // Step 3: Cleanup temp file
                tempResultFile.delete()

                if (finalPath != null) {
                    AppLogger.log("[ExternalWorker] Copied to public folder: $finalPath")
                    return Result.success(workDataOf("filePath" to finalPath))
                } else {
                    AppLogger.log("[ExternalWorker] Failed to copy to public folder")
                    return Result.failure()
                }
            } else {
                AppLogger.log("[ExternalWorker] Download failed (temp file missing) for $videoId")
                return Result.failure()
            }
        } catch (e: Exception) {
            AppLogger.log("[ExternalWorker] Error downloading $videoId: ${e.message}")
            return Result.failure()
        }
    }

    private fun copyToPublicMusic(context: Context, tempFile: File, title: String): String? {
        val fileName = tempFile.name
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Use MediaStore
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.TITLE, safeTitle)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MusicDownloader")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(tempFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    // Return standard relative path for logging, though the real access is via URI
                    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath + "/MusicDownloader/" + fileName
                }
            } else {
                // API 28 and below: Use standard file operations (requires WRITE_EXTERNAL_STORAGE)
                val publicMusicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MusicDownloader")
                if (!publicMusicDir.exists()) publicMusicDir.mkdirs()

                val publicFile = File(publicMusicDir, fileName)
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(publicFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return publicFile.absolutePath
            }
        } catch (e: Exception) {
            AppLogger.log("[ExternalWorker] Error copying to public directory: ${e.message}")
        }
        return null
    }
}
