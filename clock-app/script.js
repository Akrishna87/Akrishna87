"use strict";

const DEFAULT_ZONES = [
  { tz: "Asia/Kolkata", city: "India (New Delhi/Mumbai)", removable: false },
  { tz: "Europe/London", city: "London", removable: false },
];

const CURATED_ZONES = [
  "Asia/Kolkata",
  "Europe/London",
  "America/New_York",
  "America/Los_Angeles",
  "America/Chicago",
  "America/Sao_Paulo",
  "Europe/Paris",
  "Europe/Berlin",
  "Europe/Moscow",
  "Africa/Cairo",
  "Africa/Johannesburg",
  "Asia/Dubai",
  "Asia/Karachi",
  "Asia/Dhaka",
  "Asia/Bangkok",
  "Asia/Singapore",
  "Asia/Hong_Kong",
  "Asia/Shanghai",
  "Asia/Tokyo",
  "Asia/Seoul",
  "Australia/Sydney",
  "Australia/Perth",
  "Pacific/Auckland",
  "Pacific/Honolulu",
  "UTC",
];

function allKnownZones() {
  if (typeof Intl.supportedValuesOf === "function") {
    try {
      const values = Intl.supportedValuesOf("timeZone");
      if (values && values.length) return values;
    } catch (e) {
      // fall through to curated list
    }
  }
  return CURATED_ZONES;
}

function cityNameFromZone(tz) {
  const parts = tz.split("/");
  return parts[parts.length - 1].replace(/_/g, " ");
}

const STORAGE_KEY = "clockAppExtraZones";

function loadExtraZones() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch (e) {
    return [];
  }
}

function saveExtraZones(zones) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(zones));
  } catch (e) {
    // localStorage unavailable (e.g. private mode) - ignore
  }
}

let extraZones = loadExtraZones();

function currentZoneList() {
  return [
    ...DEFAULT_ZONES,
    ...extraZones.map((tz) => ({ tz, city: cityNameFromZone(tz), removable: true })),
  ];
}

// ICU has no letter abbreviation for some zones (e.g. India) and falls back
// to a "GMT+5:30" style string, which is redundant next to the offset badge.
const ZONE_ABBREVIATION_OVERRIDES = {
  "Asia/Kolkata": "IST",
};

function formatParts(date, tz) {
  const dtf = new Intl.DateTimeFormat("en-GB", {
    timeZone: tz,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZoneName: "short",
  });
  const parts = {};
  for (const p of dtf.formatToParts(date)) {
    parts[p.type] = p.value;
  }
  if (ZONE_ABBREVIATION_OVERRIDES[tz]) {
    parts.timeZoneName = ZONE_ABBREVIATION_OVERRIDES[tz];
  }
  return parts;
}

function utcOffsetLabel(date, tz) {
  const dtf = new Intl.DateTimeFormat("en-US", {
    timeZone: tz,
    timeZoneName: "longOffset",
  });
  const part = dtf.formatToParts(date).find((p) => p.type === "timeZoneName");
  if (!part) return "";
  return part.value.replace("GMT", "UTC");
}

function renderClocks() {
  const grid = document.getElementById("clockGrid");
  const zones = currentZoneList();
  const now = new Date();

  if (grid.children.length !== zones.length) {
    grid.innerHTML = "";
    zones.forEach((zone) => {
      const card = document.createElement("div");
      card.className = "clock-card";
      card.dataset.tz = zone.tz;
      card.innerHTML = `
        ${zone.removable ? '<button class="remove-btn" title="Remove clock" aria-label="Remove clock">&times;</button>' : ""}
        <div class="city">${zone.city}</div>
        <div class="zone-name">${zone.tz}</div>
        <div class="time"></div>
        <div class="date"></div>
        <div class="offset"></div>
      `;
      if (zone.removable) {
        card.querySelector(".remove-btn").addEventListener("click", () => {
          extraZones = extraZones.filter((tz) => tz !== zone.tz);
          saveExtraZones(extraZones);
          renderClocks();
          populateConverterZoneSelect();
        });
      }
      grid.appendChild(card);
    });
  }

  zones.forEach((zone) => {
    const card = grid.querySelector(`.clock-card[data-tz="${cssEscape(zone.tz)}"]`);
    if (!card) return;
    const parts = formatParts(now, zone.tz);
    card.querySelector(".time").textContent = `${parts.hour}:${parts.minute}:${parts.second}`;
    card.querySelector(".date").textContent = `${parts.weekday}, ${parts.day} ${parts.month} ${parts.year}`;
    card.querySelector(".offset").textContent = `${parts.timeZoneName}, ${utcOffsetLabel(now, zone.tz)}`;
  });
}

