#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, opens a sample PDF, searches it,
# opens the side panel, switches to night mode, draws and highlights, signs with a tick, saves,
# turns a page with Organize, and checks the saved file really contains the annotations.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.pakkam
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
DOC_DIR=/sdcard/Android/data/$PKG/files
DOC=$DOC_DIR/smoke.pdf

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o '\(text\|content-desc\)="[^"]\+"' "$OUT/failure.xml" | head -80 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  echo "Crashes and errors from the app:"
  grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 || true
  grep -A 12 " [WI] Pakkam" "$OUT/logcat.txt" | head -60 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1 || { sleep 2; continue; }
    grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml" || return 0
    echo "(dismissing a system 'isn't responding' popup)"
    python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait")
    sleep 3
  done
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
show() { echo "  on screen: $(tr '\n' ' ' < "$OUT/$1.xml" | grep -o '\(text\|content-desc\)="[^"]\+"' | sed 's/^[a-z-]*=//' | head -50 | tr '\n' ' ')"; }
tap() { # tap <dump name> <text> [first|last]
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
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
crashed() { adb logcat -d | grep -q "FATAL EXCEPTION"; }
# Screen position of a point given in PDF points on page 1 (612 × 792), from the page's bounds on screen.
page_xy() { # page_xy <dump name> <x pt> <y pt from the bottom>
  local b
  b=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Page 1" bounds) || fail "page 1 isn't on screen"
  python3 - "$b" "$2" "$3" <<'PY'
import sys
x1, y1, x2, y2 = map(int, sys.argv[1].split())
x, y = float(sys.argv[2]), float(sys.argv[3])
# The reported top is cut off where the page runs under the status bar, so measure from the
# bottom edge and the (never cut) width instead.
h = (x2 - x1) * 792 / 612
top = y2 - h
print(int(x1 + (x2 - x1) * x / 612), int(top + h * (792 - y) / 792))
PY
}
open_doc() {
  adb shell am start -W -a android.intent.action.VIEW -d "file://$DOC" -t application/pdf -n "$PKG/.MainActivity" > /dev/null
}

adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

echo "--- Home"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
wait_for home 'text="Combine files"' 30 "Home doesn't show the tools"
shot 01-home
show home
grep -q 'text="Combine files"' "$OUT/home.xml" || fail "Home doesn't list the tools"
echo "PASS: Home"

echo "--- Opening a PDF"
python3 "$HERE/make_test_pdf.py" "$OUT/smoke.pdf"
adb shell ls -ld "$DOC_DIR" || adb shell mkdir -p "$DOC_DIR"
adb push "$OUT/smoke.pdf" "$DOC" > /dev/null
open_doc
wait_for reader 'text="1 / 5"' 30 "the PDF didn't open on page 1 of 5"
sleep 3
shot 02-reader
show reader
grep -q 'smoke.pdf' "$OUT/reader.xml" || fail "the title bar doesn't show the file name"
echo "PASS: the PDF opens"

echo "--- Search"
tap reader "Search"
sleep 1
adb shell input text "elephant"
adb shell input keyevent KEYCODE_ENTER
wait_for search 'text="1 of 2"' 30 "searching for 'elephant' didn't find 2 matches"
sleep 1
shot 03-search
dump search-hit
grep -Eq 'text="2 / 5"' "$OUT/search-hit.xml" || fail "the first match didn't scroll to page 2"
tap search-hit "Next match"
sleep 2
dump search-next
grep -q 'text="2 of 2"' "$OUT/search-next.xml" || fail "Next match didn't move to the second match"
grep -Eq 'text="4 / 5"' "$OUT/search-next.xml" || fail "the second match isn't on page 4"
hide_keyboard
dump search-close
tap search-close "Close search"
sleep 1
echo "PASS: search"

echo "--- Contents panel"
dump before-panel
tap before-panel "Contents and pages"
wait_for panel 'text="Chapter 3"' 15 "the table of contents doesn't list the chapters"
shot 04-contents
tap panel "Chapter 1"
sleep 2
dump after-panel
grep -q 'text="2 / 5"' "$OUT/after-panel.xml" || fail "tapping Chapter 1 didn't go to page 2"
echo "PASS: table of contents"

echo "--- Night mode"
tap after-panel "More options"
sleep 1
dump menu
tap menu "Night mode"
sleep 2
shot 05-night
dump night
tap night "More options"
sleep 1
dump menu2
tap menu2 "Day mode"
sleep 1
echo "PASS: night mode"

echo "--- Highlight and draw on page 1"
dump goto
tap goto "2 / 5"
sleep 1
dump goto-dialog
tap goto-dialog "Page (1–5)"
sleep 1
adb shell input text "1"
adb shell input keyevent KEYCODE_ENTER
sleep 2
dump p1
grep -q 'text="1 / 5"' "$OUT/p1.xml" || fail "Go to page didn't go to page 1"
tap p1 "Comment"
sleep 1
dump comment
tap comment "Highlight"
sleep 1
dump hl
# "Highlight this sentence please." is at y=400, 20 pt high, from x=72.
read -r ax ay < <(page_xy hl 75 406)
read -r bx by < <(page_xy hl 330 406)
echo "  page 1 is at $(python3 "$HERE/find_text.py" "$OUT/hl.xml" "Page 1" bounds); swiping from $ax,$ay to $bx,$by"
adb shell input swipe "$ax" "$ay" "$bx" "$by" 800
sleep 1
dump hl-done
show hl-done
grep -q 'Highlight · 1 change' "$OUT/hl-done.xml" || fail "dragging over the text didn't highlight it"
tap hl-done "Draw"
sleep 1
dump draw
read -r cx cy < <(page_xy draw 100 200)
read -r dx dy < <(page_xy draw 400 150)
echo "  drawing from $cx,$cy to $dx,$dy"
adb shell input swipe "$cx" "$cy" "$dx" "$dy" 600
sleep 1
shot 06-comment
dump drawn
grep -Eq 'Draw · 2 changes' "$OUT/drawn.xml" || fail "the highlight and the drawing weren't both recorded"
tap drawn "Done"
wait_for applied 'text="Comment"' 30 "Done didn't go back to reading"
grep -q 'Not saved yet' "$OUT/applied.xml" || fail "the document isn't marked as changed"
echo "PASS: highlight and drawing"

echo "--- Fill & Sign: a tick"
tap applied "Fill & Sign"
sleep 1
dump fill
tap fill "Tick"
sleep 1
dump tick
tap tick "Place it"
sleep 1
dump ticked
tap ticked "Done"
wait_for signed 'text="Fill &amp; Sign"' 30 "Done didn't go back to reading after Fill & Sign"
shot 07-signed
echo "PASS: Fill & Sign"

echo "--- Saving"
tap signed "Save"
sleep 4
dump saved
grep -q 'Not saved yet' "$OUT/saved.xml" && fail "the document still says it isn't saved"
adb pull "$DOC" "$OUT/saved.pdf" > /dev/null
for kind in Highlight Ink; do
  grep -aq "/$kind" "$OUT/saved.pdf" || fail "the saved PDF has no $kind annotation"
done
echo "PASS: annotations saved into the file"

echo "--- Organize: turn page 1"
tap saved "Organize"
wait_for organize 'text="Apply changes"' 20 "Organize pages didn't open"
tap organize "Page 1"
sleep 1
dump org-sel
tap org-sel "Turn right"
sleep 1
shot 08-organize
tap org-sel "Apply changes"
wait_for organized 'Not saved yet' 30 "applying the page turn didn't come back to the document"
shot 09-turned
echo "PASS: organize"

echo "--- Comments list"
tap organized "Contents and pages"
wait_for panel2 'text="Comments"' 15 "the side panel didn't open"
sleep 1
tap panel2 "Comments"
wait_for comments 'Page 1 · Highlight' 20 "the Comments list doesn't show the highlight"
shot 10-comments
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "--- Leaving asks about unsaved changes"
adb shell input keyevent KEYCODE_BACK
sleep 1
dump leave
grep -q 'Save your changes?' "$OUT/leave.xml" || fail "leaving with unsaved changes didn't ask"
tap leave "Save"
wait_for home-again 'text="Combine files"' 30 "saving on the way out didn't return Home"
shot 11-home-recent
grep -q 'smoke.pdf' "$OUT/home-again.xml" || fail "the file isn't in Recent"
echo "PASS: save on leaving, Recent list"

if crashed; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
