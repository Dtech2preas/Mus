package com.example.musicdownloader.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object AppUpdater {

    private val client = OkHttpClient()

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val releaseNotes: String,
        val downloadUrl: String
    )

    suspend fun checkForUpdate(context: Context, isPartnerApp: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/Dtech2preas/Mus/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val tagName = json.optString("tag_name", "").removePrefix("v")
                val releaseNotes = json.optString("body", "No release notes provided.")

                // For simplified version comparison, assuming tags like "1.1" or "v1.1"
                val currentTag = com.example.musicdownloader.BuildConfig.GIT_TAG

                // If it's a dev build, we can ignore updates, or say yes if you prefer.
                // But generally dev-builds don't need updates.
                // We'll compare the exact strings: if the tag on GitHub != our compiled tag, an update is available.
                // Note: tagName from GitHub has 'v' removed. So GitHub's 'v1.2-abc' becomes '1.2-abc' in tagName variable above.
                // Our currentTag includes 'v'. So we should normalize them.
                val normalizedCurrentTag = currentTag.removePrefix("v")

                val isUpdateAvailable = if (currentTag == "dev-build") {
                    false
                } else {
                    tagName != normalizedCurrentTag
                }

                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    val targetApkName = if (isPartnerApp) "our-music-release.apk" else "music-downloader-release.apk"
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i)
                        val name = asset?.optString("name", "") ?: ""
                        if (name.contains(targetApkName) || (name.endsWith(".apk") && !isPartnerApp && name.contains("music-downloader"))) {
                            downloadUrl = asset?.optString("browser_download_url", "") ?: ""
                            break
                        }
                    }

                    // Fallback to the first APK if we didn't match the exact name, for safety if names differ slightly
                    if (downloadUrl.isEmpty()) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i)
                            val name = asset?.optString("name", "") ?: ""
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset?.optString("browser_download_url", "") ?: ""
                                break
                            }
                        }
                    }
                }

                return@withContext UpdateInfo(isUpdateAvailable, tagName, releaseNotes, downloadUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadAndInstallUpdate(context: Context, downloadUrl: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false

                val body = response.body ?: return@withContext false
                val totalLength = body.contentLength()

                val updateFile = File(context.cacheDir, "update.apk")
                if (updateFile.exists()) updateFile.delete()

                body.byteStream().use { input ->
                    FileOutputStream(updateFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var downloadedLength = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedLength += bytesRead
                            if (totalLength > 0) {
                                onProgress(downloadedLength.toFloat() / totalLength.toFloat())
                            }
                        }
                        output.flush()
                    }
                }

                installApk(context, updateFile)
                return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val cleanV1 = v1.substringBefore("-")
        val cleanV2 = v2.substringBefore("-")

        val parts1 = cleanV1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = cleanV2.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }
        return 0
    }
}
