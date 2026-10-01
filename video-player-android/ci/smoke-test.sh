#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, puts test videos on the "phone", and
# checks that the app lists them, plays them (turning landscape for wide videos), skips with the
# media "next" button, remembers where you stopped, browses folders, searches, keeps playing in
# picture-in-picture, and opens a video handed over by another app ("Open with").
# Usage: smoke-test.sh <apk> <videos dir> <output dir>
set -euo pipefail

APK="$1"
VIDEOS="$2"
OUT="$3"
PKG=io.github.akrishna87.myvideos
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o '\(text\|content-desc\)="[^"]\+"' "$OUT/failure.xml" | head -40 || true
  fi
  adb shell dumpsys media_session > "$OUT/session.txt" 2>/dev/null || true
  adb logcat -d > "$OUT/logcat.txt" || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null
    # The emulator's own apps sometimes freeze while it warms up; wave the "isn't responding"
    # popup away so it doesn't cover the app. Crashes of My Videos are caught from logcat at the end.
    if grep -q 'text="Viewing full screen"' "$OUT/$1.xml"; then # in case the tip shows anyway
      echo "(dismissing the 'Viewing full screen' tip)"
      adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Got it")
      sleep 2
      continue
    fi
    grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml" || return 0
    echo "(dismissing a system 'isn't responding' popup)"
    python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait")
    sleep 3
  done
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tap() { # tap <dump name> <text> [first|last]
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
session() { adb shell dumpsys media_session > "$OUT/session.txt"; }
playing() { session; grep -Eq "\{state=(PLAYING|3)" "$OUT/session.txt"; }
rotation() { grep -o '<hierarchy rotation="[0-9]"' "$OUT/$1.xml" | grep -o '[0-9]'; }
hide_keyboard() { if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi; }

adb wait-for-device
adb install -r "$APK"

echo "--- Copying test videos onto the emulator"
adb shell mkdir -p /sdcard/Movies/SmokeTest /sdcard/DCIM/Camera
for i in 1 2 3; do adb push "$VIDEOS/Smoke Video $i.mp4" /sdcard/Movies/SmokeTest/ > /dev/null; done
adb push "$VIDEOS/Smoke Portrait.mp4" /sdcard/DCIM/Camera/ > /dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null 2>&1 || true
for _ in $(seq 1 30); do
  adb shell content query --uri content://media/external/video/media --projection _display_name | grep -q "Smoke Portrait" && break
  sleep 2
done
adb shell content query --uri content://media/external/video/media --projection _id:_display_name:duration:width:height:relative_path || true

echo "--- Launching the app"
# Don't let other apps' "isn't responding" popups cover the screen during the test.
adb shell settings put global hide_error_dialogs 1 || true
# Skip Android's one-time "Viewing full screen" tip, which would cover the player.
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb shell pm grant "$PKG" android.permission.READ_MEDIA_VIDEO
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 6
dump library
shot 1-library
grep -q 'text="All videos"' "$OUT/library.xml" || fail "the video list didn't open"
grep -q 'text="Smoke Video 1"' "$OUT/library.xml" || fail "the video list doesn't show the test videos"
grep -q 'text="0:40"' "$OUT/library.xml" || fail "the video list doesn't show video lengths"
echo "PASS: lists the videos on the phone, with their lengths"

echo "--- Playing a video"
tap library "Smoke Video 1"
sleep 6
playing || fail "tapping a video didn't start playback"
grep -q "Smoke Video 1" "$OUT/session.txt" || fail "the player isn't showing the video's title"
echo "PASS: tapping a video plays it"
dump player
shot 2-player
[ "$(rotation player)" = 1 ] || [ "$(rotation player)" = 3 ] || fail "a wide video didn't turn the screen landscape"
echo "PASS: wide videos play in landscape"

echo "--- Player controls (paused, so they stay up while the screen is read)"
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 2
playing && fail "the media pause button didn't pause the video"
dump controls
shot 3-player-controls
grep -q 'content-desc="Load subtitles"' "$OUT/controls.xml" || fail "the player's top bar (subtitles, rotate…) didn't show"
grep -q 'content-desc="Rotate"' "$OUT/controls.xml" || fail "the rotate button is missing"
grep -q 'content-desc="Resize"' "$OUT/controls.xml" || fail "the resize button is missing"
echo "PASS: pausing shows the controls, including the top bar"

echo "--- Media 'next' and 'play' buttons (headphones)"
adb shell input keyevent KEYCODE_MEDIA_NEXT
sleep 2
adb shell input keyevent KEYCODE_MEDIA_PLAY
sleep 8
session
grep -q "Smoke Video 2" "$OUT/session.txt" || fail "the media next button didn't skip to the next video"
playing || fail "the next video isn't playing"
echo "PASS: media buttons skip to the next video and play it"

echo "--- Back to the list: Continue watching"
adb shell input keyevent KEYCODE_BACK
sleep 3
dump after-back
grep -q 'text="All videos"' "$OUT/after-back.xml" || fail "Back didn't return to the video list"
grep -q 'text="Continue watching"' "$OUT/after-back.xml" || fail "'Continue watching' doesn't show the videos that were started"
grep -q ' left"' "$OUT/after-back.xml" || fail "'Continue watching' doesn't say how much is left"
shot 4-continue-watching
echo "PASS: half-watched videos show under Continue watching"

echo "--- Resuming where it stopped"
tap after-back "Smoke Video 1" first # the Continue watching card is above the full list
sleep 3
dump resumed
shot 5-resumed
grep -q 'text="Resumed at 0:' "$OUT/resumed.xml" || fail "the video didn't pick up where it was left"
echo "PASS: picks up where you left off"
adb shell input keyevent KEYCODE_BACK
sleep 3

echo "--- Folders"
dump before-folders
tap before-folders "Folders"
sleep 2
dump folders
shot 6-folders
grep -q 'text="SmokeTest"' "$OUT/folders.xml" || fail "Folders doesn't show the SmokeTest folder"
grep -q 'text="Camera"' "$OUT/folders.xml" || fail "Folders doesn't show the Camera folder"
tap folders "SmokeTest"
sleep 2
dump folder
shot 7-folder
grep -q 'text="Play all"' "$OUT/folder.xml" || fail "the folder page has no Play all"
grep -q 'text="Smoke Video 3"' "$OUT/folder.xml" || fail "the folder page doesn't list its videos"
grep -q 'text="Smoke Portrait"' "$OUT/folder.xml" && fail "the folder page lists a video from another folder"
echo "PASS: folders list their videos"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK # Folders → Videos
sleep 1

echo "--- Search"
dump before-search
tap before-search "Search"
sleep 1
adb shell input text Portrait
sleep 2
hide_keyboard
dump search
shot 8-search
grep -q 'text="Smoke Portrait"' "$OUT/search.xml" || fail "searching for 'Portrait' didn't find the video"
grep -q 'text="Smoke Video 2"' "$OUT/search.xml" && fail "search shows videos that don't match"
echo "PASS: search finds videos"

echo "--- A portrait video, then picture-in-picture"
tap search "Smoke Portrait"
sleep 6
playing || fail "the portrait video didn't play"
dump portrait
shot 9-portrait
[ "$(rotation portrait)" = 0 ] || fail "a tall video didn't play upright"
echo "PASS: tall videos play upright"
adb shell input keyevent KEYCODE_HOME
sleep 5
shot 10-picture-in-picture
playing || fail "the video stopped when leaving the app instead of going picture-in-picture"
adb shell dumpsys activity activities | grep -i "pinned" | head -5 || true
echo "PASS: keeps playing in picture-in-picture"

echo "--- Open with My Videos (from another app)"
ID=$(adb shell content query --uri content://media/external/video/media --projection _id:_display_name | grep "Smoke Video 3" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
[ -n "$ID" ] || fail "couldn't find the test video's id in the media library"
adb shell am start -W -a android.intent.action.VIEW -t video/mp4 -d "content://media/external/video/media/$ID" -n "$PKG/.PlayerActivity"
sleep 6
playing || fail "a video opened from another app didn't play"
grep -q "Smoke Video 3" "$OUT/session.txt" || fail "the video opened from another app isn't the one playing"
shot 11-open-with
echo "PASS: plays videos opened from other apps"

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
