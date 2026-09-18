#!/usr/bin/env bash
# Real-runtime check on an emulator (run by android-ci.yml):
#   1. the debug APK installs and MainActivity starts,
#   2. the Atomic keyboard registers as an input method, can be selected, and
#      draws when a text field is focused,
#   3. a vault can be created and every bottom-navigation destination and the
#      Home "+" menu open without a crash,
#   4. nothing crashes along the way (logcat is scanned at the end).
# Screenshots, UI dumps and logcat land in $OUT for the workflow to upload.
# Note: once a vault exists the app sets FLAG_SECURE, so screenshots of those
# screens are blank by design -- the UI dump text is what is asserted there.
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG=com.atomicvault.android.debug
IME="$PKG/com.example.keyboard.AtomicVaultInputMethodService"
OUT=emulator-artifacts
mkdir -p "$OUT"

# uiautomator returns "null root node" while a window is still coming up or
# animating, so retry instead of letting `set -e` end the script on the first miss.
dump() {
  local i
  for i in 1 2 3 4 5; do
    rm -f "$OUT/ui.xml"
    if adb shell uiautomator dump /sdcard/ui.xml 2>&1 | grep -q "dumped to"        && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# find_center <attr> <value> [exact]  -> "x y" of the first matching node, or nothing
find_center() {
  python3 - "$OUT/ui.xml" "$1" "$2" "${3:-contains}" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
attr, val, mode = sys.argv[2], sys.argv[3], sys.argv[4]
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    a = re.search(r'\b' + attr + r'="([^"]*)"', node)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if not (a and b):
        continue
    hit = (a.group(1) == val) if mode == "exact" else (val in a.group(1))
    if hit:
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}

# nth_edit_center <n> -> center of the nth (1-based) EditText
nth_edit_center() {
  python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
n = int(sys.argv[2])
nodes = [m.group(0) for m in re.finditer(r'<node [^>]*class="android\.widget\.EditText"[^>]*>', xml)]
if len(nodes) >= n:
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', nodes[n - 1])
    x1, y1, x2, y2 = map(int, b.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
}

fail() {
  echo "::error::$1"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  cp "$OUT/ui.xml" "$OUT/failure_ui.xml" 2>/dev/null || true
  adb logcat -d > "$OUT/logcat.txt" || true
  exit 1
}

# tap_node <attr> <value> [exact] [seconds-to-wait-after]
tap_node() {
  dump || fail "UI dump failed before tapping $1=\"$2\""
  local c
  c=$(find_center "$1" "$2" "${3:-contains}")
  [ -n "$c" ] || fail "Could not find $1=\"$2\" on screen"
  echo "Tap $1=\"$2\" at $c"
  adb shell input tap $c
  sleep "${4:-2}"
}

# wait_for_text <substring> [timeout-seconds]
wait_for_text() {
  local deadline=$((SECONDS + ${2:-20}))
  while [ $SECONDS -lt $deadline ]; do
    if dump && grep -q "$1" "$OUT/ui.xml"; then return 0; fi
    sleep 2
  done
  fail "Timed out waiting for \"$1\""
}

echo "Installing $APK"
adb install -r "$APK"

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put secure show_ime_with_hard_keyboard 1

adb logcat -c

echo "Registering and selecting the Atomic keyboard"
adb shell ime list -a > "$OUT/ime_list.txt"
grep -q "$PKG" "$OUT/ime_list.txt" || fail "Atomic keyboard is not registered as an input method"
adb shell ime enable "$IME"
adb shell ime set "$IME"

echo "Launching MainActivity"
adb shell am start -n "$PKG/com.example.MainActivity"
sleep 8
wait_for_text "Create your vault" 30
adb exec-out screencap -p > "$OUT/01_launch.png"

echo "Focusing the master password field"
dump || fail "UI dump failed on the onboarding screen"
C=$(nth_edit_center 1)
[ -n "$C" ] || fail "No text field on the onboarding screen"
adb shell input tap $C
sleep 5
adb exec-out screencap -p > "$OUT/02_keyboard.png"
adb shell dumpsys input_method > "$OUT/ime_dump.txt" || true
if grep -q "mInputShown=true" "$OUT/ime_dump.txt"; then
  echo "Keyboard is showing (mInputShown=true)"
else
  fail "The Atomic keyboard did not show when a field was focused"
fi

echo "Creating a vault"
adb shell input text "CorrectHorse9Battery"
sleep 1
dump || fail "UI dump failed after typing the password"
C=$(nth_edit_center 2)
[ -n "$C" ] || fail "No confirm field"
adb shell input tap $C
sleep 1
adb shell input text "CorrectHorse9Battery"
sleep 1
adb shell input keyevent 4   # BACK closes the keyboard
sleep 1
tap_node text "Create vault" contains 1
wait_for_text "Encrypted on this device" 60   # Argon2id (64 MiB) + database creation
echo "Vault created; Home is showing"

echo "Walking the bottom navigation"
tap_node text "Generate" exact 3
grep -q "Password generator" "$OUT/ui.xml" || { dump || fail "UI dump failed"; grep -q "Password generator" "$OUT/ui.xml" || fail "Generate tab did not open the generator"; }
tap_node text "Audit" exact 3
dump || fail "UI dump failed"; grep -q "Health score\|HEALTH SCORE\|Device integrity" "$OUT/ui.xml" || fail "Audit tab did not open the security dashboard"
tap_node text "Settings" exact 3
dump || fail "UI dump failed"; grep -q "Lock after leaving the app" "$OUT/ui.xml" || fail "Settings tab did not open Settings"
tap_node text "Vault" exact 3
dump || fail "UI dump failed"; grep -q "Encrypted on this device" "$OUT/ui.xml" || fail "Vault tab did not return to Home"

echo "Opening the Home add menu"
tap_node content-desc "Add to vault" contains 2
tap_node text "Payment card" contains 3
dump || fail "UI dump failed"; grep -q "Add payment card" "$OUT/ui.xml" || fail "Add menu did not open the payment card editor"
adb shell input keyevent 4
sleep 2
dump || fail "UI dump failed"; grep -q "Encrypted on this device" "$OUT/ui.xml" || fail "Back from the card editor did not return to Home"

adb logcat -d > "$OUT/logcat.txt"

if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::A crash was logged while exercising the app and keyboard"
  grep -n -A14 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  exit 1
fi

if [ -z "$(adb shell pidof "$PKG" || true)" ]; then
  fail "App process died"
fi

echo "Emulator check passed"
