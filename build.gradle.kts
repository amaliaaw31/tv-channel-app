plugins {
    id("com.android.application") version "9.5.0" apply false
    // Compose compiler plugin must match the Kotlin version bundled with AGP.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
