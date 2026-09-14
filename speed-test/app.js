const BASE = "https://speed.cloudflare.com";
const CIRCUMFERENCE = 2 * Math.PI * 104; // matches r=104 in the SVG

const el = {
  startBtn: document.getElementById("startBtn"),
  results: document.getElementById("results"),
  gaugeFill: document.querySelector(".gauge-fill"),
  gaugeValue: document.getElementById("gaugeValue"),
  gaugePhase: document.getElementById("gaugePhase"),
  rDownload: document.getElementById("rDownload"),
  rUpload: document.getElementById("rUpload"),
  rPing: document.getElementById("rPing"),
  rJitter: document.getElementById("rJitter"),
  rPingQuality: document.getElementById("rPingQuality"),
  rJitterQuality: document.getElementById("rJitterQuality"),
  dIp: document.getElementById("dIp"),
  dAsn: document.getElementById("dAsn"),
  dLocation: document.getElementById("dLocation"),
  dServer: document.getElementById("dServer"),
  dConnType: document.getElementById("dConnType"),
  dDownlink: document.getElementById("dDownlink"),
  dRtt: document.getElementById("dRtt"),
  dProtocol: document.getElementById("dProtocol"),
  dSaveData: document.getElementById("dSaveData"),
  dBrowser: document.getElementById("dBrowser"),
  dTimestamp: document.getElementById("dTimestamp"),
};

el.gaugeFill.style.strokeDasharray = String(CIRCUMFERENCE);

function setGauge(mbps, colorClass) {
  const maxScale = 1000; // log scale so both slow and fast connections read meaningfully
  const fraction = Math.max(0, Math.min(1, Math.log10(mbps + 1) / Math.log10(maxScale + 1)));
  el.gaugeFill.style.strokeDashoffset = String(CIRCUMFERENCE * (1 - fraction));
  el.gaugeValue.textContent = mbps >= 100 ? mbps.toFixed(0) : mbps.toFixed(1);
  if (colorClass) el.gaugeFill.style.stroke = colorClass;
}

function setPhase(text, isError = false) {
  el.gaugePhase.textContent = text;
  el.gaugePhase.classList.toggle("error", isError);
}

function speedColor(mbps) {
  if (mbps >= 100) return "var(--green)";
  if (mbps >= 25) return "var(--blue)";
  if (mbps >= 5) return "var(--orange)";
  return "var(--red)";
}

// Thresholds reflect what actually matters for calls/gaming, not raw speed.
function pingQuality(ms) {
  if (ms < 20) return { label: "Excellent", color: "var(--green)" };
  if (ms < 50) return { label: "Good", color: "var(--blue)" };
  if (ms < 100) return { label: "Fair", color: "var(--orange)" };
  return { label: "Poor", color: "var(--red)" };
}

function jitterQuality(ms) {
  if (ms < 5) return { label: "Excellent", color: "var(--green)" };
  if (ms < 15) return { label: "Good", color: "var(--blue)" };
  if (ms < 30) return { label: "Fair", color: "var(--orange)" };
  return { label: "Poor", color: "var(--red)" };
}

function setQuality(el, quality) {
  el.textContent = quality.label;
  el.style.color = quality.color;
}

// ---- Metadata ----
// Cloudflare's speed-test metadata endpoint. Falls back to the widely-used
// /cdn-cgi/trace endpoint (plain text) if /meta is unavailable or returns
// something unexpected, so the details panel degrades instead of going blank.
async function fetchMeta() {
  let meta = null;
  try {
    const res = await fetch(`${BASE}/meta`, { cache: "no-store" });
    if (res.ok) {
      const data = await res.json();
      if (data && (data.clientIp || data.colo)) meta = data;
    }
    if (!meta) console.warn(`/meta returned ${res.status}; falling back to /cdn-cgi/trace`);
  } catch (err) {
    console.warn("/meta request failed; falling back to /cdn-cgi/trace", err);
  }
  if (!meta) meta = await fetchTrace();

  // Neither Cloudflare endpoint reliably carries the ISP/organization name —
  // /meta sometimes does, /cdn-cgi/trace never does. Fill the gap from a
  // dedicated IP lookup service when we don't already have it.
  if (!meta || !meta.asOrganization) {
    const isp = await fetchIspInfo();
    if (isp) meta = { ...isp, ...(meta || {}) };
  }
  return meta;
}

