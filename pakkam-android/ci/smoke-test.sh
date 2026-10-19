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
# A photo of a signature on paper, for Fill & Sign later; the phone's photo library picks it up meanwhile.
adb push "$HERE/signature-photo.jpg" /sdcard/Pictures/signature-photo.jpg > /dev/null
adb shell content call --method scan_volume --uri content://media --arg external_primary > /dev/null 2>&1 || true
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/signature-photo.jpg > /dev/null 2>&1 || true
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

echo "--- Fit the page to the screen"
# Page 5 is twice as tall as the others: at the screen's width it runs off the bottom.
dump fit-goto
tap fit-goto "/ 5"
sleep 1
dump fit-goto-dialog
tap fit-goto-dialog "Page (1–5)"
sleep 1
adb shell input text "5"
adb shell input keyevent KEYCODE_ENTER
sleep 2
dump fit0
grep -q 'text="5 / 5"' "$OUT/fit0.xml" || fail "Go to page didn't go to page 5"
tap fit0 "More options"
sleep 1
dump fit-menu
tap fit-menu "Fit page to screen"
wait_for fitted 'text="[0-9]+%"' 15 "Fit page to screen didn't zoom out"
shot 05b-fit-page
zoomed=$(grep -o 'text="[0-9]*%"' "$OUT/fitted.xml" | head -1 | sed 's/text="//; s/"$//')
echo "  zoomed out to $zoomed"
[ "${zoomed%\%}" -lt 100 ] || fail "Fit page to screen should make the page smaller than the screen's width, not $zoomed"
# The zoom badge takes it back to the normal size.
tap fitted "$zoomed"
sleep 2
dump unfitted
grep -Eq 'text="[0-9]+%"' "$OUT/unfitted.xml" && fail "tapping the zoom badge didn't go back to the normal size"
echo "PASS: fit page and back"

echo "--- Reading mode"
dump to-reading
tap to-reading "Read mode"
wait_for reading 'Page 1 of [0-9]+' 30 "Reading mode didn't lay out the text"
shot 06-reading
grep -q 'Highlight this sentence please' "$OUT/reading.xml" || fail "Reading mode doesn't show the PDF's text"
# Long-press "test" in the title line for its meaning (found from the emulator's 1080×2400 layout).
adb shell input swipe 516 196 516 196 900
wait_for reading-define 'trying something to find out about it' 20 "long-pressing a word in Reading mode didn't show its meaning"
shot 06b-reading-define
grep -q 'Search the web' "$OUT/reading-define.xml" || fail "the ways to look a word up further are missing"
adb shell input keyevent KEYCODE_BACK
sleep 2
# Hold on "Highlight" in the first line, slide to "sentence", and pick yellow to highlight the words between.
adb shell input motionevent DOWN 110 270
sleep 1.5
adb shell input motionevent MOVE 250 272
adb shell input motionevent MOVE 400 272
sleep 0.5
adb shell input motionevent UP 400 272
wait_for reading-sel 'content-desc="Yellow"' 15 "choosing several words didn't show the highlight colours"
shot 06c-reading-select
tap reading-sel "Yellow"
# Undo takes it back, and Redo (in the menus) puts it back.
wait_for reading-hl 'text="Highlighted"' 10 "highlighting didn't offer Undo"
tap reading-hl "Undo"
sleep 1
read -r rw rh < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')
adb shell input tap $((rw / 2)) $((rh / 2))
wait_for reading-undone 'content-desc="Redo"[^>]*enabled="true"' 10 "Undo didn't take the highlight back"
tap reading-undone "Redo"
sleep 1
adb shell input tap $((rw / 2)) $((rh / 2))
sleep 1
# Hold on "elephant" and add a note to it from the dictionary card.
adb shell input swipe 240 398 240 398 900
wait_for reading-word 'Add note' 20 "the dictionary card doesn't offer a note"
tap reading-word "Add note"
wait_for note-dialog 'Your note' 15 "the note box didn't open"
tap note-dialog "Your note"
sleep 1
adb shell input text "Remember%sthis"
sleep 1
dump note-typed
tap note-typed "Save note"
sleep 2
shot 06d-reading-marked
# Tap the middle for the controls, then pick sepia pages.
read -r rw rh < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')
adb shell input tap $((rw / 2)) $((rh / 2))
sleep 2
dump reading-chrome
tap reading-chrome "Text and colours"
sleep 2
dump reading-style
tap reading-style "Sepia pages"
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
shot 07-reading-sepia
dump reading-sepia
grep -Eq 'Page 1 of [0-9]+' "$OUT/reading-sepia.xml" || fail "Reading mode lost its place after changing colours"
# Both show in the list of notes and highlights.
# The top bar has the quick actions; the rest are under More.
grep -q 'content-desc="Read aloud"' "$OUT/reading-sepia.xml" || fail "Reading mode's menu has no Read aloud"
grep -q 'content-desc="Share"' "$OUT/reading-sepia.xml" || fail "Reading mode's menu has no Share"
tap reading-sepia "More options"
sleep 1
dump reading-more
tap reading-more "Notes and highlights"
wait_for reading-notes 'Remember this' 15 "the note isn't in Notes and highlights"
grep -q 'Highlight this sentence' "$OUT/reading-notes.xml" || fail "the highlight isn't in Notes and highlights"
grep -q 'elephant' "$OUT/reading-notes.xml" || fail "the noted word isn't in Notes and highlights"
shot 07a-reading-notes
adb shell input keyevent KEYCODE_BACK
sleep 2
# The Page view button goes to page view (Back would close the book).
dump reading-sepia
tap reading-sepia "More options"
sleep 1
dump reading-more2
tap reading-more2 "Page view"
wait_for back-to-pages 'text="[0-9] / 5"' 20 "Page view didn't go back to the PDF"
echo "PASS: reading mode and its dictionary"

