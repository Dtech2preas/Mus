package com.example.musicdownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.musicdownloader.ui.MusicAppTheme
import com.example.musicdownloader.ui.SharedQueueScreen

class LdrActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // When entering LDR, stop normal music playback
        MusicControllerManager.pause()

        setContent {
            MusicAppTheme {
                SharedQueueScreen(
                    onBack = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // When leaving LDR, we should probably stop LDR music
            // But the requirement says "only one section can be working at a time"
            // So we stop everything when we leave.
            MusicControllerManager.pause()
            SharedQueueManager.connect(false)
        }
    }
}
