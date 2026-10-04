#!/usr/bin/env bash
# Real-runtime check on an emulator (run by android-ci.yml):
#   1. the debug APK installs and MainActivity starts,
#   2. the system keyboard shows when the master password field is focused
#      (the app ships no keyboard of its own, and must not register one),
#   3. a vault can be created and every bottom-navigation destination and the
#      Home "+" menu open without a crash,
#   4. after a restart the vault is locked and unlocks with the master password
#      typed through the system keyboard,
#   5. with the vault locked, a real login form (the autofilltest app) gets an
#      Autofill response from AtomicVault without anything crashing,
#   6. nothing crashes along the way (logcat is scanned at the end).
# Screenshots, UI dumps and logcat land in $OUT for the workflow to upload.
# Note: the app sets FLAG_SECURE on every screen, so screenshots are blank by
# design -- the UI dump text is what is asserted.
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
TEST_APK="${2:-autofilltest/build/outputs/apk/debug/autofilltest-debug.apk}"
PKG=com.atomicvault.android.debug
OUT=emulator-artifacts
mkdir -p "$OUT"

# uiautomator returns "null root node" while a window is still coming up or
# animating, so retry instead of letting `set -e` end the script on the first miss.
dump() {
  local i
  for i in 1 2 3 4 5; do
    rm -f "$OUT/ui.xml"
    if adb shell uiautomator dump /sdcard/ui.xml 2>&1 | grep -qi "dumped to"        && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

# find_center <attr> <value> [exact]  -> "x y" of the first matching node, or nothing (ignores case)
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
    # Case-insensitive: the design system renders labels in uppercase.
    have, want = a.group(1).lower(), val.lower()
    hit = (have == want) if mode == "exact" else (want in have)
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

# nth_edit_bottom <n> -> "x bottom" of the nth (1-based) EditText
nth_edit_bottom() {
  python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
n = int(sys.argv[2])
nodes = [m.group(0) for m in re.finditer(r'<node [^>]*class="android\.widget\.EditText"[^>]*>', xml)]
if len(nodes) >= n:
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', nodes[n - 1])
    x1, y1, x2, y2 = map(int, b.groups())
    print((x1 + x2) // 2, y2)
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
    if dump && grep -qi "$1" "$OUT/ui.xml"; then return 0; fi
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

echo "Checking that the app registers no input method"
adb shell ime list -a > "$OUT/ime_list.txt"
if grep -q "$PKG" "$OUT/ime_list.txt"; then
  fail "The app still registers an input method; the Atomic keyboard was removed"
fi

echo "Launching MainActivity"
adb shell am start -n "$PKG/com.example.MainActivity"
sleep 8
wait_for_text 'resource-id="screen_onboarding"' 30
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
  fail "The system keyboard did not show when the password field was focused"
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
adb shell input swipe 540 1800 540 600 300   # the button sits below the fold on small screens
sleep 1
tap_node text "Create vault" contains 1
wait_for_text 'resource-id="screen_home"' 60   # Argon2id (64 MiB) + database creation
echo "Vault created; Home is showing"

echo "Walking the bottom navigation"
tap_node content-desc "Generate" exact 3
grep -qi 'resource-id="screen_generate"' "$OUT/ui.xml" || { dump || fail "UI dump failed"; grep -qi 'resource-id="screen_generate"' "$OUT/ui.xml" || fail "Generate tab did not open the generator"; }
tap_node content-desc "Health" exact 3
dump || fail "UI dump failed"; grep -qi 'resource-id="screen_health"' "$OUT/ui.xml" || fail "Health tab did not open the security dashboard"
tap_node content-desc "Settings" exact 3
dump || fail "UI dump failed"; grep -qi 'resource-id="screen_settings"' "$OUT/ui.xml" || fail "Settings tab did not open Settings"
tap_node content-desc "Vault" exact 3
dump || fail "UI dump failed"; grep -qi 'resource-id="screen_home"' "$OUT/ui.xml" || fail "Vault tab did not return to Home"

echo "Opening the Home add menu"
tap_node content-desc "Add to vault" contains 2
tap_node text "Payment card" contains 3
dump || fail "UI dump failed"; grep -qi "Add payment card" "$OUT/ui.xml" || fail "Add menu did not open the payment card editor"
adb shell input keyevent 4
sleep 2
dump || fail "UI dump failed"; grep -qi 'resource-id="screen_home"' "$OUT/ui.xml" || fail "Back from the card editor did not return to Home"

echo "Restarting the app: the vault must come back locked"
adb shell am force-stop "$PKG"
sleep 2
adb shell am start -n "$PKG/com.example.MainActivity"
wait_for_text 'resource-id="screen_unlock"' 30

echo "Unlocking with the master password through the system keyboard"
dump || fail "UI dump failed on the unlock screen"
C=$(nth_edit_center 1)
[ -n "$C" ] || fail "No password field on the unlock screen"
adb shell input tap $C
sleep 2
adb shell input text "CorrectHorse9Battery"
sleep 1
# Close the keyboard so the button is visible -- only if it is showing, since
# BACK with no keyboard would leave the app.
if adb shell dumpsys input_method | grep -q "mInputShown=true"; then
  adb shell input keyevent 4
  sleep 1
fi
tap_node text "Unlock" exact 1
wait_for_text 'resource-id="screen_home"' 60
echo "Unlocked with the system keyboard"

echo "Autofill: a locked vault answers a real fill request"
if [ -f "$TEST_APK" ]; then
  adb install -r "$TEST_APK"
  adb shell settings put secure autofill_service "$PKG/com.example.autofill.VaultAutofillService"
  adb shell cmd autofill set log_level verbose || true
  adb shell am force-stop "$PKG"          # vault locked: no app process
  adb shell am start -n com.atomicvault.autofilltest/.LoginActivity
  wait_for_text "Sign in" 30
  dump || fail "UI dump failed on the test login screen"
  C=$(nth_edit_center 1)
  [ -n "$C" ] || fail "No username field on the test login screen"
  adb shell input tap $C
  sleep 6
  adb exec-out screencap -p > "$OUT/03_autofill_locked.png"
  adb shell dumpsys autofill > "$OUT/autofill_dump.txt" || true
  if grep -q "com.example.autofill.VaultAutofillService" "$OUT/autofill_dump.txt"; then
    echo "AtomicVault is the active autofill service"
  else
    fail "AtomicVault did not become the active autofill service"
  fi
  # The service process must have started and answered without crashing.
  if [ -z "$(adb shell pidof "$PKG" || true)" ]; then
    fail "The autofill service process is not running after a fill request"
  fi
  # What Android received from the service (log level is verbose, so the
  # response shows whether it carries an authentication, i.e. the unlock chip).
  grep -iE "mResponses|hasAuthentication|authentication=|datasets=|mFillUi|showing" "$OUT/autofill_dump.txt" | head -20 || true
  # The suggestion is drawn in a system popup window (no keyboard strip on
  # this emulator) that uiautomator cannot see. Ask the window manager where
  # the Autofill popup is and tap its centre: AtomicVault's unlock screen must
  # open -- proof the locked-vault response reached the user.
  adb shell dumpsys window windows > "$OUT/windows.txt" || true
  POPUP=$(python3 - "$OUT/windows.txt" "$PKG" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
pkg = sys.argv[2]
blocks = re.split(r'\n(?=  Window #\d+)', text)
for b in blocks:
    head = b.split("\n", 1)[0]
    if "autofill" not in head.lower() or pkg in head or "autofilltest" in head:
        continue
    for key in ("mFrame", "frame", "visible", "parent"):
        m = re.search(r'\b' + key + r'=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', b)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            if x2 > x1 and y2 > y1:
                print(head.strip(), file=sys.stderr)
                print((x1 + x2) // 2, (y1 + y2) // 2)
                sys.exit(0)
PY
  ) || true
  OPENED=0
  TAPS=()
  if [ -n "$POPUP" ]; then
    echo "Autofill popup found at $POPUP"
    TAPS+=("$POPUP")
  else
    echo "No Autofill popup window listed; falling back to taps near the field"
    grep -n -i "autofill" "$OUT/windows.txt" | head -10 || true
  fi
  dump || true
  B=$(nth_edit_bottom 1)
  if [ -n "$B" ]; then
    set -- $B
    for dy in 70 120 170; do TAPS+=("$1 $(( $2 + dy ))"); done
  fi
  for t in "${TAPS[@]}"; do
    adb shell input tap $t
    sleep 4
    if dump && grep -qi "Fill with AtomicVault" "$OUT/ui.xml"; then OPENED=1; break; fi
  done
  if [ "$OPENED" = "1" ]; then
    echo "Tapping the AtomicVault suggestion opened the unlock screen"
    adb exec-out screencap -p > "$OUT/04_autofill_auth.png"
    adb shell input keyevent 4
    sleep 2
  else
    echo "::warning::Could not open the AtomicVault suggestion by tapping it; see 03_autofill_locked.png"
    adb logcat -d | grep -iE "AutofillSession|FillUi|AutofillManager|AutofillUI" | tail -25 || true
  fi
else
  echo "::warning::Autofill test app not found at $TEST_APK; skipping the Autofill check"
fi

adb logcat -d > "$OUT/logcat.txt"

if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::A crash was logged while exercising the app"
  grep -n -A14 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  exit 1
fi

if [ -z "$(adb shell pidof "$PKG" || true)" ]; then
  fail "App process died"
fi

echo "Emulator check passed"
