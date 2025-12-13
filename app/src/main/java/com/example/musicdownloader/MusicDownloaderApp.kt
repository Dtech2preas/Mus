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
        try {
            YoutubeDL.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            e.printStackTrace()
            // In a real app, you might want to show a UI error if init fails
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(applicationContext, "Failed to initialize YoutubeDL: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
