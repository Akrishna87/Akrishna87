// App-shell cache. Feed fetches (cross-origin, via CORS proxies) are never
// intercepted here — articles are stored by the app itself in IndexedDB.
const CACHE_NAME = "rss-reader-v2";
const PRECACHE_URLS = [
  "./",
  "./index.html",
  "./manifest.json",
  "./icons/icon.svg",
  "./icons/icon-32.png",
  "./icons/icon-180.png",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./vendor/Readability.js",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(PRECACHE_URLS))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
    )
  );
  self.clients.claim();
});

// Stale-while-revalidate for same-origin GETs: instant loads (and offline),
// with the cache refreshed in the background so updates show on next launch.
self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET" || new URL(req.url).origin !== self.location.origin) return;
  event.respondWith(
    caches.open(CACHE_NAME).then(async (cache) => {
      // Ignore the query string so share-target launches (?url=...) still hit the cache.
      const cached = await cache.match(req, { ignoreSearch: req.mode === "navigate" });
      const network = fetch(req)
        .then((response) => {
          if (response.ok) cache.put(req.mode === "navigate" ? "./index.html" : req, response.clone());
          return response;
        })
        .catch(() => cached);
      event.waitUntil(network.then(() => {}, () => {}));
      return cached || network;
    })
  );
});
