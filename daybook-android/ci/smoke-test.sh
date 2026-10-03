#!/usr/bin/env bash
# Runs inside the Android emulator job: adds tasks with Quick Add, completes one, adds a sub-task
# and a project, writes a note with a checklist, searches, and switches to the dark theme.
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
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb logcat -c

close_panel() { hide_keyboard; adb shell input keyevent KEYCODE_BACK; sleep 1; }

echo "--- Launching the app (opens on Today)"
adb shell am start -W -n "$PKG/.MainActivity"
wait_for today 'text="All clear"' 30 "Today doesn't show its empty state"
shot 1-today
show today
echo "PASS: Today screen"

echo "--- Quick Add"
tap today "Add task"
wait_for quick 'content-desc="Task name"' 10 "Quick Add didn't open"
sleep 1
adb shell input text "Buy%smilk%stoday%sp1%s@errands"
sleep 1
dump quick-typed
shot 2-quick-add
grep -q 'content-desc="P1"' "$OUT/quick-typed.xml" || fail "Quick Add didn't read p1 as priority 1"
grep -q 'content-desc="@errands"' "$OUT/quick-typed.xml" || fail "Quick Add didn't read @errands as a label"
grep -q 'content-desc="Today"' "$OUT/quick-typed.xml" || fail "Quick Add didn't read today as the date"
adb shell input keyevent KEYCODE_ENTER
sleep 1
adb shell input text "Call%sdentist%stomorrow%s9am"
adb shell input keyevent KEYCODE_ENTER
sleep 1
close_panel
wait_for today-tasks 'text="Buy milk"' 10 "the new task isn't on Today"
grep -q 'text="@errands"' "$OUT/today-tasks.xml" || fail "the task's label isn't shown"
shot 3-today-tasks
echo "PASS: Quick Add understands dates, priorities and labels"

echo "--- Completing a task"
tap today-tasks "Complete Buy milk"
sleep 1
dump completed
grep -q 'text="Completed"' "$OUT/completed.xml" || fail "no Completed snackbar with Undo"
grep -q 'text="Buy milk"' "$OUT/completed.xml" && fail "the completed task is still on Today"
grep -q 'text="All done for today"' "$OUT/completed.xml" || fail "Today doesn't say it's all done"
echo "PASS: completing a task"

echo "--- Upcoming and task details"
tap completed "Upcoming" last
wait_for upcoming 'text="Call dentist"' 10 "tomorrow's task isn't in Upcoming"
shot 4-upcoming
tap upcoming "Call dentist"
wait_for detail 'content-desc="Task options"' 10 "the task didn't open"
grep -q 'text="Remind me at the due time"' "$OUT/detail.xml" || fail "the 9am task has no reminder switch"
shot 5-task-detail
tap detail "Add sub-task"
wait_for sub-quick 'content-desc="Task name"' 10 "Quick Add didn't open for a sub-task"
sleep 1
adb shell input text "Find%sinsurance%scard"
adb shell input keyevent KEYCODE_ENTER
sleep 1
close_panel
wait_for detail-sub 'text="Find insurance card"' 10 "the sub-task wasn't added"
shot 6-subtask
adb shell input keyevent KEYCODE_BACK
sleep 1
echo "PASS: task details and sub-tasks"

echo "--- Projects"
dump before-browse
tap before-browse "Browse" last
wait_for browse 'content-desc="Add project"' 10 "Browse has no Add project button"
tap browse "Add project"
sleep 2
adb shell input text "Work"
adb shell input keyevent KEYCODE_ENTER
wait_for project 'content-desc="Project options"' 10 "the new project didn't open"
tap project "Add task" last
wait_for project-quick 'content-desc="Task name"' 10 "Quick Add didn't open in the project"
sleep 1
adb shell input text "Write%sreport%sp2"
adb shell input keyevent KEYCODE_ENTER
sleep 1
close_panel
wait_for project-task 'text="Write report"' 10 "the task didn't go into the project"
shot 7-project
adb shell input keyevent KEYCODE_BACK
sleep 1
echo "PASS: projects"

echo "--- Notes"
dump before-notes
tap before-notes "Notes" last
wait_for notes 'text="Welcome to Daybook"' 10 "the welcome note is missing"
shot 8-notes
tap notes "New note"
wait_for note-new 'content-desc="Note text"' 10 "the note editor didn't open"
sleep 1
adb shell input text "Meeting%snotes"
tap note-new "Note text"
sleep 1
adb shell input text "Agenda"
adb shell input keyevent KEYCODE_ENTER
dump note-agenda
tap note-agenda "Checklist"
sleep 1
adb shell input text "Review%sbudget"
adb shell input keyevent KEYCODE_ENTER
adb shell input text "Book%sroom"
hide_keyboard
dump note-typed
tap note-typed "Done editing"
sleep 1
dump note-view
shot 9-note
grep -q 'content-desc="Tick Review budget"' "$OUT/note-view.xml" || fail "the checklist didn't render"
grep -q 'content-desc="Tick Book room"' "$OUT/note-view.xml" || fail "the checklist didn't continue on enter"
tap note-view "Tick Review budget"
sleep 1
dump note-ticked
grep -q 'content-desc="Untick Review budget"' "$OUT/note-ticked.xml" || fail "ticking a checklist item didn't work"
hide_keyboard
adb shell input keyevent KEYCODE_BACK
wait_for notes-after 'text="Meeting notes"' 10 "the new note isn't in the list"
grep -q 'text="1/2"' "$OUT/notes-after.xml" || fail "the note's checklist progress isn't shown"
shot 10-notes-list
echo "PASS: notes with checklists"

echo "--- Search"
tap notes-after "Search"
sleep 2
adb shell input text "report"
sleep 1
hide_keyboard
wait_for search 'text="Write report"' 10 "search didn't find the task"
shot 11-search
adb shell input keyevent KEYCODE_BACK
sleep 1
echo "PASS: search"

echo "--- Dark theme"
dump before-settings
tap before-settings "Browse" last
wait_for browse-2 'content-desc="Settings"' 10 "Browse has no Settings"
tap browse-2 "Settings"
wait_for settings 'Theme: Dark' 10 "Settings has no theme choice"
tap settings "Theme: Dark"
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1
dump browse-dark
tap browse-dark "Today" last
sleep 2
shot 12-today-dark
echo "PASS: dark theme"

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
