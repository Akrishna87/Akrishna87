#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, checks the permission screen, then
# grants the permissions and checks that a live signal reading shows up (the emulator's
# simulated modem reports a 4G signal), that a spot can be measured and saved, and that the
# guide opens. Takes screenshots of each screen along the way.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.signal
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
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null
    # The emulator's own apps sometimes freeze while it warms up; wave the "isn't responding"
    # popup away so it doesn't cover the app.
    grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml" || return 0
    echo "(dismissing a system 'isn't responding' popup)"
    python3 "$FIND" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$FIND" "$OUT/$1.xml" "Wait")
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
wait_for() { # wait_for <dump name> <text> <seconds>
  local i
  for i in $(seq 1 "$3"); do
    dump "$1"
    on_screen "$1" "$2" && return 0
    sleep 1
  done
  return 1
}

adb wait-for-device
adb install -r "$APK"
adb logcat -c

echo "--- First start: the permission screen"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
wait_for perms "Allow" 20 || fail "the permission screen didn't show"
on_screen perms "Alaimaani" || fail "the app name isn't on the permission screen"
shot 1-permissions

echo "--- With permissions: a live reading"
adb shell am force-stop "$PKG"
for p in READ_PHONE_STATE ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
  adb shell pm grant "$PKG" "android.permission.$p"
done
adb shell cmd location set-location-enabled true || true
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
if ! wait_for signal "Signal strength (" 40; then
  on_screen signal "No SIM card" && fail "the app says there's no SIM card"
  fail "no signal reading appeared"
fi
sleep 3 # let the graph collect a few points
dump signal
shot 2-signal
grep -o 'text="Signal strength ([^"]*"' "$OUT/signal.xml" || true

echo "--- Scrolling to the graph and tower details"
adb shell input swipe 540 1900 540 700 400
sleep 2
dump details
on_screen details "Last 10 minutes" || fail "the graph isn't there"
shot 3-details
adb shell input swipe 540 1900 540 700 400
sleep 2
dump tower
on_screen tower "Tower" exact || echo "::warning::No tower details on the emulator (it may not report cell info)."
shot 4-tower

echo "--- Measuring a spot"
tap tower "Best spot" exact
sleep 2
dump spots
tap spots "Measure a spot"
sleep 2
dump name
tap name "Bedroom" exact
sleep 1
dump name2
shot 5-name
tap name2 "Start" exact
sleep 5
dump measuring
on_screen measuring "Measuring Bedroom" || fail "measuring didn't start"
shot 6-measuring
wait_for spots2 "just now" 40 || fail "the measured spot wasn't saved"
on_screen spots2 "Bedroom" || fail "the saved spot isn't named Bedroom"
shot 7-spot-saved

echo "--- The guide"
tap spots2 "Guide" exact
sleep 2
dump guide
on_screen guide "Reading the big number" || fail "the guide didn't open"
shot 8-guide

echo "--- Saved spots survive a restart"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
wait_for reopen "Best spot" 20 || fail "the app didn't reopen"
tap reopen "Best spot" exact
sleep 2
dump reopen2
on_screen reopen2 "Bedroom" exact || fail "the saved spot was lost after a restart"

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  fail "the app crashed (see logcat.txt)"
fi
echo "SMOKE TEST PASSED"