echo "--- Define a word in page view"
dump define-page
read -r qx qy < <(page_xy define-page 100 406)
adb shell input swipe "$qx" "$qy" "$qx" "$qy" 900
sleep 2
dump define-sel
tap define-sel "Define"
wait_for define-sheet 'the most interesting or memorable part' 20 "Define didn't show the meaning of 'Highlight'"
shot 07b-define
adb shell input keyevent KEYCODE_BACK
sleep 2
# A colour in the selection bar highlights the chosen word in it; Undo and Redo work on it.
dump define-done
tap define-done "Green"
sleep 1
dump green-done
tap green-done "Undo"
wait_for green-undone 'content-desc="Redo"[^>]*enabled="true"' 10 "Undo didn't take the green highlight back"
tap green-undone "Redo"
sleep 1
sleep 1
echo "PASS: define in page view"

echo "--- Highlight and draw on page 1"
# Android's "copied" clipboard popup can hold the screen for a few seconds after Copy.
wait_for goto 'text="[0-9] / 5"' 20 "the page counter didn't come back after copying a word"
tap goto "/ 5"
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
# Two marks from Reading mode and the green one wait to be saved too.
grep -q 'Highlight · 4 changes' "$OUT/hl-done.xml" || fail "dragging over the text didn't highlight it"
tap hl-done "Draw"
sleep 1
dump draw
read -r cx cy < <(page_xy draw 100 200)
read -r dx dy < <(page_xy draw 400 150)
echo "  drawing from $cx,$cy to $dx,$dy"
adb shell input swipe "$cx" "$cy" "$dx" "$dy" 600
sleep 1
shot 08-comment
dump drawn
grep -Eq 'Draw · 5 changes' "$OUT/drawn.xml" || fail "the highlight and the drawing weren't both recorded"
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
echo "--- Fill & Sign: a signature from a photo of paper"
tap ticked "Sign"
wait_for sign-sheet 'Choose a photo of it on paper' 15 "the signature sheet doesn't offer a photo"
tap sign-sheet "Choose a photo of it on paper"
wait_for picker 'content-desc="[^"]*(Photo|photo|Image|image) taken' 30 "the photo picker didn't show the signature photo"
shot 09a-photo-picker
tap picker "taken"
wait_for sig-photo 'Signature preview' 40 "the signature wasn't lifted off the photo"
shot 09b-signature-photo
tap sig-photo "Use it"
sleep 2
dump sig-place
tap sig-place "Place it"
sleep 1
dump ticked
tap ticked "Done"
wait_for signed 'text="Fill &amp; Sign"' 30 "Done didn't go back to reading after Fill & Sign"
shot 09-signed
echo "PASS: Fill & Sign"

