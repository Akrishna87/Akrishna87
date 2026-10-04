plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the run number so every build installs as an update over the last one.
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "io.github.akrishna87.songgrab"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.akrishna87.songgrab"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // yt-dlp, Python and ffmpeg are bundled as native libraries for each processor type, which
    // makes one APK for all phones very large. Build one APK per type instead: arm64-v8a fits
    // nearly every phone from the last ten years, armeabi-v7a older ones, x86_64 the emulator.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            // A real keystore can be supplied through CI secrets; otherwise the
            // sideload key in signing/ is used so every build can update the last.
            val keystore = System.getenv("SIGNING_KEYSTORE")
            if (keystore != null && file(keystore).exists()) {
                storeFile = file(keystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("signing/sideload.keystore")
                storePassword = "songgrab"
                keyAlias = "songgrab"
                keyPassword = "songgrab"
            }
        }
    }

    buildTypes {
        release {
            // Almost all of the APK is the bundled Python and ffmpeg, so shrinking the code
            // saves little, and the downloader library reads its JSON by reflection.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        // The bundled Python and ffmpeg run as programs, so they must be unpacked on install.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    // yt-dlp with its own Python, plus ffmpeg to turn the audio into MP3.
    val youtubedl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedl")

    testImplementation("junit:junit:4.13.2")
    // Android's own org.json is only a stub in local unit tests.
    testImplementation("org.json:json:20240303")
}
