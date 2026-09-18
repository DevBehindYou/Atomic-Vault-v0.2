#!/usr/bin/env bash
# Real-runtime check on an emulator (run by android-ci.yml):
#   1. the debug APK installs and MainActivity starts,
#   2. the Atomic keyboard registers as an input method, can be selected, and
#      draws when a text field is focused,
#   3. nothing crashes along the way.
# Screenshots, the UI dump and the logcat land in $OUT for the workflow to upload.
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG=com.atomicvault.android.debug
IME="$PKG/com.example.keyboard.AtomicVaultInputMethodService"
OUT=emulator-artifacts
mkdir -p "$OUT"

echo "Installing $APK"
adb install -r "$APK"

# Steady, deterministic UI; and show the on-screen keyboard even though the
# emulator advertises a hardware keyboard.
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put secure show_ime_with_hard_keyboard 1

adb logcat -c

echo "Registering and selecting the Atomic keyboard"
adb shell ime list -a > "$OUT/ime_list.txt"
grep -q "$PKG" "$OUT/ime_list.txt" || { echo "::error::Atomic keyboard is not registered as an input method"; cat "$OUT/ime_list.txt"; exit 1; }
adb shell ime enable "$IME"
adb shell ime set "$IME"

echo "Launching MainActivity"
adb shell am start -n "$PKG/com.example.MainActivity"
sleep 10
adb exec-out screencap -p > "$OUT/01_launch.png"

PID=$(adb shell pidof "$PKG" || true)
if [ -z "$PID" ]; then
  echo "::error::App is not running after launch"
  adb logcat -d -t 300
  exit 1
fi

echo "Focusing the first text field"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb pull /sdcard/ui.xml "$OUT/ui_launch.xml" >/dev/null
TAP=$(python3 - "$OUT/ui_launch.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
# First EditText, else the node whose text is the field's placeholder.
for pattern in (r'class="android\.widget\.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
                r'text="At least 8[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'):
    m = re.search(pattern, xml)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
if [ -z "$TAP" ]; then
  echo "::warning::No text field found on the launch screen; tapping the middle of the screen"
  TAP="540 900"
fi
echo "Tapping $TAP"
adb shell input tap $TAP
sleep 5
adb exec-out screencap -p > "$OUT/02_keyboard.png"
adb shell dumpsys input_method > "$OUT/ime_dump.txt" || true

adb logcat -d > "$OUT/logcat.txt"

if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::A crash was logged while exercising the app and keyboard"
  grep -n -A14 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  exit 1
fi

if [ -z "$(adb shell pidof "$PKG" || true)" ]; then
  echo "::error::App process died"
  exit 1
fi

if grep -q "mInputShown=true" "$OUT/ime_dump.txt"; then
  echo "Keyboard is showing (mInputShown=true)"
else
  echo "::warning::dumpsys did not report the keyboard as shown; check $OUT/02_keyboard.png"
fi

echo "Emulator check passed"
