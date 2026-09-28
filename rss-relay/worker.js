// RSS Reader relay — a tiny Cloudflare Worker that fetches a feed (or an
// article page) on the app's behalf and adds the CORS header browsers need.
//
//   GET https://<your-worker>.workers.dev/?url=<encoded feed or page URL>
//
// Only requests coming from the sites in ALLOWED_ORIGINS are served, so
// strangers can't use your relay as a free general-purpose proxy.

const ALLOWED_ORIGINS = [
  "https://akrishna87.github.io",
  "http://localhost:8000", // local testing (python3 -m http.server 8000)
];

const MAX_BYTES = 5 * 1024 * 1024; // refuse anything bigger than 5 MB
const CACHE_SECONDS = 300;         // feeds are cached at Cloudflare's edge for 5 minutes

export default {
  async fetch(request) {
    const origin = request.headers.get("Origin") || "";
    const allowed = ALLOWED_ORIGINS.includes(origin);
    const cors = {
      "Access-Control-Allow-Origin": allowed ? origin : ALLOWED_ORIGINS[0],
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Vary": "Origin",
    };

    if (request.method === "OPTIONS") return new Response(null, { status: allowed ? 204 : 403, headers: cors });
    if (request.method !== "GET") return reply(405, "Only GET is supported", cors);
    if (!allowed) return reply(403, "This relay only serves the RSS Reader app", cors);

    const target = new URL(request.url).searchParams.get("url");
    let url;
    try {
      url = new URL(target);
    } catch {
      return reply(400, "Missing or invalid ?url=", cors);
    }
    if (url.protocol !== "https:" && url.protocol !== "http:") return reply(400, "Only http(s) URLs", cors);

    let upstream;
    try {
      upstream = await fetch(url.toString(), {
        headers: {
          "User-Agent": "Mozilla/5.0 (compatible; RSSReaderRelay/1.0; +https://akrishna87.github.io/Akrishna87/docs/rss-reader/)",
          "Accept": "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5",
        },
        redirect: "follow",
        cf: { cacheTtl: CACHE_SECONDS, cacheEverything: true },
      });
    } catch (err) {
      return reply(502, "Couldn't reach " + url.hostname, cors);
    }

    const length = Number(upstream.headers.get("Content-Length") || 0);
    if (length > MAX_BYTES) return reply(413, "Response too large", cors);

    const body = await upstream.arrayBuffer();
    if (body.byteLength > MAX_BYTES) return reply(413, "Response too large", cors);

    return new Response(body, {
      status: upstream.status,
      headers: {
        ...cors,
        "Content-Type": upstream.headers.get("Content-Type") || "application/xml; charset=utf-8",
        "Cache-Control": "public, max-age=" + CACHE_SECONDS,
      },
    });
  },
};

function reply(status, message, headers) {
  return new Response(message, { status, headers: { ...headers, "Content-Type": "text/plain; charset=utf-8" } });
}
