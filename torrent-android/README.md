# ⬇️ My Torrents for Android

A native Android BitTorrent app. Add a magnet link or a `.torrent` file and it
downloads into **Download/Torrents** on your phone, where your Files app,
gallery and music player can find it.

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/Akrishna87/releases/tag/torrent-latest)
   and tap **MyTorrents.apk** to download it.
2. Open the downloaded file and allow installing from your browser when asked,
   then tap **Install** (Play Protect may ask you to confirm, because it isn't
   from the Play Store: **More details → Install anyway**).
3. Open **My Torrents**. Allow notifications so you can see progress and get
   told when a download finishes.

New builds install as updates over the old one and keep your torrents.

## Using it

- **Add** → paste a magnet link (one you've just copied is filled in for you)
  and tap **Download**, or tap **Open a .torrent file**.
- Tapping a magnet link in your browser, opening a downloaded `.torrent` file,
  or sharing a magnet link to the app opens it in My Torrents, which asks
  **Add this torrent?** first. Nothing from another app starts downloading
  until you tap **Download**.
- The list shows progress, speed and time left. Tap ⏸ / ▶ to pause or resume,
  or use ⋮ → **Pause all / Resume all**.
- Tap a torrent for its details: peers, how much is shared, where it's saved,
  and its **files**. Untick files you don't want; tap a finished file to open it.
  🗑 removes the torrent, optionally with its files. Share sends its magnet link.
- Downloads keep going with the screen off or the app closed, with a
  notification showing progress (and a **Pause all** button). The app stops
  running in the background when nothing is downloading.
- Everything is remembered: close the app or restart the phone and the
  torrents are still there, carrying on where they left off.
- **Settings**: Wi-Fi only, keep sharing after downloading (off by default, to
  save data and battery), and download/upload speed limits.

Only download things you have the right to, such as Linux images, public-domain
films and books, or Creative Commons music. Other people in a torrent can see
your internet address.

## How it's built

- Kotlin and Jetpack Compose (Material 3).
- [libtorrent](https://libtorrent.org/) through
  [libtorrent4j](https://github.com/aldenml/libtorrent4j) for the BitTorrent
  side (DHT, trackers, magnet links, peer exchange, encryption).
- Each torrent is saved as libtorrent "resume data" in the app's private folder,
  so it carries on after a restart without re-checking everything.

| File | What's in it |
| --- | --- |
| `engine/TorrentEngine.kt` | Runs libtorrent: add/pause/resume/remove, files, saving and restoring torrents. Plain Kotlin, no Android code |
| `TorrentsApp.kt` | Starts the engine, picks the download folder, Wi-Fi only, media scanning |
| `TorrentService.kt` | Foreground service that keeps downloads going in the background |
| `Notifications.kt` | Progress notification and "Download finished" |
| `TorrentsViewModel.kt` | Connects the screens to the engine; handles links/files from other apps |
| `ui/ListScreen.kt`, `ui/DetailsScreen.kt`, `ui/AddSheet.kt`, `ui/SettingsScreen.kt` | The screens |

## Building

GitHub Actions (`.github/workflows/torrent-apk.yml`) runs on every push that
touches this folder:

1. **build**: `./gradlew assembleRelease`.
2. **smoke-test**: starts a small seeder on the CI machine (`ci/seed.py`, using
   libtorrent's Python package) and installs the APK on an Android 14
   emulator. It checks the **Add** sheet opens, that a link shared from
   another app isn't added when you tap **Cancel**, adds one magnet link by
   sharing it to the app and another by opening it like a browser would, then checks they download into
   Download/Torrents with the right contents, that pausing and resuming work, a
   foreground service runs while downloading, a "Download finished"
   notification appears, the torrents are still there after the app is closed,
   and deleting one removes its files. Screenshots are saved with the build.
3. **publish**: replaces the `torrent-latest` release with the new APK and the
   screenshots.

To build locally, open this folder in Android Studio, or run
`./gradlew assembleRelease` with `ANDROID_HOME` pointing at an Android SDK.

### Security notes

- Links and `.torrent` files from other apps always go through the
  **Add this torrent?** question, so another app can't make the phone download
  and share something on its own. Only `content://` files are accepted from
  other apps (not `file://` paths).
- "Open" on a finished file shares just that file, read-only, and only files in
  the download folders can be shared (`res/xml/file_paths.xml`).
- File names inside a torrent are cleaned up by libtorrent, so a torrent can't
  write outside its download folder.

`signing/sideload.keystore` (password `mytorrents`) is committed on purpose so
every build installs over the last; see the music app's README for why, and
how to use a private key instead (`SIGNING_KEYSTORE` and friends).
