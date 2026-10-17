# Reader: native RSS reader for Android

A standalone Android app written in Kotlin with Jetpack Compose. It fetches,
parses and stores feeds itself, on the phone. It doesn't need Chrome, a
website or a relay.

**Download:** <https://github.com/Akrishna87/Akrishna87/releases/latest/download/rss-reader.apk>

## Features

- Subscribe by feed URL **or website address**: the app finds the site's feed.
  YouTube channel, subreddit, GitHub project, Medium and Substack links work too.
- RSS 2.0, RSS 1.0/RDF and Atom, including podcast and YouTube feeds.
- All / Starred / per-feed views, an "Unread only" filter, mark all as read.
- Native article reader with inline images and tappable links, plus share and
  "Read on site".
- Pull to refresh; refreshes automatically when opened (if older than 15 minutes).
- Works offline: articles are saved on the phone.
- OPML import/export (bring feeds over from the web reader or any other app:
  web reader ⚙ Settings → Export OPML, then here ⋮ → Import OPML).
- Share a link to **Reader** from any app to subscribe to it.
- Material You colours on Android 12+, dark mode.

## Installing

1. Open the download link on your phone and open `rss-reader.apk`.
2. Allow installing from your browser if Android asks, then tap **Install**.
   If Play Protect warns about an unknown app, tap **More details → Install anyway**.

New versions install over the old one and keep your feeds.

## Code

- `app/src/main/java/.../data/`: plain Kotlin, no Android dependencies:
  feed parser (`FeedParser.kt`), networking and feed discovery (`Net.kt`),
  OPML, storage (`Store.kt`).
- `ReaderViewModel.kt`: app state and actions.
- `ui/`: Compose screens.

## Building

`.github/workflows/android-apk.yml` builds and signs the APK on every change
to `android/` (or on demand from the Actions tab → *Android APK* → *Run
workflow*) and publishes it as a GitHub Release.

The signing key `app/release.p12` is committed encrypted (PKCS12, AES-256).
Its password is the **`ANDROID_KEYSTORE_PASSWORD`** repository secret.
Keep a copy of it: without it, new versions can't be signed with the same key
and won't install over the existing app.
