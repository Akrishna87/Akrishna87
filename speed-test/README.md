# Speed

A precise, honest look at your internet connection — download, upload, ping, jitter, and everything else worth knowing about the network you're on.

## Running it

No build step, no install. Just open `index.html` in a browser, or serve the folder:

```
cd speed-test
python3 -m http.server 8080
```

Then visit `http://localhost:8080`.

## What it measures

- **Download / Upload** — real throughput against Cloudflare's global network, live-updating as the test runs
- **Ping / Jitter** — median latency and consistency across multiple samples
- **Connection details** — public IP, network provider (ASN), server location, test server used, HTTP protocol, plus whatever your browser itself reports (connection type, estimated downlink, save-data mode)

## Roadmap

This is v1: an on-demand test with maximal detail. Planned next: automatic background tests at random times throughout the day, historical trends, and threshold alerts.
