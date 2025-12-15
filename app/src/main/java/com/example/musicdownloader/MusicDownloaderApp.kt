package com.example.musicdownloader

import android.app.Application
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.log("App", "Application starting...")
        // Force IPv4 globally to avoid IPv6 latency issues
        System.setProperty("java.net.preferIPv4Stack", "true")
        AppLogger.log("App", "Forced IPv4 stack")

        try {
            YoutubeDL.getInstance().init(this)
            AppLogger.log("App", "YoutubeDL initialized")
        } catch (e: YoutubeDLException) {
            AppLogger.log("App", "YoutubeDL init failed: ${e.message}")
            e.printStackTrace()
            // In a real app, you might want to show a UI error if init fails
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(applicationContext, "Failed to initialize YoutubeDL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
