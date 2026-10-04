#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, shares a link to SongGrab (as the
# YouTube app's Share button does), waits for the song to be converted to MP3 and saved in
# Music/SongGrab, then plays it from the app and checks it keeps playing in the background.
# Then it switches to Video, saves a clip to Movies/SongGrab and opens it in the video player.
# The song and clip are made by this script and served from the host, so the test doesn't
# depend on any website. A YouTube download is tried at the end too, but only warns if it fails, because
# YouTube often asks cloud machines to sign in.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.songgrab
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o 'text="[^"]\+"' "$OUT/failure.xml" | head -60 || true
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
    # The emulator's own apps sometimes freeze while it warms up; wave the popup away.
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
wait_for() { # wait_for <dump name> <grep -E pattern> <seconds> <what> [fail pattern]
  local end=$((SECONDS + $3))
  while [ $SECONDS -lt $end ]; do
    dump "$1"
    grep -Eq "$2" "$OUT/$1.xml" && return 0
    if [ -n "${5:-}" ] && grep -Eq "$5" "$OUT/$1.xml"; then show "$1"; fail "$4 (it failed)"; fi
    sleep 3
  done
  show "$1"
  fail "$4 (waited $3 s)"
}
share() { # share <text>
  adb shell "am start -W -a android.intent.action.SEND -t text/plain -n $PKG/.MainActivity --es android.intent.extra.TEXT '$1'" > /dev/null
}
session() { adb shell dumpsys media_session > "$OUT/session.txt"; }
playing() { session; grep -Eq "\{state=(PLAYING|3)" "$OUT/session.txt"; }

# A 20-second tone named like a YouTube title, served to the emulator (10.0.2.2 is the host).
SONGS="$(mktemp -d)"
ffmpeg -nostdin -loglevel error -f lavfi -i "sine=frequency=440:duration=20" -c:a libvorbis "$SONGS/Test Artist - Test Tone (Official Video).ogg"
python3 -m http.server 8765 --bind 0.0.0.0 --directory "$SONGS" > "$OUT/http.log" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
TONE_URL="http://10.0.2.2:8765/Test%20Artist%20-%20Test%20Tone%20%28Official%20Video%29.ogg"
# A 6-second 720p clip with sound.
ffmpeg -nostdin -loglevel error -f lavfi -i "testsrc=size=1280x720:rate=25:duration=6" -f lavfi -i "sine=frequency=660:duration=6" \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "$SONGS/Test Artist - Test Clip (Official Video).mp4"
CLIP_URL="http://10.0.2.2:8765/Test%20Artist%20-%20Test%20Clip%20%28Official%20Video%29.mp4"

adb wait-for-device
adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

echo "--- Launching the app"
adb shell am start -W -n "$PKG/.MainActivity"
wait_for home 'text="No songs yet"' 60 "the app didn't open on an empty song list"
shot 1-home
show home
echo "PASS: the app opens"

echo "--- Sharing a link to SongGrab, as the YouTube app's Share button does"
share "Listen to this $TONE_URL"
wait_for saving 'text="Downloads"' 20 "sharing a link didn't start a download"
shot 2-saving
show saving
wait_for saved 'text="Saved"' 240 "the shared song wasn't saved" 'content-desc="Try again"'
shot 3-saved
show saved
grep -q 'text="Test Tone"' "$OUT/saved.xml" || fail "the saved song isn't titled 'Test Tone' (the title wasn't tidied)"
grep -q 'Test Artist' "$OUT/saved.xml" || fail "the saved song doesn't show the artist 'Test Artist'"
echo "PASS: a shared link is saved as a song, with a tidied title and artist"

FILES=$(adb shell ls /sdcard/Music/SongGrab/ 2>/dev/null | tr -d '\r')
echo "  Music/SongGrab: $FILES"
echo "$FILES" | grep -q "Test Artist - Test Tone.mp3" || fail "Music/SongGrab doesn't have 'Test Artist - Test Tone.mp3'"
echo "PASS: the MP3 is in the phone's Music/SongGrab folder"

echo "--- Playing it"
tap saved "Test Tone" last # the song in the list, not the finished download above it
for _ in $(seq 1 20); do playing && break; sleep 1; done
playing || fail "tapping the song didn't play it"
sleep 2
dump playing
shot 4-playing
grep -q 'content-desc="Pause"' "$OUT/playing.xml" || fail "the mini player doesn't show a Pause button"
echo "PASS: the song plays"
# Tapping the mini player (left of its Pause button) opens the full player.
PAUSE_XY=$(python3 "$HERE/find_text.py" "$OUT/playing.xml" "Pause") || fail "no Pause button"
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f1)
adb shell input tap $((W / 3)) "${PAUSE_XY#* }"
sleep 2
dump player
shot 5-player
grep -q 'NOW PLAYING' "$OUT/player.xml" || fail "the full player didn't open"
echo "PASS: the full player opens"

