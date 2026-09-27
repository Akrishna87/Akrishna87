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
  `v` open original, `r` refresh, `a` add feed, `Esc` close.
- **Share to add** — on Android, installed app appears in the share
  sheet; share a page to it to subscribe. `?add=<url>` links work too.
- Light / dark / auto theme.

## How fetching works (CORS)

GitHub Pages only serves static files, so feeds are fetched directly by
your browser. Most sites don't allow that (no CORS headers), so the app
tries, in order:

1. the feed directly,
2. your own proxy, if you set one in **Settings**,
3. free public proxies (allorigins.win, corsproxy.io, codetabs.com).

Whichever works for a feed is remembered and tried first next time.
Public proxies are rate-limited and occasionally down — if feeds start
failing, a personal proxy is the fix. A minimal Cloudflare Worker
(free tier) is enough:

```js
export default {
  async fetch(req) {
    const url = new URL(req.url).searchParams.get("url");
    if (!url || !/^https?:\/\//.test(url)) return new Response("missing ?url=", { status: 400 });
    const res = await fetch(url, { headers: { "User-Agent": "rss-reader" } });
    return new Response(res.body, {
      status: res.status,
      headers: {
        "Content-Type": res.headers.get("Content-Type") || "application/xml",
        "Access-Control-Allow-Origin": "*",
      },
    });
  },
};
```

Then in Settings set the proxy to
`https://<your-worker>.workers.dev/?url={url}`.

## Running it

Everything lives in `index.html` — no build step. Serve the folder over
http(s) (GitHub Pages, or `python3 -m http.server 8000` locally) to get
the installable/offline bits; `manifest.json`, `sw.js` and `icons/` are
skipped if you open the file straight from disk.

## Installing on your phone

- **iOS (Safari)**: open the site, tap Share → "Add to Home Screen".
- **Android (Chrome)**: open the site, tap ⋮ → "Install app".

## Privacy

Subscriptions and articles are stored only in this browser. The only
network requests are the feed fetches themselves (directly or via the
proxy chain above), article images when you read them, and small site
icons from DuckDuckGo's favicon service.
