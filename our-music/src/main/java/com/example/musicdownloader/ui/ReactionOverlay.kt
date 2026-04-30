package com.example.musicdownloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicdownloader.Reaction
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun ReactionOverlay(reaction: Reaction?) {
    var currentReaction by remember { mutableStateOf<Reaction?>(null) }
    var key by remember { mutableIntStateOf(0) }

    LaunchedEffect(reaction) {
        if (reaction != null && reaction.id != currentReaction?.id) {
            currentReaction = reaction
            key++
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (currentReaction != null) {
            repeat(5) { i ->
                FloatingEmoji(emoji = currentReaction!!.type, key = key + i)
            }
        }
    }
}

@Composable
fun FloatingEmoji(emoji: String, key: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")

    val xOffset = remember(key) { Random.nextInt(-150, 150).dp }
    val duration = remember(key) { Random.nextInt(2000, 4000) }
    val delay = remember(key) { Random.nextInt(0, 500) }

    val animatable = remember(key) { Animatable(0f) }

    LaunchedEffect(key) {
        delay(delay.toLong())
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(duration, easing = LinearEasing)
        )
    }

    if (animatable.value < 1f && animatable.value > 0f) {
        Text(
            text = emoji,
            fontSize = 40.sp,
            modifier = Modifier
                .offset(x = xOffset, y = (-800 * animatable.value).dp)
                .alpha(1f - animatable.value)
                .graphicsLayer {
                    rotationZ = animatable.value * 360f
                }
        )
    }
}
