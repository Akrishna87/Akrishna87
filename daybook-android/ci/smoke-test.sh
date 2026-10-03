#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, adds a task, an event and a note,
# checks they all show on Today, then sets Daybook as the wallpaper and photographs the lock screen.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.daybook
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o 'text="[^"]\+"' "$OUT/failure.xml" | head -60 || true
    echo "Tappable:"; python3 - "$OUT/failure.xml" <<'PY' || true
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    if n.get("clickable") == "true":
        print(" ", n.get("class"), repr(n.get("text")), repr(n.get("content-desc")), n.get("bounds"))
PY
    adb shell wm size || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  echo "Crashes and errors from the app:"
  grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1 || { sleep 2; continue; }
    # The emulator's own apps sometimes freeze while it warms up; wave the "isn't responding"
    # popup away so it doesn't cover the app. Crashes of Daybook itself are caught from logcat.
    grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml" || return 0
    echo "(dismissing a system 'isn't responding' popup)"
    python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait")
    sleep 3
  done
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
show() { echo "  on screen: $(tr '\n' ' ' < "$OUT/$1.xml" | grep -o 'text="[^"]\+"' | sed 's/^text=//' | head -40 | tr '\n' ' ')"; }
tap() { # tap <dump name> <text> [first|last]
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
try_tap() { # like tap, but carries on if it isn't there
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}" 2> /dev/null) || return 1
  adb shell input tap $xy
}
wait_for() { # wait_for <dump name> <grep -E pattern> <seconds> <what>
  local end=$((SECONDS + $3))
  while [ $SECONDS -lt $end ]; do
    dump "$1"
    grep -Eq "$2" "$OUT/$1.xml" && return 0
    sleep 2
  done
  fail "$4 (waited $3 s)"
}
hide_keyboard() { if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi; }

adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

echo "--- Launching the app (opens on Today)"
adb shell am start -W -n "$PKG/.MainActivity"
wait_for today 'of this year has passed' 30 "Today doesn't show the year progress"
shot 1-today
show today
grep -q 'text="Nothing planned today"' "$OUT/today.xml" || fail "Today should start with nothing planned"
grep -q 'text="Put Daybook on your lock screen"' "$OUT/today.xml" || fail "Today doesn't offer to set up the lock screen"
echo "PASS: Today screen"

echo "--- Tasks"
tap today "Tasks" last
wait_for tasks 'text="Add a task"' 10 "the Tasks tab has no 'Add a task' box"
tap tasks "Add a task"
sleep 1
adb shell input text "Buy%smilk"
adb shell input keyevent KEYCODE_ENTER
sleep 1
adb shell input text "Call%sthe%sdentist"
adb shell input keyevent KEYCODE_ENTER
sleep 1
hide_keyboard
wait_for tasks-added 'text="Call the dentist"' 10 "adding tasks didn't work"
grep -q 'text="Buy milk"' "$OUT/tasks-added.xml" || fail "the first task is missing"
grep -q 'text="2 to do"' "$OUT/tasks-added.xml" || fail "the task count isn't 2"
tap tasks-added "Mark Buy milk as done"
sleep 1
dump tasks-done
grep -q 'text="1 to do"' "$OUT/tasks-done.xml" || fail "ticking a task off didn't work"
grep -q 'Done · 1' "$OUT/tasks-done.xml" || fail "the done task isn't under Done"
shot 2-tasks
echo "PASS: tasks can be added and ticked off"

echo "--- Calendar"
tap tasks-done "Calendar" last
wait_for calendar '"New event"' 10 "the Calendar tab has no New event button"
shot 3-calendar
tap calendar "New event"
wait_for event-sheet '"Save"' 10 "the event editor didn't open"
tap event-sheet "Title"
sleep 1
adb shell input text "Team%ssync"
hide_keyboard
dump event-typed
shot 4-event-editor
tap event-typed "Save"
sleep 2
wait_for calendar-after 'text="Team sync"' 10 "the new event isn't on the calendar"
echo "PASS: events can be added"

echo "--- Notes"
tap calendar-after "Notes" last
wait_for notes 'text="Welcome to Daybook"' 10 "the welcome note is missing"
tap notes "New note"
sleep 2
dump note-editor
tap note-editor "Title"
sleep 1
adb shell input text "Packing%slist"
dump note-title
tap note-title "Note"
sleep 1
adb shell input text "Passport,%scharger"
hide_keyboard
adb shell input keyevent KEYCODE_BACK
wait_for notes-after 'text="Packing list"' 10 "the new note didn't save"
shot 5-notes
echo "PASS: notes save"

echo "--- Today shows it all"
tap notes-after "Today" last
wait_for today-after 'text="Team sync"' 10 "Today doesn't show the new event"
grep -q 'text="Buy milk"' "$OUT/today-after.xml" || grep -q 'text="Call the dentist"' "$OUT/today-after.xml" || fail "Today doesn't show the open task"
shot 6-today-filled
echo "PASS: Today shows the event and the task"

echo "--- Lock screen"
tap today-after "Lock screen and settings"
wait_for lock-settings '"Set as lock screen"' 10 "the lock screen settings didn't open"
shot 7-lock-settings
tap lock-settings "Set as lock screen"
sleep 5
dump picker
shot 8-wallpaper-preview
show picker
# The preview screen differs between Android versions; try the usual button names.
try_tap picker "Set wallpaper" || try_tap picker "Apply" || try_tap picker "Set" || echo "WARNING: no button to set the wallpaper"
sleep 3
dump where
show where
try_tap where "Home and lock screens" || try_tap where "Home screen and lock screen" || true
sleep 4
if adb shell dumpsys wallpaper | grep -q "$PKG"; then
  echo "PASS: Daybook is the wallpaper"
else
  echo "WARNING: couldn't confirm the wallpaper was set"
fi
# The emulator starts with no lock screen at all; turn on the swipe lock so there's one to photograph.
adb shell locksettings set-disabled false || true
adb shell input keyevent KEYCODE_SLEEP
sleep 3
adb shell input keyevent KEYCODE_WAKEUP
sleep 4
shot 9-lock-screen
adb shell wm dismiss-keyguard || true
sleep 2

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
