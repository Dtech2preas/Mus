plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

group = "com.dtech.music.windows"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

compose.desktop {
    application {
        mainClass = "com.dtech.music.windows.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "DTECH_MUSIC"
            packageVersion = "1.0.0"
            description = "DTECH Music Application for Windows"
            vendor = "PREASX24"
            windows {
                menuGroup = "DTECH MUSIC"
                shortcut = true
            }
        }
    }
}
