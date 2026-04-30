package com.example.musicdownloader.utils

import android.content.Context
import com.example.musicdownloader.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object UpdateManager {
    private val client = OkHttpClient()

    suspend fun fetchUpdateBanner(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.dtech-services.co.za/update.txt")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.log("Failed to fetch update banner: ${e.message}")
            null
        }
    }
}
