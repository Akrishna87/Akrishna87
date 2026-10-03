# 📅 Daybook for Android

Daybook keeps your **calendar, to-do list and notes** in one place and puts your day on
the **lock screen**. The month, today's plan, how much of the year has gone and a Today /
Tomorrow / Tasks card sit under the clock, so you see them every time you pick up the
phone. Nothing leaves the phone: there are no accounts, no ads and no internet access.

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/Akrishna87/releases/tag/daybook-latest)
   and tap **Daybook.apk** to download it.
2. Open the downloaded file. Android will ask you to allow installing apps
   from your browser (or Files app). Allow it, then tap **Install**.
   Google Play Protect may warn that it doesn't recognise the app, because
   it isn't from the Play Store. Tap **More details → Install anyway**.
3. Open **Daybook**.

To update, install a newer `Daybook.apk` the same way. It installs over the
old one and keeps everything you've added.

## Putting it on the lock screen

Android doesn't let apps add widgets under the lock screen clock the way an iPhone does.
It does let a **live wallpaper** draw there, so Daybook draws your day as the wallpaper:

1. In Daybook, open **Today** and tap the 🔒 button at the top (or the "Put Daybook on
   your lock screen" card).
2. Check the preview, then tap **Set as lock screen**.
3. On Android's wallpaper screen tap **Set wallpaper**, and choose **Home and lock
   screens** if it asks.

Android still draws its own clock, notifications and shortcut buttons on top. Use
**Start below the clock** to move the calendar so it sits just under your phone's clock.
When the phone is unlocked, the home screen shows only the background, so your icons stay
clear. Switch on **Show on the home screen too** if you want the layout there as well.

You can also pick the background (six colours, or **Use a photo** from your gallery,
darkened so the text stays readable) and choose what to show: the month calendar,
today's plan, year progress and the Today / Tomorrow / Tasks card.

## Features

- **Today** looks like the lock screen: a big clock, the month with today circled and a
  dot under each day that has something on, today's events with their coloured bars and
  times (finished events fade), the year's progress ("75% of this year has passed · 90
  days left"), and the Today / Tomorrow / Tasks card. Tick tasks off right there.
- **Calendar**: swipe or use the arrows to change month and tap a day to see its events.
  **New event** adds one with a title, date, start and end time (or all day) and a colour.
  Tap an event to change or delete it.
- **Your phone's calendars**: turn on **Show my phone's calendars** in the lock screen
  settings to show Google Calendar events, public holidays and so on next to Daybook's
  own events, in the app, on the lock screen and in the widget. Daybook only reads them.
  Change those events in your calendar app.
- **Tasks**: type a task and press enter. Tap the box to tick it off, or tap the task to
  give it a due day (Today, Tomorrow or any date) or a colour, or to delete it. Overdue
  tasks are marked, and **Clear** removes the finished ones.
- **Notes**: cards in two columns with pinned notes first, a search box, and a colour
  for each note. A note saves when you go back; one you leave empty is thrown away.
- **Home screen widget** ("Daybook: Today and tasks"): the same Today / Tomorrow / Tasks
  card. Tap a task's box to tick it off, or anywhere else to open the app.

## How it's built

Kotlin and Jetpack Compose, with no libraries beyond AndroidX.

- `model/` holds the events, tasks, notes and settings (saved as one JSON file by
  `Store.kt`) and `Agenda.kt`, the calendar sums shared by everything: the month grid,
  the order of a day's events, time labels, year progress and task order.
- `lock/LockRenderer.kt` draws the lock screen on an Android `Canvas`, sized in 1/400ths
  of the screen width so it looks the same on every phone. `LockWallpaperService` uses it
  as a live wallpaper and redraws when something changes, once a minute while it's on
  screen, and when the phone locks or unlocks. The Settings preview uses the same code.
- `widget/AgendaWidget.kt` is the home screen widget.
- `DeviceCalendar.kt` reads the phone's calendars through Android's calendar provider.
- `ci/smoke-test.sh` runs on an Android emulator for every build. It adds tasks, an event
  and a note, ticks a task off, checks Today shows them, sets the wallpaper and
  photographs the lock screen. Screenshots from that run are attached to each release.

Builds are made by the
[Daybook APK workflow](../.github/workflows/daybook-apk.yml) on every push that touches
`daybook-android/`.
