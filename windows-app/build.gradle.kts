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

    // Database (Exposed + SQLite)
    val exposedVersion = "0.45.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.43.0.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")

    // Media Player (VLCJ)
    implementation("uk.co.caprica:vlcj:4.8.2")

    // Image Loading
    implementation("io.coil-kt.coil3:coil-compose:3.0.0-alpha06")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0-alpha06")
}

compose.desktop {
    application {
        mainClass = "com.dtech.music.windows.MainKt"
        nativeDistributions {
            includeAllModules = true
            modules("java.sql", "java.naming", "jdk.unsupported")
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "DTECH_MUSIC"
            packageVersion = "1.0.0"
            description = "DTECH Music Application for Windows"
            vendor = "PREASX24"
            windows {
                includeAllModules = true
                menuGroup = "DTECH MUSIC"
                shortcut = true
            }
        }
    }
}