echo "--- Saving"
tap signed "Save"
sleep 4
dump saved
grep -q 'Not saved yet' "$OUT/saved.xml" && fail "the document still says it isn't saved"
adb pull "$DOC" "$OUT/saved.pdf" > /dev/null
for kind in Highlight Ink Stamp; do
  grep -aq "/$kind" "$OUT/saved.pdf" || fail "the saved PDF has no $kind annotation"
done
grep -aq "Remember this" "$OUT/saved.pdf" || fail "the note from Reading mode isn't in the saved PDF"
echo "PASS: annotations saved into the file"

echo "--- Organize: turn page 1"
tap saved "Organize"
wait_for organize 'text="Apply changes"' 20 "Organize pages didn't open"
tap organize "Page 1"
sleep 1
dump org-sel
tap org-sel "Turn right"
sleep 1
shot 10-organize
tap org-sel "Apply changes"
wait_for organized 'Not saved yet' 30 "applying the page turn didn't come back to the document"
shot 11-turned
echo "PASS: organize"

echo "--- Comments list"
tap organized "Contents and pages"
wait_for panel2 'text="Comments"' 15 "the side panel didn't open"
sleep 1
tap panel2 "Comments"
wait_for comments 'Page 1 · Highlight' 20 "the Comments list doesn't show the highlight"
shot 12-comments
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "--- Leaving asks about unsaved changes"
adb shell input keyevent KEYCODE_BACK
sleep 1
dump leave
grep -q 'Save your changes?' "$OUT/leave.xml" || fail "leaving with unsaved changes didn't ask"
tap leave "Save"
wait_for home-again 'text="Combine files"' 30 "saving on the way out didn't return Home"
shot 13-home-recent
grep -q 'smoke.pdf' "$OUT/home-again.xml" || fail "the file isn't in Recent"
echo "PASS: save on leaving, Recent list"

echo "--- Back in Reading mode closes the book, which opens in Reading mode again"
tap home-again "smoke.pdf"
wait_for reopened 'text="[0-9] / 5"' 40 "after choosing page view, the file didn't open in page view"
grep -Eq 'Page [0-9]+ of [0-9]+' "$OUT/reopened.xml" && fail "the file opened in Reading mode after choosing page view"
tap reopened "Read mode"
wait_for reading-again 'Page [0-9]+ of [0-9]+' 30 "Reading mode didn't open"
adb shell input keyevent KEYCODE_BACK
wait_for home-3 'text="Combine files"' 20 "Back in Reading mode didn't close the book"
tap home-3 "smoke.pdf"
wait_for reopened-2 'Page [0-9]+ of [0-9]+' 40 "the file last read in Reading mode didn't open in Reading mode"
shot 14-reopened-reading
# A tap at the top of the page brings the menus back.
read -r rw rh < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')
adb shell input tap $((rw / 2)) $((rh / 12))
sleep 2
dump reopened-chrome
tap reopened-chrome "More options"
sleep 1
dump reopened-more
tap reopened-more "Page view"
wait_for reopened-pages 'text="[0-9] / 5"' 20 "Page view didn't open from Reading mode"
adb shell input keyevent KEYCODE_BACK
wait_for home-4 'text="Combine files"' 20 "leaving the document didn't return Home"
echo "PASS: Reading mode remembered for each file"

if crashed; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
