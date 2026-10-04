# 🎵 SongGrab for Android

SongGrab saves the audio of a YouTube video as a song on your phone, as an **MP3** or an
**M4A**, with the title, artist and cover art filled in, or saves the **whole video** as an
MP4. Songs go into your phone's **Music/SongGrab** folder, so SongGrab's own player, your
other music apps (including Isaialai) and the Files app can all play them, offline. Videos go
into **Movies/SongGrab**, so they also show up in your Gallery or Photos app.

It's for your own phone and is installed straight from GitHub (sideloaded). It isn't on the
Play Store, because Google doesn't allow YouTube downloaders there.

> **Save only what you're allowed to keep:** your own uploads, Creative Commons or
> public-domain audio, or songs you have permission to download. YouTube's terms don't allow
> downloading other people's music, and in most countries copying commercial songs without
> permission is copyright infringement.

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/Akrishna87/releases/tag/songgrab-latest)
   and tap **SongGrab.apk** to download it. (Only use **SongGrab-older-phones.apk** if your
   phone is very old and SongGrab.apk won't install.)
2. Open the downloaded file. Android will ask you to allow installing apps from your browser
   (or Files app). Allow it, then tap **Install**. Google Play Protect may warn that it
   doesn't recognise the app, because it isn't from the Play Store. Tap
   **More details → Install anyway**.
3. Open **SongGrab**. The first download takes a few seconds longer while it unpacks its
   downloader.

To update, install a newer `SongGrab.apk` the same way. It installs over the old one and keeps
your songs.

## Using it

- **From the YouTube app:** open a video, tap **Share**, and pick **SongGrab**. It starts
  saving straight away.
- **With a link:** copy a YouTube link, open SongGrab, tap the 📋 paste button and then
  **Save as MP3**.
- Choose **MP3** (plays everywhere), **M4A** (faster, because YouTube's audio is kept as it
  is, without converting) or **Video**. For a video, pick **480p**, **720p** or **1080p**:
  higher is sharper but bigger (a 4-minute video is roughly 15, 30 or 60 MB). SongGrab
  remembers your choices. Videos are saved as H.264 MP4 with AAC sound, which every phone
  plays; if a video isn't available at the size you chose, the closest smaller one is used.
- Downloads keep going if you leave the app, with progress in the notification. You can queue
  several; they're saved one after another.
- Tap a song to play it. You get a mini player, a full player (tap the mini player) with
  shuffle and repeat, lock-screen and notification controls, and headphone/Bluetooth buttons.
- Tap a video to watch it full screen in SongGrab's video player (turn the phone for
  landscape). Its ⋮ menu can also play just its sound in the music player (which keeps going
  with the screen off), or open it in another app.
- When you have videos, **All / Songs / Videos** filters appear above the list. *Shuffle songs*
  only shuffles songs.
- Each item's ⋮ menu can share the file, open the original video, take it off the list, or
  delete it from the phone.

Any link to one YouTube video works: normal watch links, `youtu.be` share links, Shorts,
YouTube Music and live links. Playlist links save only the video they point to. Other sites
that [yt-dlp supports](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)
usually work too.

**Tidy titles:** a video called "Artist - Song (Official Video)" is saved as the song "Song"
by "Artist". Bits like "(Official Video)", "[Lyrics]" and "(Official Audio)" are dropped.
YouTube Music's own track and artist names are used when the video has them.

## If songs stop saving

YouTube changes often, and older versions of the downloader stop working. SongGrab updates its
downloader (yt-dlp) from GitHub every couple of days, and right away if a download fails in a
way an update might fix. To update by hand, tap ⓘ at the top and **Update downloader**.

- *"YouTube wants a sign-in to prove you're not a bot"*: YouTube is suspicious of your
  network. Try again later, or switch between Wi-Fi and mobile data.
- *"This video is age-restricted"* or *"private"*: these can't be saved without signing in,
  which SongGrab doesn't do.

## How it's built

- Kotlin and Jetpack Compose, like the other apps in this repo.
- [youtubedl-android](https://github.com/yausername/youtubedl-android) bundles
  [yt-dlp](https://github.com/yt-dlp/yt-dlp), a small Python and
  [ffmpeg](https://ffmpeg.org). yt-dlp fetches the best audio, ffmpeg converts it to MP3
  (best-quality VBR) or keeps the M4A, and adds the tags and cover. For a video, yt-dlp
  fetches the picture and sound separately and ffmpeg merges them into one MP4.
  ([Grabber.kt](app/src/main/java/io/github/akrishna87/songgrab/Grabber.kt) has the exact options.)
- A foreground service runs the download queue
  ([DownloadService.kt](app/src/main/java/io/github/akrishna87/songgrab/DownloadService.kt)),
  then the song is saved through Android's MediaStore
  ([Saver.kt](app/src/main/java/io/github/akrishna87/songgrab/Saver.kt)), so it needs no
  storage permission on Android 10 and newer.
- Media3 (ExoPlayer and a media session) plays the songs; a Media3 `PlayerView` screen
  ([VideoActivity.kt](app/src/main/java/io/github/akrishna87/songgrab/VideoActivity.kt))
  plays videos.
- Because Python and ffmpeg are bundled for each processor type, CI builds a separate APK per
  type. `SongGrab.apk` is for 64-bit ARM phones, which covers nearly all phones from the last
  ten years.

CI ([songgrab-apk.yml](../.github/workflows/songgrab-apk.yml)) runs the unit tests, builds the
APKs and runs [ci/smoke-test.sh](ci/smoke-test.sh) on an Android 14 emulator. The test shares
a link to SongGrab (a tone the test serves itself), waits for the MP3 to land in
Music/SongGrab with a tidied title, plays it, and checks it keeps playing in the background.
Then it switches to Video, saves a generated 720p clip to Movies/SongGrab, and opens it in the
video player.
It also tries a real YouTube video (Blender's CC-licensed *Sintel* trailer), but only warns
if that fails, because YouTube often blocks cloud servers. Then the APK is published as the
`songgrab-latest` release.

To build it yourself: `./gradlew assembleRelease` (needs the Android SDK and JDK 17). The
icon is generated with `python3 ci/icon/gen.py app/src/main/res <preview dir>`.
