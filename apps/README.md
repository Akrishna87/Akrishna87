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
- **Details**: SHA-256 fingerprint, the latest change (the commit message of that build),
  a QR code to open the app's page on another phone, screenshots from the automatic emulator test,
  and older builds (where the release keeps them).
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

The page needs the repo's release to contain an `.apk` file. Everything else (version, size, date,
fingerprint, screenshots) is read from the release itself.

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

3. **Check on launch** and show a button:

   ```kotlin
   val updater = AppUpdater(context, "Akrishna87", "isaialai", "music-player-latest")

   // in a coroutine, e.g. viewModelScope.launch { ... }
   val update = updater.checkForUpdate()          // null when up to date
   if (update != null) { /* show "Update to ${update.versionName}" */ }

   // when the user taps it:
   if (!updater.canInstall()) updater.openInstallPermissionScreen()   // one-time Android setting
   else updater.downloadAndInstall(update) { percent -> /* progress */ }
   ```

   Android then shows its own "Do you want to update this app?" screen. The download is checked
   against the SHA-256 in `version.json` first, and Android refuses it unless it is signed with the same
   key as the installed app.

> `AppUpdater.kt` has not been compiled or run on a device yet. Try it in one app first
> (Isaialai is a good one, since its CI already runs an emulator smoke test).

## Limits worth knowing

- **No silent installs.** Outside the Play Store, Android always asks the user to confirm.
- **Same signing key every time.** Build 31 can only update build 30 if both are signed with the same key. Each app's
  CI signs with a fixed key for this reason (the debug apps commit a `debug.keystore`).
- **`versionCode` must go up** with every build.
- **GitHub's rate limit.** The page makes one request per app, and GitHub allows 60 per hour per
  network without signing in. The browser keeps each answer for 10 minutes, and shows the saved
  copy if GitHub says no, so a couple of friends will never notice. If it ever matters, a scheduled
  workflow could write a `builds.json` into this repo instead.
- **Public repos only.** The page reads releases without logging in, and anyone with the link can
  download. A private repo's APKs can't be served this way.

## Files

| File | What it is |
|---|---|
| `index.html`, `style.css`, `app.js` | The page |
| `apps.json` | The list of apps |
| `vendor/qrcode.js` | [qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator) 1.4.4 (MIT), draws the QR code in the browser |
| `android/AppUpdater.kt` | Drop-in code for the in-app update check and install |