echo "--- Background playback"
adb shell input keyevent KEYCODE_HOME
sleep 5
playing || fail "playback stopped when the app went to the background"
echo "PASS: keeps playing in the background"
adb shell cmd statusbar expand-notifications
sleep 2
shot 6-notification
adb shell cmd statusbar collapse
adb shell cmd media_session dispatch pause || adb shell input keyevent KEYCODE_MEDIA_PAUSE

echo "--- Sharing another link while the full player is open shows the download"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 2
dump before-youtube
grep -q 'NOW PLAYING' "$OUT/before-youtube.xml" || echo "(the full player had closed already)"
echo "--- Trying a YouTube video (Sintel trailer, CC BY 3.0 Blender Foundation); this only warns if it fails"
share "https://youtu.be/eRsGyueVLvQ?si=smoke"
sleep 3
dump youtube-start
grep -q 'NOW PLAYING' "$OUT/youtube-start.xml" && fail "sharing a link left the full player covering the download"
grep -q 'text="Downloads"' "$OUT/youtube-start.xml" || fail "the shared YouTube link isn't in the download list"
echo "PASS: a shared link brings the download list into view"
YT_END=$((SECONDS + 300))
YT_RESULT="timed out"
while [ $SECONDS -lt $YT_END ]; do
  dump youtube
  if [ "$(grep -o 'text="Saved"' "$OUT/youtube.xml" | wc -l)" -ge 2 ]; then YT_RESULT="saved"; break; fi
  if grep -q 'content-desc="Try again"' "$OUT/youtube.xml"; then YT_RESULT="failed"; break; fi
  sleep 5
done
shot 7-youtube
show youtube
if [ "$YT_RESULT" = saved ]; then
  echo "PASS: a YouTube video was saved as a song"
else
  echo "::warning::The YouTube download $YT_RESULT on the CI machine (YouTube often blocks cloud servers). On a phone it normally works; see the screenshot 7-youtube.png."
fi

echo "--- Saving a video"
dump before-video
tap before-video "Video"
sleep 1
dump video-choice
shot 8-video-choice
grep -q 'text="Video quality"' "$OUT/video-choice.xml" || fail "choosing Video doesn't show the quality choice"
grep -q 'text="Save video · 720p"' "$OUT/video-choice.xml" || fail "the save button doesn't say 'Save video · 720p'"
echo "PASS: Video shows the quality choice"
share "Watch $CLIP_URL"
VIDEOS=""
for _ in $(seq 1 80); do
  VIDEOS=$(adb shell ls /sdcard/Movies/SongGrab/ 2>/dev/null | tr -d '\r' || true)
  echo "$VIDEOS" | grep -q "Test Artist - Test Clip.mp4" && break
  sleep 3
done
echo "  Movies/SongGrab: $VIDEOS"
echo "$VIDEOS" | grep -q "Test Artist - Test Clip.mp4" || { dump video-failed; show video-failed; fail "Movies/SongGrab doesn't have 'Test Artist - Test Clip.mp4'"; }
echo "PASS: the video is in the phone's Movies/SongGrab folder"
sleep 2
# Clear the finished downloads and scroll down, so the library rows are on screen.
dump video-done
tap video-done "Clear"
sleep 1
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); SW=${SIZE%x*}; SH=${SIZE#*x}
adb shell input swipe $((SW / 2)) $((SH * 3 / 4)) $((SW / 2)) $((SH / 4)) 400
sleep 2
dump video-saved
shot 9-video-saved
show video-saved
grep -q 'text="Test Clip"' "$OUT/video-saved.xml" || fail "the video isn't in the list as 'Test Clip'"
grep -q 'MP4 720p' "$OUT/video-saved.xml" || fail "the video's row doesn't say 'MP4 720p'"
grep -q 'text="Videos · 1"' "$OUT/video-saved.xml" || fail "there's no 'Videos · 1' filter"
echo "PASS: the video is in the library"
tap video-saved "Test Clip" last # the library row, not the finished download above it
sleep 4
shot 10-video-player
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | grep -q VideoActivity \
  || fail "tapping the video didn't open the video player"
echo "PASS: the video opens in the video player"
adb shell input keyevent KEYCODE_BACK
sleep 2

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
