// Project root build.gradle.kts
plugins {
    id("com.android.application") version "8.4.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    // Correct plugin ID for Compose compiler
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}