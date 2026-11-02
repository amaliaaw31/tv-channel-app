import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.projectyaya.tvchannel"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.projectyaya.tvchannel"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Remote channel list. The token (optional, only for a private repo) comes from the
        // git-ignored local.properties line `channels.token=...`, never from the repo.
        val channelsToken = Properties().also { props ->
            rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(props::load)
        }.getProperty("channels.token", "")
        buildConfigField(
            "String", "CHANNELS_URL",
            "\"https://raw.githubusercontent.com/amaliaaw31/tv-channel-app/main/data/channels.json\"",
        )
        buildConfigField("String", "CHANNELS_TOKEN", "\"$channelsToken\"")
    }

    // data/channels.json is the single source of truth; it is bundled as the offline fallback.
    // The other files in data/ are tooling output and must not end up in the APK.
    sourceSets.getByName("main").assets.srcDir(rootProject.file("data"))
    androidResources {
        ignoreAssetsPattern = "!curated.json:!excluded.json:!bulk-channels.json"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.core:core-splashscreen:1.2.0")

    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Android's org.json is a stub in JVM unit tests, so tests need the real library.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
