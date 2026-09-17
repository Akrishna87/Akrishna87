# World Clock

A small, dependency-free, single-file web app for live world clocks and timezone conversion.

## Features

- Live clocks for India (`Asia/Kolkata`) and London (`Europe/London`), updating every second.
- London automatically switches between GMT and BST — daylight saving is handled by the browser's timezone database, not hardcoded.
- Add any other IANA timezone as an extra clock; your selection is saved in `localStorage` so it's still there next time you open the app.
- Time converter: pick a date, time, and source timezone (e.g. "10 pm in London"), and see the equivalent time in India and every other clock you've added.

## Running it

Everything (markup, CSS, JS) lives in the single `index.html` file — no build step, no server, and no other files it depends on. Just open it directly in a browser, or serve it (e.g. via GitHub Pages, or `python3 -m http.server 8000` locally) if you'd rather visit it as a URL.

## How the conversion works

JavaScript's `Date` object has no built-in way to interpret a wall-clock time as "this time in timezone X". The inline script works around that by:

1. Treating the entered date/time as if it were UTC (a first guess at the instant).
2. Formatting that guess in the target timezone and comparing it to what was actually entered.
3. Correcting the guess by the difference (repeated once more to handle DST transition edges).

This uses only the standard `Intl.DateTimeFormat` API, so it correctly accounts for daylight saving in any timezone without any external library or timezone data file.
