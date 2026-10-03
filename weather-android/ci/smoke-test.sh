#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, picks a place by searching for it, and
# checks that the forecast, the wind map, the storm list and the sources screen all open without
# crashing. The weather services are on the internet, so their data is checked but not required.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.weather
HERE="$(cd "$(dirname "$0")" && pwd)"
FIND="$HERE/../../music-player-android/ci/find_text.py"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o 'text="[^"]\+"' "$OUT/failure.xml" | head -40 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  exit 1
}
dump() {
  local i
  rm -f "$OUT/$1.xml"
  adb shell rm -f /sdcard/ui.xml
  for i in 1 2 3; do
    # uiautomator often can't capture the animated wind map; callers check for an empty dump.
    { adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null; } 2>&1 || true
    # The emulator's own apps sometimes freeze while it warms up; wave the "isn't responding"
    # popup away so it doesn't cover the app.
    [ -s "$OUT/$1.xml" ] || { echo "(no screen dump this time)"; return 0; }
    grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml" || return 0
    # "Close app" restarts the frozen app (usually the launcher, in the background), so the popup
    # stops coming back; "Wait" would leave it hanging and the popup returns every few seconds.
    echo "(closing a frozen system app: $(grep -o 'text="[^"]*isn[^"]*responding"' "$OUT/$1.xml" | head -1))"
    if python3 "$FIND" "$OUT/$1.xml" "Close app" > /dev/null 2>&1; then
      adb shell input tap $(python3 "$FIND" "$OUT/$1.xml" "Close app")
    elif python3 "$FIND" "$OUT/$1.xml" "Wait" > /dev/null 2>&1; then
      adb shell input tap $(python3 "$FIND" "$OUT/$1.xml" "Wait")
    fi
    sleep 3
  done
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
on_screen() { python3 "$FIND" "$OUT/$1.xml" "$2" "${3:-first}" > /dev/null 2>&1; }
tap() { # tap <dump name> <text> [first|last|exact]
  local xy
  xy=$(python3 "$FIND" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
tab() { # tab <dump name> <label>: taps the bottom-most exact match, i.e. the navigation bar entry
  local xy W H i
  if [ ! -s "$OUT/$1.xml" ]; then
    # uiautomator often can't capture the animated wind map; tap the tab by its place in the bar.
    case "$2" in "Weather") i=1 ;; "Wind map") i=3 ;; "Storms") i=5 ;; *) i=7 ;; esac
    read -r W H < <(adb shell wm size | grep -o "[0-9]*x[0-9]*" | tail -1 | tr x ' ')
    adb shell input tap $((W * i / 8)) $((H - 120))
    return 0
  fi
  xy=$(python3 - "$OUT/$1.xml" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, label = sys.argv[1], sys.argv[2].lower()
best = None
for n in ET.parse(path).iter("node"):
    if label in ((n.get("text") or "").lower(), (n.get("content-desc") or "").lower()):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        if best is None or y1 > best[1]:
            best = ((x1 + x2) // 2, (y1 + y2) // 2)
if best is None:
    sys.exit(1)
print(best[0], best[1])
PY
) || fail "couldn't find the '$2' tab"
  adb shell input tap $xy
}
texts() { # texts <dump name>: what was on screen, for the log
  [ -s "$OUT/$1.xml" ] && grep -o 'text="[^"]\+"' "$OUT/$1.xml" | sed 's/^text=//' | tr '\n' ' ' | fold -w 200 -s || true
  echo
}
sources_log() { adb logcat -d -s Vaanilai:I | grep -v "^---" || true; }
open_until() { # open_until <dump name> <text to tap> <text expected after>: retries when a system popup eats the tap
  local i
  for i in 1 2 3 4; do
    dump "$1"
    on_screen "$1" "$3" && return 0
    on_screen "$1" "$2" && tap "$1" "$2"
    sleep 3
  done
  dump "$1"
  on_screen "$1" "$3" || fail "tapping '$2' didn't show '$3'"
}
crashed() {
  adb logcat -d > "$OUT/logcat.txt"
  grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"
}

adb wait-for-device
adb install -r "$APK"
adb logcat -c

echo "--- Opening the app"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 8
dump welcome
on_screen welcome "Search for a place" || fail "the app didn't open on the welcome screen"
shot 1-welcome

echo "--- Choosing a place by searching"
open_until search "Search for a place" "Town or city"
tap search "Town or city"
sleep 1
adb shell input text "London"
FOUND=""
for i in $(seq 1 10); do
  sleep 2
  dump results
  if on_screen results "United Kingdom"; then FOUND=1; break; fi
done
if [ -z "$FOUND" ]; then
  echo "::warning::Place search (Open-Meteo) couldn't be reached from the emulator; skipping the forecast checks."
  shot 2-search-offline
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  adb shell input keyevent KEYCODE_BACK
  sleep 1
else
  shot 2-search
  tap results "United Kingdom"
  for i in $(seq 1 15); do
    sleep 2
    dump weather
    on_screen weather "Feels like" && break
  done
  if on_screen weather "Feels like"; then
    shot 3-weather
    adb shell input swipe 540 1700 540 500 600
    sleep 1
    adb shell input swipe 540 1700 540 500 600
    sleep 2
    shot 4-weather-more
    adb shell input swipe 540 1700 540 400 600
    sleep 1
    adb shell input swipe 540 1700 540 400 600
    sleep 2
    dump weather2
    shot 5-sources-compare
    echo "Weather screen: $(texts weather) $(texts weather2)"
  else
    echo "::warning::The forecast didn't load in the emulator."
    shot 3-weather-offline
  fi
fi
crashed && fail "the app crashed (see logcat.txt)"

echo "--- Wind map"
dump tabs
tab tabs "Wind map"
sleep 15 # let the wind grid load and the particles run
shot 6-wind-map
if sources_log | grep -q "wind grid: OK"; then
  echo "Wind grid loaded: $(sources_log | grep "wind grid" | tail -1)"
else
  echo "::warning::The wind grid didn't load in the emulator: $(sources_log | grep "wind grid" | tail -1)"
fi
crashed && fail "the app crashed on the wind map (see logcat.txt)"

echo "--- Zooming the map out"
adb shell input swipe 300 900 500 900 300
sleep 6
shot 7-wind-map-moved
crashed && fail "the app crashed while moving the map (see logcat.txt)"

echo "--- Storms"
dump wind
tab wind "Storms"
sleep 6
dump storms
on_screen storms "Named storms" || fail "the storm list didn't open"
shot 8-storms
echo "Storms screen: $(texts storms)"
if on_screen storms "Show on wind map"; then
  tap storms "Show on wind map"
  sleep 12
  dump storm-map
  shot 9-storm-on-map
  crashed && fail "the app crashed showing a storm on the map (see logcat.txt)"
  dump tabs2
  tab tabs2 "Sources"
else
  tab storms "Sources"
fi

echo "--- Sources"
sleep 3
dump sources
on_screen sources "Data sources" || fail "the sources screen didn't open"
shot 10-sources

echo "--- What each data source returned"
sources_log

crashed && fail "the app crashed (see logcat.txt)"
echo "SMOKE TEST PASSED"
