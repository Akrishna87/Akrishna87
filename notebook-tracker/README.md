# 🎒 Tomorrow's Bag — Notebook Tracker

A super simple, kid-friendly web app for tracking which notebooks/subjects your
child needs to bring to school the next day.

## How it works

- **Kid mode** 🧒 — shows today's date and tomorrow's day/date, plus a big
  colorful grid of subject icons (Math, Science, Art, etc.). The kid just
  taps the ones they're bringing and hits **"All Packed!"**. There's a note
  field for extras like "PE shoes".
- **Mom mode** 👩 — shows what's planned for tomorrow at a glance, lets you
  set a **usual weekly plan** per weekday (so the right subjects are
  suggested automatically each evening), **rename or re-icon any subject**
  (tap ✏️), add/remove subjects, and browse **history** of past days.
- **Holiday check (best-effort)** 🎉 — optionally pick a country in Mom
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

Just open `index.html` in any browser, or host the file (e.g. via GitHub
Pages) so it's easy for the kid to bookmark on their device.

No build step, no dependencies.
