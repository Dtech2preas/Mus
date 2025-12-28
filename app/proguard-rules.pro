# Android & Compose
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# Retrofit & GSON (Network)
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Coil (Images)
-keep class coil.** { *; }

# Data Classes (Don't rename database/JSON fields)
-keep class com.example.musicdownloader.data.** { *; }

# ChaquoPython / FFmpeg (The Engines - CRITICAL)
-keep class com.chaquo.python.** { *; }
-keep class com.arthenica.ffmpegkit.** { *; }

# YouTubeDL (Explicitly requested additions)
-keep class io.github.junkfood02.** { *; }
-keep class com.yausername.** { *; }
