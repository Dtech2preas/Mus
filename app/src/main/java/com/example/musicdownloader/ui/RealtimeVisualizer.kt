package com.example.musicdownloader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.hypot

@Composable
fun RealtimeVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    barColor: Color = ElectricPurple
) {
    val context = LocalContext.current
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }
    var fftBytes by remember { mutableStateOf<ByteArray?>(null) }

    // Check permission
    val hasPermission = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(audioSessionId, hasPermission) {
        if (audioSessionId != 0 && hasPermission) {
            try {
                val viz = Visualizer(audioSessionId)
                // Try to set highest capture size
                val captureSize = Visualizer.getCaptureSizeRange()[1]
                viz.captureSize = captureSize

                viz.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fftBytes = fft
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)

                viz.enabled = true
                visualizer = viz
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onDispose {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
        }
    }

    Canvas(modifier = modifier) {
        val widthPerBar = size.width / barCount.toFloat()
        val gap = widthPerBar * 0.2f
        val barWidth = widthPerBar - gap

        if (!isPlaying || fftBytes == null) {
            // Draw idle state (small bars)
            for (i in 0 until barCount) {
                drawRoundRect(
                    color = barColor.copy(alpha = 0.3f),
                    topLeft = Offset(i * widthPerBar, size.height - 4.dp.toPx()),
                    size = Size(barWidth, 4.dp.toPx()),
                    cornerRadius = CornerRadius(4f)
                )
            }
            return@Canvas
        }

        val fft = fftBytes!!
        // FFT Size usually 1024 or similar.
        // First byte is DC, but we often ignore index 0 and 1.
        // We only care about the first half (Nyquist), actually simpler: Visualizer return n/2 complex pairs?
        // Documentation: The FFT data contains the real and imaginary parts of a number of frequency points...
        // Format: [Re(0), Im(0), Re(1), Im(1), ... Re(n/2-1), Im(n/2-1)]

        val n = fft.size
        // We want to map the first portion (bass/mid) to the bars,
        // as high frequencies are often empty in music files unless hi-res.
        // Let's use the first 75% of the spectrum.
        val usefulDataSize = (n / 2) * 0.75
        val segmentSize = (usefulDataSize / barCount).toInt().coerceAtLeast(1)

        for (i in 0 until barCount) {
            var magnitude = 0f
            // Average magnitude for this frequency band
            for (j in 0 until segmentSize) {
                val index = (i * segmentSize + j) * 2
                if (index + 1 < n) {
                    val real = fft[index].toFloat()
                    val imag = fft[index + 1].toFloat()
                    magnitude += hypot(real, imag)
                }
            }
            magnitude /= segmentSize

            // Scaling: Logarithmic looks better for audio
            // Visualizer values are small bytes (-128..127). hypot can go up to ~180.
            // We want to boost low values.

            val scale = 2.0f // Sensitivity
            val scaledHeight = (magnitude * scale).coerceIn(4f, size.height)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(barColor, barColor.copy(alpha = 0.5f))
                ),
                topLeft = Offset(i * widthPerBar, size.height - scaledHeight),
                size = Size(barWidth, scaledHeight),
                cornerRadius = CornerRadius(4f)
            )
        }
    }
}
