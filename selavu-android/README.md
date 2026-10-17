# Selavu (செலவு): an expense manager for Android

Log what you spend and what you earn, see where the money went, and know how much is left in
your budget. Everything stays on the phone: no account, no bank linking, and no `INTERNET`
permission.

**Download:** the latest build is the `Selavu.apk` in the
[`selavu-latest` release](https://github.com/Akrishna87/Akrishna87/releases/tag/selavu-latest), or from the
[download page](https://akrishna87.github.io/Akrishna87/apps/).

## What it does

**Home**
- Day, Week, Month or Year at the top; ‹ › step through them, and tapping the label goes back to today
- Balance for the period, with income and spending under it
- Every entry, grouped by day with each day's total; tap one to change or delete it
- Big **Expense** and **Income** buttons at the bottom

**Adding an entry**
- Amount, category (the last one you used is already picked), date (Today, Yesterday or any day), and a note
- **Repeat** every day, week, month or year: rent, salary, subscriptions and EMIs are then logged on
  their own when they come due. A monthly entry on the 31st lands on the last day of shorter months.
- Long-press the app icon for **Add expense** and **Add income** shortcuts

**Budget**
- One spending budget, weekly or monthly
- Home shows how much is left, a bar that turns amber then red, and how much you can spend a day for
  the rest of the period

**Reports**
- A donut chart of spending (or income) by category, with each category's amount and share
- Bars by day (for a week or month) or by month (for a year), and the average a day
- Tap a category to see its entries

**Settings**
- Categories: add, rename, change the emoji and colour, or remove one (its entries move to another)
- Repeating entries: see what's coming next, and stop any of them
- Currency symbol (₹ by default, with Indian digit grouping: ₹1,25,000), and whether weeks start on Monday or Sunday
- **Export to CSV** (Date, Type, Category, Amount, Note) for Excel, Google Sheets or a backup, and
  **Import from CSV**, which skips entries already there

## How it's built

Kotlin and Jetpack Compose (Material 3), light and dark themes following the phone. The ledger is one
JSON file in the app's private storage, written atomically with the previous copy kept as a backup.
The money, period, budget, repeat and CSV logic lives in `core/` with no Android code, and is covered
by unit tests (`app/src/test`).

## Building

```
./gradlew testReleaseUnitTest assembleRelease
```

The [workflow](../.github/workflows/selavu-apk.yml) runs the unit tests, builds the APK, installs it on
an Android 14 emulator and runs [`ci/smoke-test.sh`](ci/smoke-test.sh) (add an expense and an income,
a repeating entry, a budget, the reports, delete, restart, the shortcut), then publishes the APK with
the test's screenshots as the `selavu-latest` release.
