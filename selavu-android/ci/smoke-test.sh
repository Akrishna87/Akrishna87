#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK and checks that you can log an expense
# and an income, set up a repeating one, set a budget and see what's left, read the reports,
# delete an entry, keep everything after the app is closed, and use the launcher shortcut.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.selavu
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o '\(text\|content-desc\)="[^"]\+"' "$OUT/failure.xml" | head -60 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  grep -E "AndroidRuntime|FATAL|Selavu" "$OUT/logcat.txt" | tail -40 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3 4 5 6; do
    rm -f "$OUT/$1.xml"
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
has() { grep -qF "$2" "$OUT/$1.xml"; }
expect() { has "$1" "$2" || fail "$3"; }
hide_keyboard() { if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi; }

adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

echo "--- Launching the app"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5
dump home
shot 1-empty
expect home 'text="Balance"' "the home screen didn't open"
expect home 'text="Nothing logged yet"' "a fresh install doesn't say nothing is logged yet"
echo "PASS: opens on the home screen"

echo "--- Adding an expense"
tap home "Expense"
sleep 2
dump add1
expect add1 'text="New expense"' "the Expense button didn't open a new expense"
adb shell input text 250
hide_keyboard
dump add1
tap add1 "Groceries"
sleep 1
dump add1
shot 2-new-expense
tap add1 "Save"
sleep 2
dump home
expect home 'text="−₹250"' "the new expense isn't in the list"
expect home 'text="Groceries"' "the expense's category isn't shown"
echo "PASS: logs an expense"

echo "--- Adding an income"
tap home "Income" last # the button, below the "Income" total
sleep 2
dump add2
expect add2 'text="New income"' "the Income button didn't open a new income"
adb shell input text 50000
hide_keyboard
dump add2
tap add2 "Save"
sleep 2
dump home
expect home 'text="+₹50,000"' "the income isn't in the list"
expect home 'text="₹49,750"' "the balance isn't income minus spending"
echo "PASS: logs an income and the balance adds up"

echo "--- A repeating expense"
tap home "Expense"
sleep 2
adb shell input text 999
hide_keyboard
dump add3
tap add3 "Entertainment"
tap add3 "Note (optional)"
sleep 1
adb shell input text Netflix
hide_keyboard
dump add3
tap add3 "Every month"
sleep 1
dump add3
expect add3 "logged every month on its own" "choosing Every month doesn't say it will repeat"
shot 3-repeating
tap add3 "Save"
sleep 2
dump home
expect home 'content-desc="Repeats"' "the repeating expense isn't marked as repeating"
expect home 'text="Netflix"' "the note isn't shown"
echo "PASS: sets up a repeating expense"

echo "--- Budget"
tap home "Settings"
sleep 2
dump settings
tap settings "Spending budget"
sleep 1
dump budget
tap budget "Budget amount"
sleep 1
adb shell input text 20000
hide_keyboard
dump budget
tap budget "Save" last
sleep 1
dump settings
expect settings "a month" "the budget wasn't saved"
expect settings "1 active" "the repeating entry isn't listed in Settings"
tap settings "Home"
sleep 2
dump home
shot 4-home-with-budget
expect home 'text="₹18,751 left"' "the budget card doesn't show what's left"
expect home "a day for the next" "the budget card doesn't say how much can be spent a day"
echo "PASS: budget shows what's left"

echo "--- Reports"
tap home "Reports"
sleep 2
dump reports
shot 5-reports
expect reports 'text="By category"' "Reports didn't open"
expect reports 'text="₹1,249"' "Reports doesn't total the spending"
expect reports 'text="Entertainment"' "Reports doesn't list the categories"
expect reports 'text="80%"' "Reports doesn't show each category's share"
tap reports "Entertainment"
sleep 2
dump detail
expect detail 'text="1 entry"' "a category's page doesn't list its entries"
adb shell input keyevent KEYCODE_BACK
sleep 1
for p in Week Year Day; do
  dump reports
  tap reports "$p"
  sleep 1
done
dump reports
shot 6-reports-day
echo "PASS: reports by category and period"

echo "--- Deleting an entry"
dump reports
tap reports "Home"
sleep 2
dump home
tap home "Groceries"
sleep 2
dump edit
expect edit 'text="Edit expense"' "tapping an entry didn't open it"
tap edit "Delete"
sleep 1
dump confirm
tap confirm "Delete" last
sleep 2
dump home
has home 'text="−₹250"' && fail "the deleted expense is still listed"
echo "PASS: deletes an entry"

echo "--- Still there after closing the app"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity"
sleep 4
dump home
expect home 'text="+₹50,000"' "entries were lost when the app was closed"
expect home 'text="Netflix"' "the repeating entry was lost when the app was closed"
echo "PASS: keeps everything"

echo "--- Launcher shortcut"
adb shell am start -W -a io.github.akrishna87.selavu.ADD_EXPENSE -n "$PKG/.MainActivity"
sleep 3
dump shortcut
expect shortcut 'text="New expense"' "the Add expense shortcut didn't open a new expense"
hide_keyboard
shot 7-shortcut
echo "PASS: Add expense shortcut"

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
