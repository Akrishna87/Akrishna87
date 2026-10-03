# ⏳ Neram (நேரம்): screen time for Android

Neram (*neram*, the Tamil word for time) shows how long you use each app on
your phone, **by day, week and month**. There are no ads, no account and no
internet access: your usage never leaves the phone.

**Download:** the latest build is always the **Neram.apk** file on the
[`screen-time-latest` release](https://github.com/Akrishna87/Akrishna87/releases/tag/screen-time-latest).
Open it on your phone and allow installing from your browser when asked.
Each new build installs as an update over the last one.

## What it does

- **Day / Week / Month** tabs at the top, with ‹ › arrows to step back
  through earlier days, weeks or months.
- **Total screen time** for the period, plus the **daily average** for a week
  or month.
- **A bar chart**: screen time per hour for a day, per day for a week or a
  month. Tap a day's bar to open that day.
- **Every app you used**, longest first, with its icon, its time, a bar for its
  share, and how many times you opened it.
- **Tap an app** for its own page: its time and opens over the same day, week
  or month, a per-day chart, and a shortcut to its Android App info page.
- Dark and light themes follow the phone's setting.

## Setting it up

The first time you open Neram it asks for **Usage access**, a special
Android permission that apps can't just pop up a prompt for. Tap **Open Usage
access settings**, find Neram in the list, turn on **Permit usage access**
and come back. That's all.

## How it works, and a few honest notes

- Neram reads Android's own usage events (`UsageStatsManager`): an app's
  time runs from when it comes to the front until it leaves, or the screen
  turns off. Time on the home screen isn't counted. Leaving the home screen or
  another app for an app counts as opening it.
- **Android only keeps about a week of detailed history.** So on the first run
  Neram picks up the last ~10 days that Android still has, and from then on it
  saves your usage into its own database every few hours (and whenever you
  open it). Weeks and months fill in from the day you install it; the app
  shows "Tracking since …" at the bottom.
- Its numbers can differ a little from Digital Wellbeing's, which uses its
  own rules for things like split screen and picture-in-picture.
- History lives only on this phone and is left out of cloud backups, so it
  starts again after a factory reset or on a new phone.
- Work-profile apps are tracked separately by Android and don't appear.

## Building

This is a standard Gradle project (Kotlin, Jetpack Compose, Room,
WorkManager), built with JDK 17:

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

To try it on an emulator without tapping through Settings, grant access with
`adb shell appops set io.github.akrishna87.screentime GET_USAGE_STATS allow`.

CI (`.github/workflows/screen-time-apk.yml`) builds the release APK on every
push that touches `screen-time-android/`, signs it, runs it on an Android 14
emulator (`ci/smoke-test.sh`), and publishes it to the `screen-time-latest`
release with the emulator's screenshots.

**Signing:** release builds are signed with Isaialai's private key
(`music-player-android/signing/release.keystore.gpg`, unlocked in CI with the
`SIGNING_PASSPHRASE` secret), like Vaanalai. See
[`music-player-android/SECURITY.md`](../music-player-android/SECURITY.md).
