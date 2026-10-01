# 🎵 My Music

A small, dependency-free music player for the songs that are already on
your phone. It plays offline, keeps going in the background, and has
lock-screen and notification controls. There are no ads, no account, and
nothing is uploaded anywhere. You can install it to your home screen on
Android (and iOS).

**Live:** https://akrishna87.github.io/Akrishna87/docs/music-player/

## Getting your songs in

1. Open the link above in **Chrome** on your Android phone.
2. Tap **＋ Add music**, then choose one of these:
   - **Pick songs**: choose one or many songs. In the picker, long-press a
     song and then tap **Select all** to grab a whole folder at once.
   - **Pick a whole folder**: for example `Music` or `Download`. Not every
     phone supports this. If yours only lets you pick single files, use
     **Pick songs**.
3. The app reads each song's title, artist, album and cover art, then
   saves its own copy. You only do this once: the songs are still there
   the next time you open the app, even with no internet.

If you add the same songs again, the app skips the ones it already has, so
re-picking a folder to pick up new downloads is safe.

## Features

- **Songs / Albums / Artists / Playlists** tabs, plus search across all of
  them. Songs can be sorted A–Z, by artist, or newest first.
- **Background playback and lock-screen controls**: play/pause, next,
  previous and seeking from the notification, lock screen, Bluetooth
  headphones or car. The song's cover art shows there too.
- **Shuffle and repeat** (off / all songs / this song). Turning shuffle off
  puts the queue back in its original order.
- **Queue**: from any song's ⋮ menu, choose **Play next** or **Add to
  queue**. The ☰ button on the full player shows what's up next, and you
  can tap a song there to jump to it.
- **Playlists**: create, rename and delete them, and add songs from any
  song's ⋮ menu.
- **Picks up where you left off**: reopening the app brings back the last
  song, where you were in it, and the queue.
- **Tag reading** for MP3 (ID3v1/v2.2–2.4), FLAC, M4A/AAC and Ogg/Opus.
  Songs with no tags get a name from the file name (`03 - Artist -
  Title.mp3` → "Title" by "Artist"). A song with no cover of its own uses
  its album's cover.
- Dark and light themes that follow your phone's setting.
- The Android back button closes the player, menus and album/artist pages
  instead of leaving the app.

## Installing on your phone

- **Android (Chrome)**: open the site, tap the ⋮ menu, then **Add to Home
  screen** / **Install app**. Installing also tells Android to keep the
  app's songs even when the phone runs low on space (the ⚙ menu in the
  app shows whether that's switched on).
- **iOS (Safari)**: open the site, tap the Share icon, then **Add to Home
  Screen**. Playback works, but iOS is less reliable than Android about
  keeping web apps playing in the background.

## A few honest notes

- **The app keeps its own copy of each song**, so your music takes up
  space twice: once as the original file and once inside the app. A web
  app can't reopen a phone's music folder by itself after it's closed, so
  keeping a copy is what makes "pick once, there forever" work. The ⚙
  menu shows how much space the app is using, and **Remove all songs**
  frees it again. Deleting songs inside the app never touches your
  original files.
- The songs live only in this browser on this phone. They don't sync to
  other devices, and clearing Chrome's site data for this page removes
  them.
- File formats are whatever your phone's Chrome can play. MP3, M4A/AAC,
  FLAC, Ogg/Opus and WAV all work on Android. A file that can't be played
  is greyed out and skipped.

## Running it

All the markup, CSS, and JS live in the single `index.html` file, with no
build step. Serve the folder over http(s) (GitHub Pages, or
`python3 -m http.server 8000` from `docs/`). It needs a real web address
rather than a `file://` page so that the browser lets it store songs and
install. `manifest.json`, `sw.js`, and `icons/` provide home-screen
installability and offline loading.

Storage is IndexedDB with four stores: `tracks` (song details),
`blobs` (the audio files), `art` (cover images, one per album) and
`playlists`. Small preferences such as shuffle, repeat, the last tab and
the play position are kept in `localStorage`.
