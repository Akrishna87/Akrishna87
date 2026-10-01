#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK and downloads two torrents from a seeder
# running on the CI machine (ci/seed.py, reachable from the emulator at 10.0.2.2). Checks that:
# links from other apps are only added after "Download" in an "Add this torrent?" question;
# a magnet link shared to the app and one opened like a browser link both download, the files land
# in Download/Torrents with the right contents, pausing works, a background service runs while
# downloading, a "finished" notification appears, torrents are remembered after the app is
# closed, and deleting a torrent removes its files.
# Usage: smoke-test.sh <apk> <seeder work dir> <output dir>
set -euo pipefail

APK="$1"
SEED="$2"
OUT="$3"
PKG=io.github.akrishna87.mytorrents
DIR=/sdcard/Download/Torrents
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o '\(text\|content-desc\)="[^"]\+"' "$OUT/failure.xml" | head -60 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  echo "Crashes in logcat:"; grep -A25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60 || true
  echo "My Torrents log lines:"; grep "MyTorrents\|libtorrent\|torrent4j\|UnsatisfiedLink" "$OUT/logcat.txt" | tail -40 || true
  echo "App process log:"; pid=$(adb shell pidof "$PKG" | tr -d '\r'); [ -n "$pid" ] && grep " $pid " "$OUT/logcat.txt" | grep -v "ImeTracker\|InputMethod" | tail -40 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null
    # Wave away "isn't responding" popups from the emulator's own apps.
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
wait_for() { # wait_for <seconds> <dump name> <grep pattern> <what>
  local i
  for i in $(seq 1 "$1"); do
    dump "$2"
    grep -q "$3" "$OUT/$2.xml" && return 0
    sleep 1
  done
  fail "timed out waiting for $4"
}
launch() { adb shell am start -W -n "$PKG/.MainActivity" > /dev/null; sleep 3; }
check_file() { # check_file <path relative to the seeder's content dir>
  local want got
  want=$(grep "  $1\$" "$SEED/md5sums.txt" | cut -d' ' -f1)
  got=$(adb shell "md5sum '$DIR/$1' 2>/dev/null" | cut -d' ' -f1)
  [ -n "$want" ] && [ "$want" = "$got" ] || fail "$DIR/$1 is missing or wrong (want $want, got '$got')"
  echo "PASS: $1 downloaded correctly"
}

ALBUM=$(sed 's/127\.0\.0\.1/10.0.2.2/' "$SEED/Smoke-Album.magnet")
SINGLE=$(sed 's/127\.0\.0\.1/10.0.2.2/' "$SEED/Smoke-Single.magnet")
echo "Album:  $ALBUM"
echo "Single: $SINGLE"

adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb logcat -c

echo "--- Empty list"
launch
dump empty
shot 1-empty
grep -q "No torrents yet" "$OUT/empty.xml" || fail "the empty list isn't shown"
echo "PASS: app opens on the empty list"

echo "--- The Add sheet"
tap empty "Add a torrent"
sleep 2
dump add
shot 2-add
grep -q "Open a .torrent file" "$OUT/add.xml" || fail "the Add sheet didn't open"
grep -q "Magnet link" "$OUT/add.xml" || fail "the Add sheet has no magnet link box"
echo "PASS: the Add sheet opens"
adb shell input keyevent KEYCODE_BACK
sleep 2

echo "--- Sharing a magnet link to the app"
# (Typing a long link with the emulator's keyboard is too slow and unreliable to test.)
share_album() {
  adb shell "am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'Check this out: $ALBUM' -n $PKG/.MainActivity" > /dev/null
}
share_album
wait_for 20 confirm-album "Add this torrent?" "the 'Add this torrent?' question"
grep -q "Smoke Album" "$OUT/confirm-album.xml" || fail "the question doesn't name the torrent"
shot 3-confirm
tap confirm-album "Cancel"
sleep 3
dump after-cancel
grep -q "No torrents yet" "$OUT/after-cancel.xml" || fail "a link from another app was added without saying Download"
echo "PASS: links from other apps aren't added until you say so"
share_album
wait_for 20 confirm-album-2 "Add this torrent?" "the 'Add this torrent?' question again"
tap confirm-album-2 "Download"
wait_for 20 list-album "Smoke Album" "the shared magnet link to show up"
echo "PASS: a shared magnet link is added"

