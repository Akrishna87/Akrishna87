# 📒✅ Daybook for Android

Daybook is **notes and tasks in one app**: Evernote-style notebooks and notes, with
Todoist-style tasks and Quick Add. A note can hold the tasks that come out of it, so a
meeting's notes and its action items stay together. Everything stays on your phone:
there are no accounts, no ads and no internet access.

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/Akrishna87/releases/tag/daybook-latest)
   and tap **Daybook.apk** to download it.
2. Open the downloaded file. Android will ask you to allow installing apps
   from your browser (or Files app). Allow it, then tap **Install**.
   Google Play Protect may warn that it doesn't recognise the app, because
   it isn't from the Play Store. Tap **More details → Install anyway**.
3. Open **Daybook**.

To update, install a newer `Daybook.apk` the same way. It installs over the old one and
keeps everything. (Calendar events from the first version of Daybook become tasks with
the same date and time.)

## Tasks

- **Quick Add**: tap **+** and type a task in plain words. Daybook highlights what it
  understands as you type:

  | Type | For |
  |---|---|
  | `today`, `tomorrow`, `friday`, `next week`, `this weekend`, `in 3 days`, `dec 25`, `2026-12-25` | the due date |
  | `9am`, `6:30pm`, `21:00`, `at 5`, `noon`, `tonight` | the time (and a reminder) |
  | `p1` `p2` `p3` | priority, as in Todoist (red, orange, blue) |
  | `#Work` | an existing project |
  | `@errands` | labels (new ones are made as you go) |
  | `every day`, `every weekday`, `every monday`, `weekly`, `every month`, `yearly` | repeating tasks |

  For example: `Pay rent friday 9am p1 #Home @bills every month`. The chips under the
  text set the same things by hand, and the panel stays open for the next task.
- **Today** shows overdue tasks (with **Reschedule** to move them all to today), then
  today's tasks, and a progress bar for the day.
- **Upcoming** lists the next four weeks day by day, with a week strip to jump around and
  a **+** on each day.
- **Inbox and projects**: tasks without a project go to the Inbox. Make projects with a
  colour in **Browse**. **Labels** collect tasks across projects.
- **Task details**: tap a task to change its title, description, date, time, repeat,
  priority, project and labels, switch its reminder on or off, and add **sub-tasks**.
- **Swipe** a task right to complete it or left to delete it, with **Undo** either way.
  Completing a repeating task moves it to its next date. **Completed** in Browse keeps a
  history you can bring tasks back from.
- **Reminders** notify you at a task's time, with a **Complete** button on the
  notification. Android asks for notification permission the first time you add a task
  with a time.

## Notes

- **Notebooks**: every note lives in a notebook. Make, rename and delete notebooks from
  the Notes tab, and filter by notebook or **#tag**.
- **Writing**: notes open ready to read. Tap the text (or the pencil) to edit, with a
  toolbar for **checklists**, bulleted and numbered lists, headings, bold, italic,
  strikethrough and dividers. Lists carry on when you press enter, and pressing enter on
  an empty item ends the list. Notes save as you type.
- **Checklists** tick off right in the note, and the list shows progress like `2/5`.
- **Tasks from notes**: at the bottom of every note, **Add task** makes a task linked to
  the note. The task shows a note icon and opens the note from its details.
- **Pin** notes to the top, **sort** by date updated, date created or title, **share** a
  note as text, and **Trash** keeps deleted notes until you restore them or empty it.

## Everything else

- **Search** (the magnifier on any tab) finds tasks by title, description or label, and
  notes by title, text or tag.
- **Light and dark themes**, following the phone or chosen in Settings.
- **Backup**: Settings → Export backup saves everything to a JSON file, and Restore from
  backup reads one back.
- **Home screen widget** ("Daybook: Today"): today's and overdue tasks. Tap a circle to
  complete a task, or + to open Quick Add.

## How it's built

Kotlin and Jetpack Compose with Material 3, and no libraries beyond AndroidX.

- `model/` holds the data and the logic, all plain Kotlin with unit tests:
  `QuickAdd.kt` reads plain-word tasks, `Dates.kt` handles due dates, repeats and task
  order, and `Markdown.kt` parses notes and powers the editing toolbar.
- `Store.kt` keeps everything in memory and saves it to one JSON file, `Reminders.kt`
  schedules notifications, and `Backup.kt` exports and restores.
- `ui/` has the screens. `widget/TodayWidget.kt` is the home screen widget.
- `ci/smoke-test.sh` runs on an Android emulator for every build. It adds tasks with
  Quick Add, completes one, adds a sub-task and a project, writes a note with a checklist,
  searches, and switches to the dark theme. Screenshots from that run are attached to
  each release.

Builds are made by the [Daybook APK workflow](../.github/workflows/daybook-apk.yml) on
every push that touches `daybook-android/`.
