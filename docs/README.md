# 🎒 Tomorrow's Bag — Notebook Tracker

A super simple, kid-friendly web app for tracking which notebooks/subjects your
child needs to bring to school the next day.

## How it works

- **Kid mode** 🧒 — greets the kid by name, shows their class, today's date
  and tomorrow's day/date, plus a big colorful grid of subject icons (Math,
  Science, Art, etc.). The kid just taps the ones they're bringing and hits
  **"All Packed!"**. There's a note field for extras like "PE shoes".
- **Parent mode** 🧑 — shows what's planned for tomorrow at a glance, lets
  you set the **kid's name, class/grade, and parent's name** (used for the
  personalized greeting), set a **usual weekly plan** per weekday (so the
  right subjects are suggested automatically each evening), **rename or
  re-icon any subject** (tap ✏️), add/remove subjects, and browse
  **history** of past days.
- **Holiday check (best-effort)** 🎉 — optionally pick a country in Parent
  mode and the app checks tomorrow's date against a public holiday calendar
  ([date.nager.at](https://date.nager.at)). If it looks like a holiday, both
  modes show a heads-up banner. This is a *general* public holiday
  calendar, not your specific school's calendar, so always double-check —
  it needs an internet connection and quietly does nothing if it can't
  reach the service.

Everything else is saved locally in the browser (`localStorage`), so the
app works fully offline and needs no sign-up, account, or server — just
open it on a shared family phone or tablet.

## Running it

Just open `index.html` in any browser — no build step, no dependencies.

This folder is also set up to be served directly by **GitHub Pages** (see
the repo root README for the one-time setup step), so the kid can bookmark
a real URL on their phone instead of a local file.
