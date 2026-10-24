#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, opens the PPF calculator and checks the
# published figure, types a new deposit, checks SCSS's quarterly payout, then on the Loans tab checks a
# home loan's EMI and what a part payment saves, as a shorter loan and as a lower EMI.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.semippu
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o '\(text\|content-desc\)="[^"]\+"' "$OUT/failure.xml" | head -80 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  echo "Crashes from the app:"
  grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 || true
  exit 1
}
dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1 || true; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
tap() { # tap <dump name> <text>
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
wait_for() { # wait_for <dump name> <grep -E pattern> <seconds> <what>
  local end=$((SECONDS + $3))
  while [ $SECONDS -lt $end ]; do
    dump "$1"
    [ -f "$OUT/$1.xml" ] && grep -Eq "$2" "$OUT/$1.xml" && return 0
    sleep 2
  done
  fail "$4 (waited $3 s)"
}

adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

echo "--- Home"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
wait_for home 'text="Public Provident Fund"' 30 "Home doesn't list the schemes"
grep -q 'text="Government rates for 1 Oct – 31 Dec 2026"' "$OUT/home.xml" || fail "Home doesn't say which quarter's rates it uses"
shot 01-home
echo "PASS: Home"

echo "--- PPF"
tap home "PPF"
wait_for ppf 'text="₹40,68,209"' 15 "PPF doesn't show ₹40,68,209 for ₹1.5 lakh a year for 15 years"
shot 02-ppf
echo "PASS: PPF default"

echo "--- Typing a new deposit"
tap ppf "150000"
adb shell input keyevent KEYCODE_MOVE_END
for _ in 1 2 3 4 5 6; do adb shell input keyevent KEYCODE_DEL; done
adb shell input text 50000
wait_for ppf-typed 'text="₹13,56,070"' 15 "₹50,000 a year doesn't give ₹13,56,070"
shot 03-ppf-typed
echo "PASS: typing"

adb shell input keyevent KEYCODE_BACK   # hides the keyboard
sleep 1
dump ppf-back
if grep -q 'text="₹13,56,070"' "$OUT/ppf-back.xml"; then adb shell input keyevent KEYCODE_BACK; fi
wait_for home2 'text="Public Provident Fund"' 15 "Back doesn't return to the list"

echo "--- SCSS"
tap home2 "SCSS"
wait_for scss 'text="₹30,750 every quarter"' 15 "SCSS doesn't pay ₹30,750 a quarter on ₹15 lakh"
shot 04-scss
echo "PASS: SCSS"

adb shell input keyevent KEYCODE_BACK
wait_for home3 'text="Public Provident Fund"' 15 "Back doesn't return to the list"

echo "--- Loans"
tap home3 "Loans"
wait_for loans 'text="Home loan"' 15 "the Loans tab doesn't list the loans"
shot 05-loans
tap loans "Home loan"
wait_for home-loan 'text="₹43,391"' 15 "a ₹50 lakh home loan at 8.5% for 20 years doesn't show an EMI of ₹43,391"
shot 06-home-loan
echo "PASS: EMI"

echo "--- Part payment"
swipe_up() { adb shell input swipe 540 1800 540 800 600; sleep 1; }
swipe_up
dump part
tap part "Part payment (₹)"
adb shell input keyevent KEYCODE_MOVE_END
adb shell input keyevent KEYCODE_DEL
adb shell input text 100000
sleep 1
adb shell input keyevent KEYCODE_BACK   # hides the keyboard
sleep 1
swipe_up
wait_for saved 'text="11 months sooner"' 15 "₹1 lakh paid after the first EMI doesn't end the loan 11 months sooner"
grep -q 'text="₹4,19,612"' "$OUT/saved.xml" || fail "the interest saved isn't ₹4,19,612"
shot 07-part-payment
echo "PASS: part payment, shorter loan"

adb shell input swipe 540 900 540 1400 600
sleep 1
dump choice
tap choice "Lower EMI"
sleep 1
swipe_up
wait_for lower 'text="₹42,522"' 15 "the lower EMI isn't ₹42,522"
shot 08-lower-emi
echo "PASS: part payment, lower EMI"

adb logcat -d | grep -q "FATAL EXCEPTION" && fail "the app crashed"
echo "Smoke test passed."
