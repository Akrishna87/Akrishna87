# Vellum: RSS reader for Android

A standalone Android app written in Kotlin with Jetpack Compose. It fetches,
parses and stores feeds itself, on the phone. It doesn't need Chrome, a
website or a relay.

The design follows Apple's iOS conventions: iOS system colours (light and
dark), iOS text sizes and weights, inset-grouped lists, large titles that
collapse into the navigation bar, a segmented All/Unread control, swipe
actions, bottom sheets and a reader-style article view. Text is set in
[Inter](https://rsms.me/inter/), an open-source typeface designed along the
same lines as Apple's San Francisco (which can't be bundled outside Apple
platforms). Inter is licensed under the SIL Open Font License 1.1; see
`third_party/Inter-LICENSE.txt`.

**Download:** <https://github.com/Akrishna87/Akrishna87/releases/latest/download/rss-reader.apk>

## Features

- Subscribe by feed URL **or website address**: the app finds the site's feed.
  YouTube channel, subreddit, GitHub project, Medium and Substack links work too.
- RSS 2.0, RSS 1.0/RDF and Atom, including podcast and YouTube feeds.
- **Discover feeds**: a directory of 38 popular feeds across 7 topics (Tech,
  Cloud & AI, News, India, Business, Science, Learning) with search and one-tap
  adding, plus quick add for YouTube channels, subreddits, GitHub releases,
  Mastodon accounts and Medium/Substack.
- **Folders**: group feeds (set when adding, from Discover by topic, or with
  ✎ in Manage feeds), each with a combined view and unread count; collapse
  them in the drawer. OPML import/export keeps folders.
- **Manage feeds**: rename, move to a folder, or unsubscribe (🗑).
- **Keyword filters**: *mute* words to hide matching articles, *highlight*
  words to tag them and collect them under Highlights.
- All / Starred / per-feed views, an "Unread only" filter, mark all as read.
- Reader-style article view with inline images and tappable links, and a
  toolbar for unread, star, share and open in browser.
- Swipe an article right to mark it read/unread, left to star it.
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
