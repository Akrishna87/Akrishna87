# 📲 Aravind's Android apps: download page

A small website where friends can install my Android apps and get new builds as soon as
they're ready.

**Live:** https://akrishna87.github.io/Akrishna87/apps/

There is no server and no build step. The page is plain HTML, CSS and JavaScript. When it opens,
it asks GitHub for the latest release of each app listed in [`apps.json`](apps.json), so it is
always up to date without anyone editing it.

## What a friend sees

- A card per app with its version, size, and when that build was made.
- **Install** (first time) / **Update** (when a newer build exists than the one they last
  downloaded on that browser) / **Download again**.
- **Details**, laid out like a Play Store listing: icon, name, a stats row (size, build or version,
  updated, downloads), Install and Share, screenshots from the automatic emulator test, **About this
  app** (from the app's README, with Read more and Highlights), **What's new** (the recent commit
  messages that went into the build), **App info** (version, size, file, SHA-256), a QR code to open
  the app's page on another phone, and older builds (where the release keeps them).
- A short how-to-install guide, including the Play Protect warning.
- Works on phones and computers, in light and dark mode. Each app has its own link,
  for example `…/apps/?app=isaialai`.

## Adding or changing an app

Edit [`apps.json`](apps.json) and add an entry:

```json
{
  "repo": "my-new-app",
  "tag": "latest",
  "apk": "MyNewApp.apk",
  "name": "My New App",
  "nameLocal": "optional name in another script",
  "tagline": "One sentence on what it does.",
  "emoji": "🧩",
  "hue": 200,
  "channel": "debug"
}
```

| Field | Meaning |
|---|---|
| `repo` | Repository name under `owner`. It must be public. |
| `tag` | The release tag the CI publishes to. Leave it out to use the newest release that has an APK. |
| `apk` | Preferred file name. If it isn't there, the highest `…-vNN.apk` is used. |
| `emoji`, `hue` | The icon tile (0–360 is the colour). |
| `channel` | `"debug"` shows a "Test build" label. Leave it out for normal builds. |
| `group` | Heading the card is listed under. Apps are shown in file order. |
| `id` | Name used for the card, deep link (`?app=<id>`) and saved state. Defaults to `repo`. Needed when several apps share one repo. |
| `tagPrefix` | Use the newest release whose tag starts with this (for apps with versioned tags). |
| `readme`, `path` | README to build "About this app" from (default `README.md`), and the folder whose commits make up "What's new". For apps that live inside a larger repo. |
| `hideShots` | Words in screenshot file names to leave out of Details, for test screens that don't show the app itself (for example a test video playing). |
| `about` | Optional text that replaces the README-derived description. |
| `note` | Optional line shown at the top of the app's Details, for example which of two APKs to use. |

The page needs the repo's release to contain an `.apk` file. Everything else (version, size, date,
fingerprint, screenshots) is read from the release itself.

## Icons and saved text

- **Icons** are each app's own launcher icon, drawn from its repository (`icons/<id>.svg`). After an
  app's icon changes, run `python3 tools/build-icons.py` and commit the result. The emoji in
  `apps.json` is only shown if an icon can't load.
- **Description and What's new** appear straight away from `content.json`, then the page asks GitHub
  and replaces them when it has a newer answer. If GitHub is out of requests, the saved text stays
  on screen. Refresh it with `python3 tools/build-content.py`.

## How a new build reaches friends

```
push to app repo → GitHub Actions builds + signs the APK → publishes the release
                                                           ↓
              friend opens this page (or the app itself checks) → taps Update
```

Nothing has to be done on this site when an app has a new build. The apps' workflows already
publish to a rolling release, and the page reads whatever is there.

## Updating from inside the app

Friends can also be offered the update inside the app, without coming back here:

1. **Publish `version.json`** with each build. Add this step to the app's `publish` job, just
   before `gh release create`, and add `out/version.json` to the files that command uploads.
   Use the same number your Gradle build puts in `versionCode`:

   ```yaml
   - name: Write version.json for the in-app updater
     run: |
       apk=out/Isaialai.apk
       cat > out/version.json <<EOF
       {
         "versionCode": ${{ github.run_number }},
         "versionName": "1.0.${{ github.run_number }}",
         "apk": "$(basename "$apk")",
         "sha256": "$(sha256sum "$apk" | cut -d' ' -f1)",
         "size": $(stat -c %s "$apk")
       }
       EOF
   ```

2. **Copy [`android/AppUpdater.kt`](android/AppUpdater.kt)** into the app and add to
   `AndroidManifest.xml`:

   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

   <application ...>
       <receiver android:name=".updater.InstallResultReceiver" android:exported="false" />
   </application>
   ```

   (Alaimaani has no `INTERNET` permission today, which is part of what it promises. Add the
   updater there only if you're happy to give that up.)

3. **Check for updates** (on launch, or only when the user taps a button) and show the answer:

   ```kotlin
   val updater = AppUpdater(context, "Akrishna87", "isaialai", "music-player-latest")

   // in a coroutine, e.g. viewModelScope.launch { ... }
   when (val result = updater.checkForUpdate()) {
       is UpdateCheck.Available -> { /* show "Update to ${result.update.versionName}" */ }
       UpdateCheck.UpToDate -> { /* "You have the latest build" */ }
       is UpdateCheck.Failed -> { /* show result.reason, e.g. "No connection to GitHub right now" */ }
   }

   // when the user taps the update button:
   if (!updater.canInstall()) updater.openInstallPermissionScreen()   // one-time Android setting
   else updater.downloadAndInstall(update) { percent -> /* progress */ }
   ```

   Android then shows its own "Do you want to update this app?" screen. The download is checked
   against the SHA-256 in `version.json` first, and Android refuses it unless it is signed with the same
   key as the installed app.

> **Status.** Isaialai is the first app to use it (Settings → App updates → Check for updates; see
> `ui/UpdateCard.kt` and `ci/updater-test.sh` in that repo for a worked example). The code compiles in a
> real release build, and its check runs on the emulator in Isaialai's CI, where it reaches GitHub and
> shows the right message. The download-and-install half can only be tried once a newer build than the
> installed one is published, so it hasn't been exercised yet.
>
> Isaialai also promised "no internet access", which the updater can't keep. It now says it goes
> online only when you tap **Check for updates** (see its `SECURITY.md`). Decide that trade for each app.

## Limits worth knowing

- **No silent installs.** Outside the Play Store, Android always asks the user to confirm.
- **Same signing key every time.** Build 31 can only update build 30 if both are signed with the same key. Each app's
  CI signs with a fixed key for this reason (the debug apps commit a `debug.keystore`).
- **`versionCode` must go up** with every build.
- **GitHub's rate limit.** The page makes one request per app (21 today), and GitHub allows 60 per
  hour per network without signing in. The browser keeps each answer for 10 minutes, repeat checks
  that find nothing new don't count against the limit, and the page shows the saved
  copy if GitHub says no, so a couple of friends will never notice. If it ever matters, a scheduled
  workflow could write a `builds.json` into this repo instead.
- **Public repos only.** The page reads releases without logging in, and anyone with the link can
  download. A private repo's APKs can't be served this way.

## Files

| File | What it is |
|---|---|
| `index.html`, `style.css`, `app.js` | The page |
| `apps.json` | The list of apps |
| `icons/` | The app icons (made by `tools/build-icons.py`) |
| `content.json` | Saved descriptions and changes (made by `tools/build-content.py`) |
| `vendor/qrcode.js` | [qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator) 1.4.4 (MIT), draws the QR code in the browser |
| `android/AppUpdater.kt` | Drop-in code for the in-app update check and install |
