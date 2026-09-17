# 🎒 Tomorrow's Bag — Notebook Tracker

A super simple, kid-friendly web app for tracking which notebooks/subjects your
child needs to bring to school the next day.

## How it works

- **Kid mode** 🧒 — greets the kid by name, shows their class, today's date
  and tomorrow's day/date, plus a big colorful grid of subject icons (Math,
  Science, Art, etc.). The kid just taps the ones they're bringing and hits
  **"All Packed!"**. There's a note field for extras like "PE shoes".
- **Parent mode** 🧑 — shows what's planned for tomorrow at a glance, lets
  you set the **kid's name, class/grade, parent's name, and school
  location** (used for the personalized greeting), set a **usual weekly
  plan** per weekday (so the right subjects are suggested automatically
  each evening), **rename or re-icon any subject** (tap ✏️), add/remove
  subjects, and browse **history** of past days.
- **School holidays** 🏫 — pre-loaded with DPS Gurgaon's published 2026–27
  holiday list, editable in Parent mode (add, rename, or remove any date).
  Since no public API covers school-specific closures, these are entered
  once by hand and then take priority over the generic public holiday
  guess: if tomorrow matches one, both modes show a confident "No school
  tomorrow" banner instead of the softer "might be a holiday" one.
- **Public holiday check (best-effort)** 🎉 — pick a country in Parent mode
  (India by default) and the app checks tomorrow's date against a public
  holiday calendar ([date.nager.at](https://date.nager.at)), plus shows a
  list of **upcoming public holidays**. This is a *national* calendar only
  — it has no concept of a specific city, state, or school, so it's a
  fallback for whatever isn't already covered by the school holidays list
  above. It needs an internet connection and clearly reports when it can't
  reach the service (rather than hanging on "loading").

Everything else is saved locally in the browser (`localStorage`), so the
app works fully offline and needs no sign-up, account, or server — just
open it on a shared family phone or tablet.

## Running it

Just open `index.html` in any browser — no build step, no dependencies.

This folder is also set up to be served directly by **GitHub Pages** (see
the repo root README for the one-time setup step), so the kid can bookmark
a real URL on their phone instead of a local file.
