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
  if (mbps >= 100) return "#30d158"; // green
  if (mbps >= 25) return "#0a84ff"; // blue
  if (mbps >= 5) return "#ff9f0a"; // orange
  return "#ff453a"; // red
}

// ---- Metadata ----
async function fetchMeta() {
  try {
    const res = await fetch(`${BASE}/meta`, { cache: "no-store" });
    return await res.json();
  } catch {
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
    el.dAsn.textContent = meta.asOrganization ? `${meta.asOrganization} (AS${meta.asn})` : meta.asn ? `AS${meta.asn}` : "—";
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
  el.gaugeFill.style.stroke = "#0a84ff";

  try {
    const metaPromise = fetchMeta();

    setPhase("Ping");
    const { ping, jitter } = await testPing();
    el.rPing.textContent = ping.toFixed(0);
    el.rJitter.textContent = jitter.toFixed(1);
    setGauge(0);

    setPhase("Download");
    const download = await testDownload();
    el.rDownload.textContent = download >= 100 ? download.toFixed(0) : download.toFixed(1);
    setGauge(download, speedColor(download));

    setPhase("Upload");
    setGauge(0, "#0a84ff");
    const upload = await testUpload();
    el.rUpload.textContent = upload >= 100 ? upload.toFixed(0) : upload.toFixed(1);
    setGauge(upload, speedColor(upload));

    setPhase("Done");
    const meta = await metaPromise;
    fillConnectionInfo(meta);

    el.results.hidden = false;
    el.startBtn.textContent = "Test Again";
  } catch (err) {
    setPhase("Connection error", true);
    el.gaugeFill.style.stroke = "#ff453a";
    el.startBtn.textContent = "Try Again";
    console.error("Speed test failed:", err);
  } finally {
    el.startBtn.disabled = false;
  }
}

el.startBtn.addEventListener("click", runTest);
