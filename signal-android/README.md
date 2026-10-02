# 📶 Alaimaani (அலைமானி): signal meter for Android

Alaimaani (*alai*, wave + *maani*, meter, as in வெப்பமானி, thermometer) shows how
good your mobile signal really is: the actual numbers behind the bars, in plain words.
There are no ads, no account, and no internet access at all.

**Download:** the latest build is always the **Alaimaani.apk** file on the
[`signal-latest` release](https://github.com/Akrishna87/Akrishna87/releases/tag/signal-latest).
Open it on your phone and allow installing from your browser when asked.
Each new build installs as an update over the last one.

## What it does

- **Signal** shows a live gauge of your signal strength in dBm (RSRP on 4G/5G,
  RSCP on 3G, RSSI on 2G), coloured from excellent to very poor, with a one-line
  verdict on what it means for calls and data.
- **Quality** (SINR and RSRQ), with a note on why full bars can still mean slow data.
- **4G, 4G+, 5G, or 5G on 4G (NSA)**, the way the operator actually connects you.
  On 5G-on-4G it also shows the 5G layer's own strength.
- **Last 10 minutes** graph, with the lowest, average and highest readings.
- **Tower details:** band and frequency (e.g. B3 · 1850 MHz, n78 · 3549.6 MHz),
  EARFCN/NR-ARFCN, PCI, eNB/gNB site, cell ID, TAC and network code.
- **Other towers nearby** that the phone can hear, strongest first.
- **Dual SIM:** switch between SIMs at the top of the screen.
- **Best spot:** stand somewhere, tap *Measure a spot*, give it a name
  (Bedroom, Balcony…) and hold still for 20 seconds. Alaimaani averages the
  signal there and ranks your spots, separately for each SIM. The screen stays on
  while measuring.
- **Guide:** what the numbers mean, the difference between 5G kinds, low vs high
  bands, and what to do about a weak signal.

## Permissions

- **Phone** (`READ_PHONE_STATE`): to read each SIM and whether it's on 4G or 5G.
- **Location** (`ACCESS_FINE_LOCATION`): Android only shares tower details
  (band, cell ID, nearby towers) with apps that have location permission, and
  only while the phone's Location switch is on. Alaimaani never reads or saves
  where you are, and has no `INTERNET` permission.

Without them it still shows the main signal reading for the default SIM.

## Limits

- Android updates the readings every second or two at best, and less often with
  the screen off. Readings normally jump by a few dBm; look at the graph.
- Some phones leave numbers out (often SINR, or the 5G layer when no data is
  flowing); those just aren't shown.
- The app needs a real phone with a SIM. The emulator only reports a simulated signal.

## Building

This is a standard Gradle project (Kotlin, Jetpack Compose), built with JDK 17:

```sh
./gradlew testDebugUnitTest    # rating, band and spot-ranking tests
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/signal-apk.yml`) runs the unit tests and builds the
release APK on every push that touches `signal-android/`, signs it, runs it on
an Android 14 emulator (`ci/smoke-test.sh`), and publishes it to the
`signal-latest` release with the emulator's screenshots.

**Signing:** release builds are signed with Isaialai's private key
(`music-player-android/signing/release.keystore.gpg`, unlocked in CI with the
`SIGNING_PASSPHRASE` secret), so no public key is ever used. See
[`music-player-android/SECURITY.md`](../music-player-android/SECURITY.md).
