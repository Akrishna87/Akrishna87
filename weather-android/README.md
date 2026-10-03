# 🌀 Vaanilai (வானிலை): weather and windstorms for Android

Vaanilai (Tamil for *weather*) brings together the forecasts of weather
services from around the world and shows them side by side. It also has a
live **wind-flow map** that shows the world's **named storms** (hurricanes,
typhoons and cyclones) with their names, strength and tracks. There are no
ads, no account and no API keys.

**Download:** the latest build is always the **Vaanilai.apk** file on the
[`weather-latest` release](https://github.com/Akrishna87/Akrishna87/releases/tag/weather-latest).
Open it on your phone and allow installing from your browser when asked.
Each new build installs as an update over the last one.

## What it does

- **Weather**: right now, the next 48 hours and 7 days for your location
  or any place you search for. Shows temperature, feels-like, rain chance,
  wind and gusts (with the Beaufort scale), humidity, pressure, cloud, UV,
  sunrise and sunset.
- **Every source side by side**: "What each source says now" lists the
  current forecast from up to a dozen models and services, with the range
  they span:

  | Source | Run by |
  |---|---|
  | Open-Meteo best match | Blend of the best models for the place |
  | ECMWF IFS | European Centre for Medium-Range Weather Forecasts |
  | GFS | NOAA (USA) |
  | ICON | Deutscher Wetterdienst (Germany) |
  | GEM | Environment Canada |
  | JMA GSM | Japan Meteorological Agency |
  | ARPEGE | Météo-France |
  | UKMO | UK Met Office |
  | GRAPES | China Meteorological Administration |
  | ACCESS-G | Bureau of Meteorology (Australia) |
  | MET Norway | Norwegian Meteorological Institute (its own API) |
  | NWS | US National Weather Service (USA only) |

- **Air quality** (US and European AQI, PM2.5, PM10, ozone, NO₂, SO₂,
  dust) from Copernicus CAMS, and **waves and swell** for places near the sea.
- **Official alerts**: US National Weather Service watches and warnings,
  plus a banner when a named storm is within 2,000 km of your place
  ("Cyclone X is 620 km SE, moving NW at 15 km/h").
- **Wind map**: thousands of particles drift with the wind, coloured by
  speed from calm blue to hurricane-force white, over a world map. You can:
  - pinch and drag to explore (it wraps around the date line);
  - use the **time slider** to step through the next 48 hours;
  - switch between **Wind** and **Gusts**;
  - tap anywhere to read the wind speed and direction at that spot.
- **Named storms on the map**: each active tropical cyclone is drawn with a
  spiral symbol, its **name** and category, and its track and wind areas.
  Tap one for its details (wind, pressure, movement, distance from you)
  and a link to the official advisory.
- **Storms**: a list of every active named storm in every ocean.
- **Sources**: shows which data sources answered on the last refresh, how
  fast, and why any were skipped (for example "Only covers the USA"). The
  units can also be changed here (°C/°F; km/h, mph, m/s or knots).

## Where the data comes from

| Data | Source | Licence |
|---|---|---|
| Forecasts, models, wind grid, place search, air quality, waves | [Open-Meteo](https://open-meteo.com) | CC BY 4.0 |
| Forecast | [MET Norway](https://api.met.no) | CC BY 4.0 |
| US forecast and alerts | [National Weather Service](https://api.weather.gov) | Public domain |
| Tropical cyclones in every ocean, tracks and wind areas | [GDACS](https://www.gdacs.org) (UN / European Commission) | Free to use with credit |
| Atlantic and East/Central Pacific storms | [NOAA National Hurricane Center](https://www.nhc.noaa.gov) | Public domain |
| Coastlines and borders (bundled in the app) | [Natural Earth](https://www.naturalearthdata.com) | Public domain |

## A few honest notes

- **Which storms have names:** storm names come from the official
  tropical-cyclone feeds (GDACS and NHC). Europe's named winter windstorms
  (named by the UK, Irish, Dutch and other weather services) have no free
  data feed, so they aren't labelled. Their strong winds still show on the
  wind map, and the gust forecast on the Weather screen warns about them.
- GDACS reports a storm's **peak** wind so far, while NHC reports the
  **current** wind. The app says which one it is showing.
- The wind map asks Open-Meteo for about 150 grid points at a time, and the
  free service counts each point as one request. If you zoom and pan a lot,
  you may briefly see "Too many requests"; wait a minute and tap reload.
- Location is used only when you tap **Use my location**, and only a rough,
  city-level position is requested. Your place and settings stay on this
  phone and are left out of cloud backups.
- Always follow your national weather service's warnings for decisions
  about safety.

## Building

This is a standard Gradle project (Kotlin, Jetpack Compose), built with JDK 17:

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

The map outlines in `app/src/main/assets` are made by `tools/pack_map.py`
from Natural Earth's 1:50m data.

CI (`.github/workflows/weather-apk.yml`) builds the release APK on every push
that touches `weather-android/`, signs it, and runs it on an Android 14
emulator (`ci/smoke-test.sh`): it searches for a place, then opens the
forecast, wind map, storms and sources screens. It then publishes the APK to
the `weather-latest` release along with the emulator's screenshots.

**Signing:** release builds are signed with Isaialai's private key
(`music-player-android/signing/release.keystore.gpg`, unlocked in CI with the
`SIGNING_PASSPHRASE` secret), the same key as the other apps here. See
[`music-player-android/SECURITY.md`](../music-player-android/SECURITY.md).
