package com.example.musicdownloader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

@Composable
fun UpdateBanner(text: String) {
    val context = LocalContext.current

    // Simple URL regex to find http/https links
    val urlPattern = Pattern.compile(
        "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)"
                + "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*"
                + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)",
        Pattern.CASE_INSENSITIVE or Pattern.MULTILINE or Pattern.DOTALL
    )

    val matcher = urlPattern.matcher(text)
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        while (matcher.find()) {
            val start = matcher.start(1)
            val end = matcher.end()

            // Append text before URL
            append(text.substring(lastIndex, start))

            // Append URL
            val url = text.substring(start, end)
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url

            pushStringAnnotation(tag = "URL", annotation = fullUrl)
            pushStyle(style = SpanStyle(color = Color(0xFF00A6FF), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold))
            append(url)
            pop()
            pop()

            lastIndex = end
        }
        // Append remaining text
        append(text.substring(lastIndex))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E2A))
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        ClickableText(
            text = annotatedString,
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 14.sp
            ),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                        context.startActivity(intent)
                    }
            }
        )
    }
}
