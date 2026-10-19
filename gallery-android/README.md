# 🌅 Lumen for Android

A photo gallery for Android designed the way Apple designs Photos for iPhone:
iOS colours, layouts, controls and gestures, set in Inter, an open-source typeface
designed along the same lines as Apple's San Francisco. It shows the photos and
videos **already on your phone**, straight from storage. There's nothing to import,
and no copies, uploads, ads or account.

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/Akrishna87/releases/tag/gallery-latest)
   and tap **Lumen.apk** to download it.
2. Open the downloaded file. Android will ask you to allow installing apps from
   your browser (or Files app). Allow it, then tap **Install**. Google Play Protect
   may warn that it doesn't recognise the app, because it isn't from the Play Store.
   Tap **More details → Install anyway**.
3. Open **Lumen**, tap **Continue** and allow access to photos and videos.

To update, install a newer `Lumen.apk` the same way. It installs over the old one
and keeps your favourites.

## Features

- **Library**: every photo and video in one grid, oldest at the top and newest at
  the bottom, where it opens. The dates on screen show in bold at the top. Pinch to
  switch between 3, 5 and 7 photos across. Videos show their length and favourites
  a heart. A count sits under the last row.
  - **Years** and **Months**: big cards with a key photo, a strip of more photos
    and the count. Tap a year to see its months, and a month to jump to it in
    All Photos.
- **Select**: tap **Select** (or long-press a photo) to pick several, then share,
  favourite or delete them from the toolbar.
- **The viewer**: swipe between photos, pinch or double-tap to zoom, and drag down
  to close. Tap once to hide the bars; the background turns black, as in Photos.
  The title shows when it was taken ("Today", "Yesterday", "Monday", "March 3").
  A filmstrip of thumbnails sits above the toolbar: **Share**, **Favorite**,
  **Info** and **Delete**.
  - **Info** shows the date, the camera, lens, file name, megapixels, resolution,
    size and format, and the exposure (ISO, focal length, ev, aperture, shutter
    speed) read from the photo's EXIF data.
  - **Videos** play in place with a scrubber, play/pause and a mute button.
- **Albums**: **My Albums** (Recents, Favorites, then each folder like Camera,
  Screenshots or WhatsApp Images) as two rows of covers that scroll sideways, with
  **See All**. **Media Types** has Videos, Screenshots, Panoramas and Animated
  (GIFs). On Android 11+, **Recently Deleted** shows deleted items and the days left
  before they're removed for good, with **Recover** and **Delete**.
- **Search**: albums, months ("October", "2025") and file names, with suggestions
  for albums and recent months before you type.
- **Limited access** (Android 14+): if you let Lumen see only some photos, Albums
  says so and **Manage…** lets you change the selection.
- Light and dark mode follow the phone's setting.

Deleting a photo moves it to Android's trash (Android 11+), where it stays for 30
days. Android asks you to confirm, as it does for every app. On older versions
Lumen asks, then deletes it. Favourites are kept in the app, so marking one is
instant. Requires Android 8.0 or newer.

## How it's built

- Kotlin and Jetpack Compose. The iOS look comes from the app's own components
  rather than Material: navigation bars with large titles, tab bar, segmented
  control, search field, inset lists, action sheets and capsule buttons
  (`ui/Components.kt`), and line icons in the style of SF Symbols (`ui/AppIcons.kt`).
- Colours are Apple's iOS system palette for light and dark mode, and text uses the
  iOS text styles (sizes, leading and weights from Apple's Human Interface
  Guidelines) in [Inter](https://rsms.me/inter/) (`ui/Theme.kt`). Inter is licensed
  under the SIL Open Font License 1.1; see `third_party/Inter-LICENSE.txt`. Apple's
  own San Francisco fonts may only be used in apps for Apple's platforms, so they
  aren't bundled.
- Photos and videos come from Android's MediaStore (`Media.kt`). Grid thumbnails are
  the media library's own, with a memory cache (`Thumbnails.kt`). Full-size photos,
  GIFs and video frames load with [Coil](https://coil-kt.github.io/coil/), and videos
  play with [Media3](https://developer.android.com/media/media3) ExoPlayer.
- Camera details are read with AndroidX ExifInterface.

| File | What's in it |
| --- | --- |
| `Media.kt` | Photo/video model, the MediaStore scan (and trash), albums, months, formatting |
| `GalleryViewModel.kt` | Library state, permission level, refresh on changes, delete/recover requests |
| `Favorites.kt` | Remembers which photos have a heart |
| `Thumbnails.kt` | Grid thumbnails from the media library, with a memory cache |
| `ui/App.kt` | Welcome screen, tab bar and navigation, share/delete actions, selection toolbar |
| `ui/LibraryScreen.kt` | Library: All Photos, Months and Years |
| `ui/AlbumsScreen.kt` | Albums, See All, an album's photos, Recently Deleted |
| `ui/SearchScreen.kt` | Search and its suggestions |
| `ui/Viewer.kt` | Full-screen viewer: zoom, swipe to close, video, filmstrip, Info |
| `ui/PhotoGrid.kt` | The photo grid, thumbnails, pinch to resize, selection |
| `ui/Components.kt` | iOS-style controls |
| `ui/Theme.kt` | iOS colours and text styles in Inter |
| `ui/AppIcons.kt` | SF Symbols-style icons |

## Building

GitHub Actions builds it (`.github/workflows/gallery-apk.yml`) on every push that
touches this folder:

1. **build**: `./gradlew assembleRelease` (needs JDK 17 and the Android SDK).
2. **smoke-test**: makes test photos (with EXIF camera details), screenshots, a
   panorama and a video, puts them on an Android 14 emulator, and checks the welcome
   screen, the Library, Months and Years, selecting, the viewer (favourite, Info, a
   video playing), Albums, Favorites, Search and dark mode. Screenshots are saved
   with the build.
3. **publish**: replaces the `gallery-latest` release with the new APK and the
   screenshots.

To build locally instead, open this folder in Android Studio, or run
`./gradlew assembleRelease` with `ANDROID_HOME` pointing at an Android SDK.

### About the signing key

`signing/sideload.keystore` (password `lumengallery`) is committed on purpose. It
lets every build install as an update over the last one, which matters for a
sideloaded app. Since it's public, it doesn't prove who built the APK. Only install
builds from this repository's releases. If you ever publish the app more widely,
make a private key and supply it through the `SIGNING_KEYSTORE`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` environment
variables (for example from GitHub secrets). `app/build.gradle.kts` uses them when
they're set.
