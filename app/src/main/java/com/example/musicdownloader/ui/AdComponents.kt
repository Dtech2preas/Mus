package com.example.musicdownloader.ui

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.Cover
import com.startapp.sdk.ads.banner.Mrec

@Composable
fun StartAppBannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.wrapContentSize(),
        factory = { context ->
            Banner(context).apply {
                // Optional: LayoutParams can be set here if needed
            }
        }
    )
}

@Composable
fun StartAppMrecAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.wrapContentSize(),
        factory = { context ->
            Mrec(context).apply {
                // Mrec is 300x250
            }
        }
    )
}

@Composable
fun StartAppCoverAd(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.wrapContentSize(),
        factory = { context ->
            Cover(context).apply {
                // Cover is 1200x628
            }
        }
    )
}
