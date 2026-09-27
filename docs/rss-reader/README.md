# 📰 RSS Reader

A small, dependency-free RSS/Atom feed reader. Installable to the home
screen on iOS and Android, works offline, and keeps everything on your
own device — no account, no server.

**Live:** https://akrishna87.github.io/Akrishna87/docs/rss-reader/

## Features

- **Subscribe to anything** — paste an RSS/Atom feed link *or* just a
  website address; the app finds the site's feed automatically.
  Handles RSS 2.0, RSS 1.0 (RDF), Atom, podcasts (audio/video
  enclosures) and YouTube channel feeds.
- **Three-pane reader** on desktop (feeds · articles · article), a
  drawer + full-screen reader on phones, with the back gesture closing
  the article.
- **Folders** — group feeds (Tech, News, …); each folder has its own
  combined view and unread count, and can be collapsed. Set a folder when
  adding a feed or from ✎ Edit feed. OPML import/export keeps folders.
- **Search** (🔍 or `/`) across every saved article's title, author and
  full text, with matches highlighted.
- **Full article text** — for feeds that only include a summary, tap the
  📄 button (or `f`) to pull the complete article from the website and
  show it cleanly (Mozilla's Readability, the engine behind Firefox's
  Reader View). Saved for offline reading. Turn on *Always load the full
  article* per feed in ✎ Edit feed.
- **Sync across devices** (optional) — sign in with Google and your
  feeds, folders, read/unread and starred articles, and settings stay in
  step on every device. See below.
- **Unread tracking, stars, "mark all read"**, per-feed unread counts,
  and an "Unread only" filter.
- **Clean article view** — feed HTML is sanitized (no scripts, trackers
  or iframes), relative links/images fixed, tracking pixels dropped.
- **Offline** — the app shell is cached by a service worker and articles
  are stored in IndexedDB, so everything you've already fetched is
  readable with no connection.
- **Auto-refresh** on open and every 15/30/60 minutes while open.
- **OPML import/export** to move subscriptions from/to other readers.
- **Keyboard shortcuts**: `j`/`k` next/prev, `s` star, `m` mark unread,
  `f` full article, `v` open original, `/` search, `r` refresh,
  `a` add feed, `Esc` close.
- **Share to add** — on Android, installed app appears in the share
  sheet; share a page to it to subscribe. `?add=<url>` links work too.
- Light / dark / auto theme.

## How fetching works (CORS) and your own relay

GitHub Pages only serves static files, so feeds (and full articles) are
fetched by your browser. Most sites don't allow that (no CORS headers),
so the app tries, in order:

1. **your own relay**, if you've set one in Settings,
2. the site directly,
3. free public proxies (allorigins.win, corsproxy.io, codetabs.com).

Public proxies are rate-limited and sometimes down, so for reliable
updates set up your own free relay on Cloudflare. It takes about 5
minutes and needs no installs: see [`rss-relay/`](../../rss-relay/). It
only serves this app, so nobody else can use it.

## Sync across devices

Sync is optional and off until you connect a Firebase project. What
syncs: subscriptions (with names, folders and the full-article setting),
read/unread and starred state, and settings (theme, refresh interval,
relay). Articles themselves aren't uploaded; each device fetches its
own. If two devices change the same thing, the most recent change wins,
so changes made offline merge when you're back online.

You can reuse the Firebase project Tomorrow's Bag already uses
(`school-notebook-tracker`). Google sign-in and this site's domain are
already set up there. Two steps:

1. **Paste the config.** In `docs/rss-reader/index.html`, find
   `var FIREBASE_CONFIG = null;` and replace `null` with the same
   `{ apiKey: ..., authDomain: ..., projectId: ..., ... }` object that's in
   `docs/index.html`. (Or with a new project's config, from Firebase
   console → Project settings → Your apps → Web app.)
2. **Allow the reader's data in Firestore.** Firebase console →
   Firestore Database → **Rules**. *Add* this block inside the existing
   `match /databases/{database}/documents { ... }`, keeping the rules
   already there for Tomorrow's Bag, then click **Publish**:
   ```
   match /rssReader/{email} {
     allow read, write: if request.auth != null
                        && request.auth.token.email.lower() == email;
   }
   ```
   Each person can only read and write their own document.

Then open the reader → ⚙ Settings → **Sign in with Google**. Do the same
on your other devices with the same Google account.

If you use a brand-new Firebase project instead, also enable
**Authentication → Sign-in method → Google** and add
`akrishna87.github.io` under **Authentication → Settings → Authorized
domains**.

## Running it

Everything lives in `index.html` — no build step. Serve the folder over
http(s) (GitHub Pages, or `python3 -m http.server 8000` locally) to get
the installable/offline bits; `manifest.json`, `sw.js` and `icons/` are
skipped if you open the file straight from disk.

## Installing on your phone

- **iOS (Safari)**: open the site, tap Share → "Add to Home Screen".
- **Android (Chrome)**: open the site, tap ⋮ → "Install app".

## Privacy

Subscriptions and articles are stored in this browser. The only network
requests are the feed and article fetches themselves (via your relay,
directly, or via the public proxies above), article images when you read
them, and small site icons from DuckDuckGo's favicon service. If you turn
on sync, your subscription list, read/starred state and settings are
stored in your own Firebase project, readable only by your Google account.

`vendor/Readability.js` is [Mozilla Readability](https://github.com/mozilla/readability)
0.6.0 (Apache License 2.0), included unmodified.
