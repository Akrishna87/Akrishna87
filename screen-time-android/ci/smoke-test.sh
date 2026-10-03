#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, checks that it asks for usage access and
# that its button opens the right settings page, then turns access on (as the Settings switch
# would), uses Settings for a while, and checks Settings shows up with its time in the Day, Week
# and Month views and on its own page. Takes screenshots along the way.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.screentime
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

adb wait-for-device
adb install -r "$APK"
adb logcat -c

echo "--- Opening the app: it should ask for usage access"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 6
dump access
on_screen access "Allow usage access" exact || fail "the usage access screen didn't show"
shot 1-access

echo "--- Its button opens Android's Usage access settings"
tap access "Open Usage access settings" exact
sleep 4
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" > "$OUT/top.txt" || true
cat "$OUT/top.txt"
grep -qi "settings" "$OUT/top.txt" || fail "the Usage access settings page didn't open"
shot 2-usage-access-settings
adb shell input keyevent KEYCODE_HOME
sleep 1

echo "--- Turning usage access on (what the Settings switch does)"
adb shell appops set "$PKG" GET_USAGE_STATS allow

echo "--- Using another app (Settings) for a while"
adb shell am start -W -a android.settings.SETTINGS > /dev/null
sleep 20
adb shell input keyevent KEYCODE_HOME
sleep 2

echo "--- Back in the app: today's list"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 8
dump today
on_screen today "Today" exact || fail "the Day view didn't open on Today"
on_screen today "Settings" exact || fail "Settings isn't in today's list"
on_screen today "Apps" exact || fail "no app list"
shot 3-today

echo "--- Week view"
tap today "Week" exact
sleep 3
dump week
on_screen week "Settings" exact || fail "Settings isn't in this week's list"
on_screen week "Daily average" || fail "the week has no daily average"
shot 4-week

echo "--- Month view"
tap week "Month" exact
sleep 3
dump month
on_screen month "Settings" exact || fail "Settings isn't in this month's list"
shot 5-month

echo "--- One app's page"
tap month "Settings" exact
sleep 3
dump app
on_screen app "App info" exact || fail "the app's page didn't open"
shot 6-app-page
adb shell input keyevent KEYCODE_BACK
sleep 2
dump back
on_screen back "Apps" exact || fail "Back didn't return to the list"

echo "--- Background sync is scheduled"
adb shell dumpsys jobscheduler > "$OUT/jobs.txt"
grep -q "$PKG" "$OUT/jobs.txt" || fail "the background sync job isn't scheduled"

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  fail "the app crashed (see logcat.txt)"
fi
echo "SMOKE TEST PASSED"
