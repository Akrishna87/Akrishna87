# 🎬 My Videos for Android

A native Android video player for the movies and videos already saved on
your phone. It plays them **straight from storage**. There's nothing to
import and no copies, uploads, ads or account. New downloads and recordings
show up by themselves.

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/Akrishna87/releases/tag/video-player-latest)
   and tap **MyVideos.apk** to download it.
2. Open the downloaded file. Android will ask you to allow installing apps
   from your browser (or Files app). Allow it, then tap **Install**.
   Google Play Protect may warn that it doesn't recognise the app, because
   it isn't from the Play Store. Tap **More details → Install anyway**.
3. Open **My Videos** and tap **Allow access to videos**.

To update, install a newer `MyVideos.apk` the same way. It installs over the
old one and keeps your watch progress.

## Features

- **Videos** lists every video on the phone with a thumbnail, its length,
  quality (720p, 1080p, 4K…) and size. Sort by **Newest**, **Name**,
  **Longest** or **Largest**. Videos added in the last few days get a
  **NEW** badge.
- **Continue watching** at the top shows videos you started but didn't
  finish, with how much is left. Every video **picks up where you left off**
  (a "Resumed at…" note offers **Start over**). Finished videos get a ✓.
- **Folders** groups videos by where they are (Camera, Download, WhatsApp
  Video, Movies…). A folder page has **Play all**.
- **Search** matches video and folder names as you type.
- Tapping a video plays the list from there, so the **next video starts
  when one ends**. "Continue watching" carries on through the rest of that
  video's folder, in name order, which suits series episodes.
- **The player**:
  - Play/pause, seek bar, previous/next, and ±10 s buttons.
  - The ⚙ menu has **playback speed**, the **audio track** (for movies with
    several languages) and **subtitles**. Subtitles built into the file show
    up there.
  - **Load subtitles** (CC button at the top) adds an `.srt`, `.vtt`,
    `.ass` or `.ttml` file from your phone.
  - **Gestures**: double-tap the left or right side to skip 10 s back or
    ahead. Swipe up or down on the left half for **brightness**, or on the
    right half for **volume**.
  - Turns **landscape** for wide videos and stays **upright** for phone
    videos. **Rotate** switches it by hand.
  - **Resize** cycles through Fit, Crop to fill and Stretch.
  - **Picture-in-picture**: press Home while a video plays (or tap the PiP
    button) and it keeps playing in a small window.
  - Pauses when headphones are unplugged. Headphone buttons work.
- **Open with My Videos**: file managers, Downloads and chat apps can open
  videos in the player.
- **Details** in a video's ⋮ menu shows its file name, folder, resolution,
  size and date. The menu also has **Share**, **Play from start** and
  **Mark as watched/unwatched**.

Plays whatever Android can decode: MP4, MKV, WebM, 3GP, MOV and more, with
H.264, H.265/HEVC, VP9 and AV1 video (depending on the phone). Some movies
have DTS or Dolby (AC3/E-AC3) sound, which many phones can't decode. The
app tells you when that happens and plays the picture silently.
Requires Android 8.0 or newer.

## How it's built

- Kotlin and Jetpack Compose (Material 3) for the screens.
- [Media3](https://developer.android.com/media/media3) ExoPlayer and
  `PlayerView` for playback and the standard controls. A `MediaSession`
  handles headphone buttons.
- Videos come from Android's MediaStore (`VideoRepository` in `Videos.kt`).
  The app reads files in place and only needs the "Photos and videos"
  permission.
- Watch progress is stored in the app's SharedPreferences.

| File | What's in it |
| --- | --- |
| `Videos.kt` | Video model, the MediaStore scan, folders, sorting, formatting |
| `WatchProgress.kt` | Remembers how far each video was watched |
| `Thumbnails.kt` | Thumbnails from the media library, with a memory cache |
| `VideoViewModel.kt` | Library state for the screens; refreshes when videos are added |
| `PlayerActivity.kt` | The player: queue, resume, subtitles, rotation, picture-in-picture |
| `GestureLayout.kt` | Double-tap to skip and swipe for brightness/volume |
| `ui/App.kt` | Permission screen, Videos and Folders tabs, search, folder page |
| `ui/Components.kt` | Video rows, thumbnails, Continue watching cards, details dialog |
| `ui/PlayerOverlay.kt` | The player's top bar, gesture bubble and "Resumed at" note |

## Building

GitHub Actions builds it (`.github/workflows/video-player-apk.yml`) on every
push that touches this folder:

1. **build**: `./gradlew assembleRelease` (needs JDK 17 and the Android SDK).
2. **smoke-test**: installs the APK on an Android 14 emulator, copies test
   videos onto it, and checks that the app lists and plays them. It also
   checks that wide videos turn landscape and tall ones stay upright, that
   the controls show up and the media "next" button works, and that
   Continue watching and resuming work. Folders, search,
   picture-in-picture and "Open with" are covered too. Screenshots are
   saved with the build.
3. **publish**: replaces the `video-player-latest` release with the new APK
   and the screenshots.

To build locally instead, open this folder in Android Studio, or run
`./gradlew assembleRelease` with `ANDROID_HOME` pointing at an Android SDK.

### About the signing key

`signing/sideload.keystore` (password `myvideos`) is committed on purpose. It
lets every build install as an update over the last one, which matters for a
sideloaded app. Since it's public, it doesn't prove who built the APK. Only
install builds from this repository's releases. If you ever publish the app
more widely, make a private key and supply it through the `SIGNING_KEYSTORE`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`
environment variables (for example from GitHub secrets). `app/build.gradle.kts`
uses them when they're set.
