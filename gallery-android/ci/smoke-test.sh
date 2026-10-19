#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, puts test photos and a video on the
# "phone", and checks the welcome screen, the Library (All Photos, Months, Years), the viewer
# (favourite, info, video playback), Albums, Search and dark mode.
# Usage: smoke-test.sh <apk> <media dir> <output dir>
set -euo pipefail

APK="$1"
MEDIA="$2"
OUT="$3"
PKG=io.github.akrishna87.lumen
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o '\(text\|content-desc\)="[^"]\+"' "$OUT/failure.xml" | head -60 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  echo "App log:"
  grep -E "AndroidRuntime|FATAL|lumen|ExoPlayer.*(rror|ail)" "$OUT/logcat.txt" | tail -60 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3 4 5 6; do
    rm -f "$OUT/$1.xml"
    # Remove the last reading first: when uiautomator gives up it leaves the old file behind,
    # which would describe the wrong screen.
    adb shell rm -f /sdcard/ui.xml
    local said
    said=$(adb shell uiautomator dump /sdcard/ui.xml 2>&1 || true)
    if ! echo "$said" | grep -q "dumped to" || ! adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1 \
      || ! grep -q '<hierarchy' "$OUT/$1.xml"; then
      echo "(couldn't read the screen: ${said//$'\n'/ }; trying again)"
      rm -f "$OUT/$1.xml"
      sleep 1
      continue
    fi
    # The emulator's own apps sometimes freeze while it warms up; wave the popup away.
    if grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml"; then
      echo "(dismissing a system 'isn't responding' popup)"
      python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait")
      sleep 3
      continue
    fi
    return 0
  done
  [ -f "$OUT/$1.xml" ] || fail "couldn't read what's on screen ($1)"
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tap() { # tap <dump name> <text> [first|last]
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
has() { grep -qi "$2" "$OUT/$1.xml"; } # has <dump name> <grep pattern>
crashed() { adb logcat -d | grep -A3 "FATAL EXCEPTION" | grep -q "$PKG"; }

adb wait-for-device
adb install -r "$APK"

echo "--- Copying test photos and a video onto the emulator"
adb shell mkdir -p /sdcard/DCIM/Camera /sdcard/Pictures/Screenshots /sdcard/Pictures/Panoramas
for f in "$MEDIA"/camera/*; do adb push "$f" /sdcard/DCIM/Camera/ > /dev/null; done
for f in "$MEDIA"/screenshots/*; do adb push "$f" /sdcard/Pictures/Screenshots/ > /dev/null; done
for f in "$MEDIA"/panoramas/*; do adb push "$f" /sdcard/Pictures/Panoramas/ > /dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null 2>&1 || true
for _ in $(seq 1 30); do
  adb shell content query --uri content://media/external/file --projection _display_name | grep -q "Smoke Clip" && break
  sleep 2
done
adb shell content query --uri content://media/external/file --projection _id:_display_name:media_type:relative_path \
  --where "media_type IN (1,3)" || true

echo "--- The welcome screen, before access is given"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5
dump welcome
shot 01-welcome
has welcome 'text="Welcome to Lumen"' || fail "the welcome screen didn't show"
has welcome 'text="Continue"' || fail "the welcome screen has no Continue button"
echo "PASS: asks for access with a welcome screen"

echo "--- The Library"
adb shell am force-stop "$PKG"
adb shell pm grant "$PKG" android.permission.READ_MEDIA_IMAGES
adb shell pm grant "$PKG" android.permission.READ_MEDIA_VIDEO
adb shell am start -W -n "$PKG/.MainActivity"
sleep 6
dump library
shot 02-library
has library 'content-desc="Photo, ' || fail "the Library doesn't show the test photos"
has library 'text="All Photos"' || fail "the Years/Months/All Photos control is missing"
has library '[0-9] Photos, 1 Video' || fail "the Library doesn't count its photos and video"
has library 'text="Select"' || fail "the Select button is missing"
echo "PASS: the Library lists the photos with a count"

echo "--- Months and Years"
tap library "Months"
sleep 2
dump months
shot 03-months
has months "text=\"$(date +%B) $(date +%Y)\"" || fail "Months doesn't show $(date +%B) $(date +%Y)"
tap months "Years"
sleep 2
dump years
shot 04-years
has years "text=\"$(date +%Y)\"" || fail "Years doesn't show $(date +%Y)"
tap years "$(date +%Y)"
sleep 2
dump years-tapped
has years-tapped 'text="Months"' || fail "tapping a year didn't open Months"
tap years-tapped "All Photos"
sleep 2
echo "PASS: Months and Years show the photos' dates"

echo "--- Selecting"
dump library
tap library "Select"
sleep 1
dump select
tap select "Photo, " last
sleep 1
dump select
has select 'text="1 Photo Selected"' || fail "selecting a photo didn't say '1 Photo Selected'"
shot 05-select
tap select "Cancel"
sleep 1
echo "PASS: selecting photos"

echo "--- The viewer"
dump library
tap library "Photo, " last
sleep 3
dump viewer
shot 06-viewer
has viewer 'content-desc="Favorite"' || fail "the viewer has no Favorite button"
has viewer 'content-desc="Info"' || fail "the viewer has no Info button"
has viewer 'text="Today"' || fail "the viewer's title isn't the photo's date (Today)"
tap viewer "Favorite"
sleep 1
dump viewer
has viewer 'content-desc="Unfavorite"' || fail "tapping the heart didn't favourite the photo"
tap viewer "Info"
sleep 2
dump info
shot 07-info
has info 'text="Album"' || fail "Info doesn't show the album"
has info 'text="Camera"' || fail "Info doesn't say the photo is in Camera"
has info 'Pixel Smoke' || fail "Info doesn't show the camera from the photo's EXIF data"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
dump library
has library 'text="All Photos"' || fail "Back didn't close the viewer"
echo "PASS: the viewer opens a photo, favourites it and shows its info"

echo "--- Albums"
tap library "Albums"
sleep 2
dump albums
shot 08-albums
has albums 'text="My Albums"' || fail "Albums doesn't show My Albums"
for name in Recents Favorites Camera Screenshots Panoramas Videos; do
  has albums "\"$name" || fail "Albums doesn't list $name"
done
tap albums "Favorites"
sleep 2
dump favorites
shot 09-favorites
has favorites 'text="1 Photo"' || fail "Favorites doesn't hold the photo that was favourited"
tap favorites "Back"
sleep 2
dump albums
tap albums "Videos" last
sleep 2
dump videos
has videos 'content-desc="Video, ' || fail "the Videos album doesn't show the test video"
echo "PASS: albums for folders, Favorites and media types"

echo "--- Playing a video"
adb logcat -c
tap videos "Video, "
sleep 5
shot 10-video
adb logcat -d > "$OUT/video-log.txt"
grep -q "ExoPlayerImpl: Init" "$OUT/video-log.txt" || fail "the viewer didn't start the video player"
if grep -q "ExoPlaybackException" "$OUT/video-log.txt"; then fail "the video failed to play"; fi
adb shell input keyevent KEYCODE_BACK
sleep 2
echo "PASS: videos play in the viewer"

echo "--- Search"
dump videos
tap videos "Search"
sleep 2
dump search
tap search "Albums, Months, Files and More"
sleep 1
adb shell input text "Screen"
sleep 2
adb shell input keyevent KEYCODE_BACK # hide the keyboard
sleep 1
dump search
shot 11-search
has search 'text="Screenshots"' || fail "searching 'Screen' didn't suggest the Screenshots album"
has search '[0-9] Results' || fail "searching 'Screen' didn't find the screenshots"
echo "PASS: search finds albums and photos"

echo "--- Dark mode"
adb shell cmd uimode night yes || true
sleep 3
shot 12-dark-search
tap search "Library"
sleep 2
shot 13-dark-library
adb shell cmd uimode night no || true

if crashed; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