function cssEscape(str) {
  return str.replace(/["\\]/g, "\\$&");
}

function populateAddZoneSelect() {
  const select = document.getElementById("zoneSelect");
  const zones = allKnownZones();
  const alreadyAdded = new Set(currentZoneList().map((z) => z.tz));
  select.innerHTML = "";
  zones
    .filter((tz) => !alreadyAdded.has(tz))
    .forEach((tz) => {
      const option = document.createElement("option");
      option.value = tz;
      option.textContent = `${cityNameFromZone(tz)} (${tz})`;
      select.appendChild(option);
    });
}

function populateConverterZoneSelect() {
  const select = document.getElementById("fromZone");
  const previous = select.value;
  const zones = allKnownZones();
  select.innerHTML = "";
  zones.forEach((tz) => {
    const option = document.createElement("option");
    option.value = tz;
    option.textContent = `${cityNameFromZone(tz)} (${tz})`;
    select.appendChild(option);
  });
  if (previous && zones.includes(previous)) {
    select.value = previous;
  } else {
    select.value = "Europe/London";
  }
  populateAddZoneSelect();
}

document.getElementById("addZoneBtn").addEventListener("click", () => {
  const select = document.getElementById("zoneSelect");
  const tz = select.value;
  if (!tz || extraZones.includes(tz)) return;
  extraZones.push(tz);
  saveExtraZones(extraZones);
  renderClocks();
  populateConverterZoneSelect();
});

// Converts a wall-clock date/time meant for `tz` into the UTC instant it
// represents. There's no native API for this, so we guess (treating the
// wall time as UTC), see what that guess renders as in `tz`, and correct
// by the difference. One correction pass is enough outside DST-transition
// instants; a second pass handles those too.
function zonedWallTimeToUtc(year, month, day, hour, minute, tz) {
  let guess = Date.UTC(year, month - 1, day, hour, minute, 0);
  for (let i = 0; i < 2; i++) {
    const parts = formatParts(new Date(guess), tz);
    const shown = Date.UTC(
      Number(parts.year),
      monthShortToIndex(parts.month),
      Number(parts.day),
      Number(parts.hour),
      Number(parts.minute),
      Number(parts.second)
    );
    const wanted = Date.UTC(year, month - 1, day, hour, minute, 0);
    const diff = wanted - shown;
    if (diff === 0) break;
    guess += diff;
  }
  return new Date(guess);
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
function monthShortToIndex(short) {
  const idx = MONTHS.indexOf(short);
  return idx === -1 ? 0 : idx;
}

function renderConverterResults(utcInstant) {
  const container = document.getElementById("converterResults");
  container.innerHTML = "";

  const targets = [
    ...currentZoneList(),
    { tz: "UTC", city: "UTC" },
  ];

  const seen = new Set();
  targets.forEach((zone) => {
    if (seen.has(zone.tz)) return;
    seen.add(zone.tz);
    const parts = formatParts(utcInstant, zone.tz);
    const row = document.createElement("div");
    row.className = "result-row";
    row.innerHTML = `
      <div>
        <div>${zone.city}</div>
        <div class="rr-zone">${zone.tz} &middot; ${parts.timeZoneName}, ${utcOffsetLabel(utcInstant, zone.tz)}</div>
      </div>
      <div class="rr-time">${parts.weekday}, ${parts.day} ${parts.month} ${parts.year} &middot; ${parts.hour}:${parts.minute}</div>
    `;
    container.appendChild(row);
  });
}

document.getElementById("converterForm").addEventListener("submit", (e) => {
  e.preventDefault();
  const fromTz = document.getElementById("fromZone").value;
  const dateVal = document.getElementById("fromDate").value;
  const timeVal = document.getElementById("fromTime").value;
  if (!dateVal || !timeVal) return;

  const [year, month, day] = dateVal.split("-").map(Number);
  const [hour, minute] = timeVal.split(":").map(Number);

  const utcInstant = zonedWallTimeToUtc(year, month, day, hour, minute, fromTz);
  renderConverterResults(utcInstant);
});

function initConverterDefaults() {
  const now = new Date();
  const dateField = document.getElementById("fromDate");
  const timeField = document.getElementById("fromTime");
  const pad = (n) => String(n).padStart(2, "0");
  dateField.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  timeField.value = `${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

function init() {
  populateAddZoneSelect();
  populateConverterZoneSelect();
  renderClocks();
  initConverterDefaults();
  document.getElementById("converterForm").requestSubmit
    ? document.getElementById("converterForm").requestSubmit()
    : renderConverterResults(new Date());
  setInterval(renderClocks, 1000);
}

init();
