# Meal Plan

A small, dependency-free web app for planning breakfast, lunch, and dinner for
the week, with a shopping list generated automatically from what you planned.
Installable to the home screen on both iOS and Android.

## Features

- Weekly planner: pick a recipe (or type a one-off custom meal, like
  "leftovers" or "eating out") for breakfast, lunch, and dinner on every day
  of the week. Navigate between weeks with ◀ / ▶, or jump back to the
  current one with **Today**.
- Recipe library: keep a reusable list of meals with an emoji, a name, and a
  comma-separated ingredient list. These are the quick picks offered when
  filling in the planner.
- Shopping list: automatically built from the ingredients of every recipe
  you've assigned to the current week, deduplicated and checkable. Add extra
  items that aren't tied to a recipe (like milk or coffee), check things off
  as you shop, and copy the whole list to your clipboard.
- Everything is saved in `localStorage`, so your recipes and plans are still
  there next time you open the app — no account or internet connection
  needed.
- Mobile-friendly layout, and installable as a home-screen app (see below).

## Running it

All the markup, CSS, and JS live in the single `index.html` file — no build
step required. Just open it directly in a browser. `manifest.json`, `sw.js`,
and `icons/` are optional extras that only matter when the app is served over
http(s) (e.g. via GitHub Pages or `python3 -m http.server 8000`) — they
enable "Add to Home Screen" installability and offline support, and are
silently skipped when you open `index.html` straight from disk.

## Installing on your phone

- **iOS (Safari)**: open the site, tap the Share icon, then "Add to Home
  Screen". It gets its own icon and opens full-screen, no App Store or Apple
  Developer account needed.
- **Android (Chrome)**: open the site, tap the ⋮ menu, then "Add to Home
  screen" / "Install app". Chrome may also offer an install prompt
  automatically.

## A few honest notes

- Data is saved only on the device/browser you're using it on — opening the
  app on a different phone or browser starts fresh.
- Only meals picked from your recipe library carry ingredients into the
  shopping list automatically; custom/typed-in meals don't, since there's no
  ingredient data attached to them.