echo "--- Opening a magnet link like a browser does"
adb shell "am start -W -a android.intent.action.VIEW -d '$SINGLE'" > /dev/null
wait_for 20 confirm-single "Add this torrent?" "the 'Add this torrent?' question"
tap confirm-single "Download"
wait_for 20 list-both "Smoke Single.bin" "the opened magnet link to show up"
echo "PASS: an opened magnet link is added"

echo "--- Downloading"
wait_for 30 downloading "% of " "a download in progress"
shot 4-downloading
adb shell dumpsys activity services "$PKG" > "$OUT/services.txt"
grep -q "isForeground=true" "$OUT/services.txt" || fail "no foreground service while downloading"
echo "PASS: downloads run in a foreground service"

echo "--- Pausing and resuming"
tap downloading "Smoke Single.bin"
sleep 2
dump single
grep -q 'text="Pause"' "$OUT/single.xml" || fail "Smoke Single.bin already finished before it could be paused"
tap single "Pause"
wait_for 10 single-paused 'text="Resume"' "the Resume button"
grep -q "Paused" "$OUT/single-paused.xml" || fail "the torrent doesn't say it's paused"
shot 5-paused
echo "PASS: pausing works"
tap single-paused "Resume"
wait_for 10 single-resumed 'text="Pause"' "the Pause button again"
echo "PASS: resuming works"
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "--- Finishing"
for _ in $(seq 1 120); do
  dump list-done
  [ "$(grep -o 'Finished · ' "$OUT/list-done.xml" | wc -l)" -ge 2 ] && break
  sleep 1
done
[ "$(grep -o 'Finished · ' "$OUT/list-done.xml" | wc -l)" -ge 2 ] || fail "both torrents didn't finish"
shot 6-finished
echo "PASS: both torrents finished"
check_file "Smoke Album/Track 1.bin"
check_file "Smoke Album/Track 2.bin"
check_file "Smoke Album/Notes.txt"
check_file "Smoke Single.bin"
adb shell dumpsys notification --noredact > "$OUT/notifications.txt"
grep -q "Download finished" "$OUT/notifications.txt" || fail "no 'Download finished' notification"
echo "PASS: a 'Download finished' notification was shown"

echo "--- Torrent details and files"
tap list-done "Smoke Album"
sleep 2
dump album
shot 7-album
grep -q "Track 2.bin" "$OUT/album.xml" || fail "the album's files aren't listed"
echo "PASS: the details screen lists the files"
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "--- Remembered after the app is closed"
adb shell am force-stop "$PKG"
sleep 1
launch
wait_for 15 relaunched "Smoke Single.bin" "the torrents after reopening"
grep -q "Smoke Album" "$OUT/relaunched.xml" || fail "the album is gone after reopening"
wait_for 15 relaunched "Finished · " "the torrents to show as finished after reopening"
shot 8-reopened
echo "PASS: torrents are remembered"

echo "--- Deleting a torrent and its files"
tap relaunched "Smoke Single.bin"
sleep 2
dump single-details
tap single-details "Delete"
sleep 1
dump delete-dialog
tap delete-dialog "Also delete the downloaded files"
sleep 1
dump delete-dialog-checked
tap delete-dialog-checked "Remove"
sleep 4
dump after-delete
shot 9-after-delete
grep -q "Smoke Single.bin" "$OUT/after-delete.xml" && fail "the deleted torrent is still listed"
adb shell "ls '$DIR/Smoke Single.bin'" > /dev/null 2>&1 && fail "the deleted torrent's file is still there"
check_file "Smoke Album/Track 1.bin"
echo "PASS: deleting removes the torrent and its files, and leaves the others"

if adb logcat -d | grep -A5 "FATAL EXCEPTION" | grep -q "$PKG"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
