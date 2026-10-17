# RSS Reader for Android

A native Android app for the [RSS Reader](../docs/rss-reader/), built as a
**Trusted Web Activity**: a small app shell that opens the live web app full
screen in Chrome's engine. Because it runs the real web app, it always has the
latest version, and Google sign-in, sync, offline reading and the share sheet
all keep working.

**Download:** <https://github.com/Akrishna87/Akrishna87/releases/latest/download/rss-reader.apk>

## Installing

1. Open the download link above on your Android phone.
2. Open the downloaded `rss-reader.apk`. If Android asks, allow your browser
   (or Files app) to **install unknown apps**, then tap **Install**.
3. Open **Reader** from your app drawer and sign in to sync (⚙ Settings).

Updates: download and install the newest APK the same way. It installs over
the old one and keeps your data. The reader itself updates on its own, since
it's the live web app; a new APK is only needed if this Android shell changes.

## How it's built

`.github/workflows/android-apk.yml` builds and signs the APK on every change
to `android/` (or on demand from the Actions tab → *Android APK* → *Run
workflow*) and publishes it as a GitHub Release.

### Signing

- `app/release.p12` is the app's signing key, encrypted (PKCS12, AES-256).
- Its password is the **`ANDROID_KEYSTORE_PASSWORD`** repository secret
  (Settings → Secrets and variables → Actions). Keep a copy of the password
  somewhere safe. Without it, future versions can't be signed with the
  same key and won't install over the existing app.
- Certificate SHA-256 fingerprint:
  `4A:EA:B4:00:4B:A2:B3:73:25:1C:D8:E3:FB:1A:DF:0A:B0:78:45:1D:C2:12:46:06:AE:E6:A0:0D:EF:D4:49:4D`

### No address bar: Digital Asset Links

Android only hides the browser address bar once the website confirms the app
belongs to it. That's the file
`https://akrishna87.github.io/.well-known/assetlinks.json`, served from the
`Akrishna87.github.io` repository (with an empty `.nojekyll` file so GitHub
Pages serves the `.well-known` folder):

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "io.github.akrishna87.rssreader",
      "sha256_cert_fingerprints": [
        "4A:EA:B4:00:4B:A2:B3:73:25:1C:D8:E3:FB:1A:DF:0A:B0:78:45:1D:C2:12:46:06:AE:E6:A0:0D:EF:D4:49:4D"
      ]
    }
  }
]
```

Until that file is live, the app works but shows a slim address bar at the top.
If it still shows after the file is up, clear the app's storage once
(Settings → Apps → Reader → Storage → Clear storage) so Android re-checks.
