package com.example.musicdownloader.data

import android.content.Context
import com.example.musicdownloader.AppLogger
import com.yausername.youtubedl_android.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class CompressionQuality(val displayName: String, val bitrateVal: String, val approxBitrateKbps: Int) {
    MAX_COMPRESSION("Max Compression (Space Saver)", "48K", 48), // ~48kbps
    BALANCED("Balanced", "96K", 96),       // ~96kbps
    HIGH_QUALITY("High Quality", "128K", 128); // ~128kbps
}

object CompressionManager {

    /**
     * Compresses the given song file to the target bitrate.
     * Returns the new File if successful, or null if failed.
     * Note: This does NOT update the database; the caller must do that.
     */
    suspend fun compressSong(
        context: Context,
        song: Song,
        quality: CompressionQuality
    ): File? = withContext(Dispatchers.IO) {
        val inputFile = File(song.filePath)
        if (!inputFile.exists()) {
            AppLogger.log("[Compression] Input file not found: ${song.filePath}")
            return@withContext null
        }

        // Output file: Use a temp name first
        // We'll stick to m4a (AAC) for efficiency
        val tempFileName = "${song.id}_compressed_${System.currentTimeMillis()}.m4a"
        val outputDir = inputFile.parentFile ?: context.filesDir
        val tempFile = File(outputDir, tempFileName)

        try {
            AppLogger.log("[Compression] Starting compression for ${song.title} to ${quality.bitrateVal}")

            // Use FFmpeg directly to avoid yt-dlp "NoneType" error on local files
            val command = arrayOf(
                "-y",
                "-i", inputFile.absolutePath,
                "-vn",
                "-c:a", "aac",
                "-b:a", quality.bitrateVal.lowercase(),
                tempFile.absolutePath
            )

            // Execute
            FFmpeg.getInstance().execute(command, null)

            if (tempFile.exists() && tempFile.length() > 0) {
                AppLogger.log("[Compression] Success. New size: ${tempFile.length()} vs Old: ${inputFile.length()}")

                // If the new file is somehow bigger (unlikely with low bitrate but possible if re-encoding poorly),
                // we might want to abort?
                // User said "compress", so we assume they want the bitrate target.
                // However, let's strictly replace.

                return@withContext tempFile
            } else {
                AppLogger.log("[Compression] Output file missing or empty.")
                return@withContext null
            }

        } catch (e: Exception) {
            AppLogger.log("[Compression] Error: ${e.message}")
            e.printStackTrace()
            // Cleanup temp
            if (tempFile.exists()) tempFile.delete()
            return@withContext null
        }
    }

    /**
     * Helper to estimate new size in bytes
     * (Bitrate kbps * 1000 / 8) * duration_seconds
     */
    fun estimateSize(song: Song, quality: CompressionQuality): Long {
        val durationSecs = parseDurationInSeconds(song.duration)
        if (durationSecs == 0L) return 0L

        // kbps -> bytes per second: (kbps * 1024) / 8 ... usually bitrate is 1000 based in ffmpeg for audio?
        // Let's use 1000 for simplicity of "k"
        val bytesPerSec = (quality.approxBitrateKbps * 1000) / 8
        return bytesPerSec * durationSecs
    }

    private fun parseDurationInSeconds(durationStr: String): Long {
        return try {
            // format "3:45" or "1:02:30" or "234"
            if (durationStr.contains(":")) {
                val parts = durationStr.split(":").map { it.toLongOrNull() ?: 0L }
                when (parts.size) {
                    2 -> parts[0] * 60 + parts[1]
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    else -> 0L
                }
            } else {
                durationStr.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
