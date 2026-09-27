# RSS Reader relay (Cloudflare Worker)

Most websites don't let a browser app read their feeds directly (they don't
send CORS headers). This small relay fetches the feed for the
[RSS Reader](../docs/rss-reader/) and passes it back with the right header.
It runs free on Cloudflare: the free plan allows 100,000 requests a day,
far more than one person's reader uses.

It only answers requests from `https://akrishna87.github.io`, so other
people can't use it as a free proxy. If you host the reader somewhere
else, add that address to `ALLOWED_ORIGINS` at the top of `worker.js`.

## Set it up (about 5 minutes, no installs)

1. Sign up (free) at <https://dash.cloudflare.com/sign-up> and verify your email.
2. In the dashboard, open **Compute (Workers) → Workers & Pages** and click **Create** (or **Create application**).
3. Choose **Start with Hello World!**, name it `rss-relay`, and click **Deploy**.
4. Click **Edit code**. Delete everything in the editor, paste in the whole
   contents of [`worker.js`](worker.js), and click **Deploy**.
5. Copy your worker's address from the top of the page. It looks like
   `https://rss-relay.<your-subdomain>.workers.dev`.
6. Open the RSS Reader → ⚙ **Settings** → **Your feed relay**, and enter
   that address followed by `/?url={url}`:
   ```
   https://rss-relay.<your-subdomain>.workers.dev/?url={url}
   ```
   Then click **Test**. You should see "✅ Relay works."

If you're signed in to sync, the relay setting carries over to your other
devices automatically.

## Or deploy from the command line

```sh
cd rss-relay
npx wrangler login
npx wrangler deploy
```

## Checking it

Opening the worker's address directly in a browser tab shows
"This relay only serves the RSS Reader app". That's expected, because
the request didn't come from the reader. Use the app's **Test** button
instead.
