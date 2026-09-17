# World Clock

A small, dependency-free web app for live world clocks and timezone conversion.

## Features

- Live clocks for India (`Asia/Kolkata`) and London (`Europe/London`), updating every second.
- London automatically switches between GMT and BST — daylight saving is handled by the browser's timezone database, not hardcoded.
- Add any other IANA timezone as an extra clock; your selection is saved in `localStorage` so it's still there next time you open the app.
- Time converter: pick a date, time, and source timezone (e.g. "10 pm in London"), and see the equivalent time in India and every other clock you've added.

## Running it

No build step or server required — just open `index.html` in a browser. Optionally serve it locally:

```sh
python3 -m http.server 8000
```

then visit `http://localhost:8000`.

## How the conversion works

JavaScript's `Date` object has no built-in way to interpret a wall-clock time as "this time in timezone X". `script.js` works around that by:

1. Treating the entered date/time as if it were UTC (a first guess at the instant).
2. Formatting that guess in the target timezone and comparing it to what was actually entered.
3. Correcting the guess by the difference (repeated once more to handle DST transition edges).

This uses only the standard `Intl.DateTimeFormat` API, so it correctly accounts for daylight saving in any timezone without any external library or timezone data file.
