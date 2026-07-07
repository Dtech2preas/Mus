package com.example.musicdownloader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.musicdownloader.PlayerStatus
import com.example.musicdownloader.VideoItem
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun PartnerViewPlayer(status: PlayerStatus, onCollapse: () -> Unit, onPlaySong: (VideoItem) -> Unit, partnerReaction: com.example.musicdownloader.Reaction? = null) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050510))) {
        Image(
            painter = rememberAsyncImagePainter(status.thumbnailUrl),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.3f),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back", tint = Color.White)
            }

            Text("PARTNER IS LISTENING TO", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(10.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(status.thumbnailUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(status.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(status.artist, color = PremiumGold, fontSize = 18.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val id = status.mediaId.ifEmpty { "unknown" }
                    onPlaySong(VideoItem(id, status.title, "", status.artist, status.thumbnailUrl, "https://youtube.com/watch?v=$id"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play Locally", tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play Locally", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emoji Reactions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val emojis = listOf("❤️", "🔥", "😂", "😍", "😢")
                emojis.forEach { emoji ->
                    Button(
                        onClick = { com.example.musicdownloader.FirebaseManager.sendReaction(emoji) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (status.duration > 0) {
                Slider(
                    value = status.position.toFloat() / status.duration.toFloat(),
                    onValueChange = {},
                    enabled = false,
                    colors = SliderDefaults.colors(
                        disabledThumbColor = Color.White,
                        disabledActiveTrackColor = Color.White
                    )
                )
            }

            Text("VIEW ONLY MODE", color = Color.Red.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        }

        ReactionOverlay(reaction = partnerReaction)
    }
}