async function fetchTrace() {
  try {
    const res = await fetch("https://www.cloudflare.com/cdn-cgi/trace", { cache: "no-store" });
    if (!res.ok) return null;
    const text = await res.text();
    const fields = {};
    for (const line of text.trim().split("\n")) {
      const idx = line.indexOf("=");
      if (idx === -1) continue;
      fields[line.slice(0, idx)] = line.slice(idx + 1);
    }
    return {
      clientIp: fields.ip,
      colo: fields.colo,
      country: fields.loc,
      httpProtocol: fields.http ? fields.http.toUpperCase() : undefined,
    };
  } catch (err) {
    console.error("Connection metadata unavailable:", err);
    return null;
  }
}

async function fetchIspInfo() {
  try {
    const res = await fetch("https://ipwho.is/", { cache: "no-store" });
    if (!res.ok) return null;
    const data = await res.json();
    if (!data || data.success === false) return null;
    return {
      clientIp: data.ip,
      asOrganization: (data.connection && (data.connection.isp || data.connection.org)) || undefined,
      country: data.country_code,
      city: data.city,
      region: data.region,
    };
  } catch (err) {
    console.warn("ISP lookup failed:", err);
    return null;
  }
}

// ---- Ping / jitter ----
async function testPing(samples = 16) {
  const times = [];
  for (let i = 0; i < samples; i++) {
    const start = performance.now();
    await fetch(`${BASE}/__down?bytes=0&cachebust=${Date.now()}-${i}`, { cache: "no-store" });
    const elapsed = performance.now() - start;
    // Discard the first two as connection warm-up.
    if (i >= 2) times.push(elapsed);
  }
  times.sort((a, b) => a - b);
  const ping = times[Math.floor(times.length / 2)]; // median
  let jitterSum = 0;
  for (let i = 1; i < times.length; i++) jitterSum += Math.abs(times[i] - times[i - 1]);
  const jitter = times.length > 1 ? jitterSum / (times.length - 1) : 0;
  return { ping, jitter };
}

// ---- Download ----
async function testDownload(durationMs = 8000, parallel = 6) {
  let totalBytes = 0;
  const start = performance.now();
  const deadline = start + durationMs;
  const controllers = [];

  async function stream() {
    while (performance.now() < deadline) {
      const controller = new AbortController();
      controllers.push(controller);
      try {
        const res = await fetch(`${BASE}/__down?bytes=26214400`, {
          cache: "no-store",
          signal: controller.signal,
        });
        const reader = res.body.getReader();
        while (true) {
          if (performance.now() >= deadline) {
            controller.abort();
            break;
          }
          const { done, value } = await reader.read();
          if (done) break;
          totalBytes += value.length;
          const elapsedSec = (performance.now() - start) / 1000;
          const mbps = (totalBytes * 8) / elapsedSec / 1e6;
          setGauge(mbps);
        }
      } catch {
        // Aborted at deadline — expected.
      }
    }
  }

  await Promise.all(Array.from({ length: parallel }, stream));
  const elapsedSec = (performance.now() - start) / 1000;
  return (totalBytes * 8) / elapsedSec / 1e6;
}

// ---- Upload ----
async function testUpload(durationMs = 6000, parallel = 4, chunkBytes = 4 * 1024 * 1024) {
  const payload = new Uint8Array(chunkBytes);
  crypto.getRandomValues(payload.subarray(0, Math.min(65536, chunkBytes))); // seed a random block, reused to fill
  for (let i = 65536; i < chunkBytes; i += 65536) {
    payload.set(payload.subarray(0, Math.min(65536, chunkBytes - i)), i);
  }

  let totalBytes = 0;
  const start = performance.now();
  const deadline = start + durationMs;

  async function worker() {
    while (performance.now() < deadline) {
      try {
        await fetch(`${BASE}/__up`, {
          method: "POST",
          body: payload,
          cache: "no-store",
        });
        totalBytes += chunkBytes;
        const elapsedSec = (performance.now() - start) / 1000;
        const mbps = (totalBytes * 8) / elapsedSec / 1e6;
        setGauge(mbps);
      } catch {
        break;
      }
    }
  }

  await Promise.all(Array.from({ length: parallel }, worker));
  const elapsedSec = (performance.now() - start) / 1000;
  return (totalBytes * 8) / elapsedSec / 1e6;
}

