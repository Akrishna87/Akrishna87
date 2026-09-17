# World Clock

A small, dependency-free web app for live world clocks and timezone conversion. Installable to the home screen on both iOS and Android.

## Features

- Live clocks for India (`Asia/Kolkata`) and London (`Europe/London`), updating every second.
- London automatically switches between GMT and BST — daylight saving is handled by the browser's timezone database, not hardcoded.
- Add any other IANA timezone as an extra clock; your selection is saved in `localStorage` so it's still there next time you open the app.
- Time converter: pick a date, time, and source timezone (e.g. "10 pm in London"), and see the equivalent time in India and every other clock you've added.
- Mobile-friendly layout, and installable as a home-screen app (see below).

## Running it

All the markup, CSS, and JS live in the single `index.html` file — no build step required. Just open it directly in a browser. `manifest.json`, `sw.js`, and `icons/` are optional extras that only matter when the app is served over http(s) (e.g. via GitHub Pages or `python3 -m http.server 8000`) — they enable "Add to Home Screen" installability and offline support, and are silently skipped when you open `index.html` straight from disk.

## Installing on your phone

- **iOS (Safari)**: open the site, tap the Share icon, then "Add to Home Screen". It gets its own icon and opens full-screen, no App Store or Apple Developer account needed.
- **Android (Chrome)**: open the site, tap the ⋮ menu, then "Add to Home screen" / "Install app". Chrome may also offer an install prompt automatically.

## How the conversion works

JavaScript's `Date` object has no built-in way to interpret a wall-clock time as "this time in timezone X". The inline script works around that by:

1. Treating the entered date/time as if it were UTC (a first guess at the instant).
2. Formatting that guess in the target timezone and comparing it to what was actually entered.
3. Correcting the guess by the difference (repeated once more to handle DST transition edges).

This uses only the standard `Intl.DateTimeFormat` API, so it correctly accounts for daylight saving in any timezone without any external library or timezone data file.
