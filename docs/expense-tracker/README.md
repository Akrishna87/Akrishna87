# 💰 Expense Tracker

A small, dependency-free web app for tracking household income and
expenses — two salaries, recurring bills (loan EMI, mutual fund SIP, RD,
a bi-monthly EB bill), category budgets, and full history. Installable
to the home screen on both iOS and Android.

## Features

- **Income & expenses** — log entries with amount, date, note, category
  (expenses) and payment method (expenses). Income entries just need a
  source name (e.g. "Salary – Primary", "Salary – Spouse").
- **Recurring items** — set up a recurring income or expense once (name,
  amount, frequency, first due date): every month, every 2 months (e.g.
  EB Bill), every 3/6 months, or yearly. When a period comes due it shows
  up on the Dashboard under **🔔 Due now**, where you confirm it (amount
  and date are editable at that point, in case a bill varies) or skip
  that one period.
- **Dashboard** — pick any month to see total income, total expenses,
  net, and a category-by-category breakdown with a budget progress bar
  (green / amber / red) for any category you've set a budget on.
- **Editable categories** — add, rename, or delete categories, each with
  its own emoji and an optional monthly budget. Comes pre-loaded with
  common ones (Housing, Loan EMI, Mutual Fund, RD, EB Bill, Groceries,
  etc.) — edit freely.
- **History** — every entry, filterable by type, category, month, and a
  text search over notes. Edit or delete any entry from here.
- **Works offline / installable** — see below.
- **Optional cloud sync** — off by default (fully local, `localStorage`
  only). Turn it on to sync your ledger across your phone and laptop via
  your own Google sign-in — see **Cloud sync setup** below.

## Running it

All the markup, CSS, and JS live in the single `index.html` file — no
build step required. Just open it directly in a browser. `manifest.json`,
`sw.js`, and `icons/` are optional extras that only matter when the app
is served over http(s) (e.g. via GitHub Pages or `python3 -m http.server
8000`) — they enable "Add to Home Screen" installability and offline
support, and are silently skipped when you open `index.html` straight
from disk.

## Installing on your phone

- **iOS (Safari)**: open the site, tap the Share icon, then "Add to Home
  Screen".
- **Android (Chrome)**: open the site, tap the ⋮ menu, then "Add to Home
  screen" / "Install app".

## How recurring items work

Each recurring item has a **first due date** and a **frequency** (every
N months). The app computes every due period from that first date up to
today; any period that's due and hasn't been logged or skipped yet shows
up on the Dashboard's "Due now" card. Confirming logs it as a normal
history entry (linked back to the recurring item, so it's never counted
twice); skipping just marks that one period as handled without adding an
entry — useful for a month you didn't actually pay, or for backfilling
your setup without logging everything retroactively. If you don't open
the app for a while, only the most recent 6 overdue periods per item are
surfaced at once, so a forgotten bill doesn't flood the screen.

## Cloud sync setup (optional)

By default the app stores everything in `localStorage` on the one device
you use it on — nothing leaves your browser. To sync across devices with
your own Google account:

1. Create a free project at the [Firebase console](https://console.firebase.google.com/).
2. In **Build → Firestore Database**, create a database (production mode is fine).
3. In **Build → Authentication → Sign-in method**, enable **Google**.
4. In **Project settings → General**, scroll to "Your apps", add a **Web app**, and copy the `firebaseConfig` object it gives you.
5. In `index.html`, find `var FIREBASE_CONFIG = null;` near the top of the `<script>` and replace `null` with that config object, e.g.:
   ```js
   var FIREBASE_CONFIG = {
     apiKey: "...",
     authDomain: "your-project.firebaseapp.com",
     projectId: "your-project",
     storageBucket: "your-project.firebasestorage.app",
     messagingSenderId: "...",
     appId: "..."
   };
   ```
6. In Firestore **Rules**, restrict each record to its own signed-in owner:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /expenseTracker/{email} {
         allow read, write: if request.auth != null && request.auth.token.email == email;
       }
     }
   }
   ```
7. Re-open the app (served over http/https) — you'll be asked to sign in with Google, and from then on your ledger syncs to every device where you sign in with that same account.

Until you do this, the app works fully and normally — cloud sync is a
pure add-on, not a requirement.