// ---- Browser / connection info ----
function detectBrowser() {
  const ua = navigator.userAgent;
  if (ua.includes("Edg/")) return "Edge";
  if (ua.includes("Chrome/") && !ua.includes("Chromium")) return "Chrome";
  if (ua.includes("Firefox/")) return "Firefox";
  if (ua.includes("Safari/") && !ua.includes("Chrome")) return "Safari";
  return "Unknown";
}

function fillConnectionInfo(meta) {
  if (meta) {
    el.dIp.textContent = meta.clientIp || "—";
    if (meta.asOrganization && meta.asn) {
      el.dAsn.textContent = `${meta.asOrganization} (AS${meta.asn})`;
    } else if (meta.asOrganization) {
      el.dAsn.textContent = meta.asOrganization;
    } else if (meta.asn) {
      el.dAsn.textContent = `AS${meta.asn}`;
    } else {
      el.dAsn.textContent = "—";
    }
    const loc = [meta.city, meta.region, meta.country].filter(Boolean).join(", ");
    el.dLocation.textContent = loc || "—";
    el.dServer.textContent = meta.colo ? `Cloudflare ${meta.colo}` : "—";
    el.dProtocol.textContent = meta.httpProtocol || "—";
  }

  const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
  if (conn) {
    el.dConnType.textContent = conn.effectiveType ? conn.effectiveType.toUpperCase() : "—";
    el.dDownlink.textContent = conn.downlink ? `${conn.downlink} Mbps (estimate)` : "—";
    el.dRtt.textContent = conn.rtt ? `${conn.rtt} ms` : "—";
    el.dSaveData.textContent = conn.saveData ? "On" : "Off";
  } else {
    el.dConnType.textContent = "Not supported by this browser";
    el.dDownlink.textContent = "—";
    el.dRtt.textContent = "—";
    el.dSaveData.textContent = "—";
  }

  el.dBrowser.textContent = detectBrowser();
  el.dTimestamp.textContent = new Date().toLocaleString();
}

// ---- Orchestration ----
async function runTest() {
  el.startBtn.disabled = true;
  el.startBtn.textContent = "Testing…";
  el.results.hidden = true;
  setGauge(0);
  el.gaugeFill.style.stroke = "var(--blue)";

  try {
    const metaPromise = fetchMeta();

    setPhase("Ping");
    const { ping, jitter } = await testPing();
    el.rPing.textContent = ping.toFixed(0);
    el.rJitter.textContent = jitter.toFixed(1);
    setQuality(el.rPingQuality, pingQuality(ping));
    setQuality(el.rJitterQuality, jitterQuality(jitter));
    setGauge(0);

    setPhase("Download");
    const download = await testDownload();
    el.rDownload.textContent = download >= 100 ? download.toFixed(0) : download.toFixed(1);
    setGauge(download, speedColor(download));

    setPhase("Upload");
    setGauge(0, "var(--blue)");
    const upload = await testUpload();
    el.rUpload.textContent = upload >= 100 ? upload.toFixed(0) : upload.toFixed(1);
    setGauge(upload, speedColor(upload));

    setPhase("Done");
    const meta = await metaPromise;
    fillConnectionInfo(meta);

    el.results.hidden = false;
    el.startBtn.textContent = "Test Again";

    // The final numbers now live in the result cards below — let the dial
    // settle back to a ready state rather than freezing on the last reading.
    setTimeout(() => {
      setGauge(0);
      el.gaugeFill.style.stroke = "var(--blue)";
    }, 600);
  } catch (err) {
    setPhase("Connection error", true);
    el.gaugeFill.style.stroke = "var(--red)";
    el.startBtn.textContent = "Try Again";
    console.error("Speed test failed:", err);
  } finally {
    el.startBtn.disabled = false;
  }
}

el.startBtn.addEventListener("click", runTest);
