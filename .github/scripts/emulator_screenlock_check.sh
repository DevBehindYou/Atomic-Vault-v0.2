#!/usr/bin/env bash
# Screen-lock unlock on an emulator with no fingerprint (Android 11+), run by
# android-ci.yml:
#   1. set a screen-lock PIN and no fingerprint,
#   2. create a vault with "Screen-lock unlock" on and confirm it with the PIN,
#   3. restart: the prompt opens by itself; the PIN opens Home,
#   4. restart and cancel the prompt: the password field stays usable,
#   5. nothing crashes (logcat is scanned at the end).
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="${PKG:-com.atomicvault.android.debug}"
PIN=1111
PASSWORD=CorrectHorse9Battery
OUT=emulator-artifacts
mkdir -p "$OUT"

# dump, find_center, nth_edit_center, nth_edit_bottom, fail, tap_node, wait_for_text
source "$(dirname "$0")/emulator_lib.sh"

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

enter_pin() {
  # The system's confirm-credential screen takes the PIN from the keyboard.
  sleep 2
  adb shell input text "$PIN"
  adb shell input keyevent 66
  sleep 3
}

echo "Setting a screen-lock PIN (no fingerprint)"
adb shell locksettings set-pin "$PIN"

echo "Installing $APK"
adb install -r "$APK"
adb logcat -c

echo "Creating a vault with screen-lock unlock on"
adb shell am start -n "$PKG/com.example.MainActivity"
wait_for_text 'resource-id="screen_onboarding"' 30
dump || fail "UI dump failed on the onboarding screen"
C=$(nth_edit_center 1); [ -n "$C" ] || fail "No password field"
adb shell input tap $C; sleep 2
adb shell input text "$PASSWORD"; sleep 1
dump || fail "UI dump failed after typing"
C=$(nth_edit_center 2); [ -n "$C" ] || fail "No confirm field"
adb shell input tap $C; sleep 1
adb shell input text "$PASSWORD"; sleep 1
adb shell input keyevent 4
sleep 1
adb shell input swipe 540 1800 540 600 300
sleep 1
dump || fail "UI dump failed before the screen-lock switch"
grep -q 'Screen-lock unlock' "$OUT/ui.xml" || fail "Onboarding does not offer screen-lock unlock on a phone with a PIN and no fingerprint"
grep -qi 'resource-id="onboarding_biometric_toggle"[^>]*checked="true"' "$OUT/ui.xml" \
  || tap_node resource-id "onboarding_biometric_toggle" exact 1
tap_node text "Create vault" contains 1

echo "Confirming with the PIN"
wait_for_text 'Enable screen-lock unlock' 60   # Argon2id runs first
enter_pin
wait_for_text 'resource-id="screen_home"' 30
echo "Vault created with screen-lock unlock armed"

echo "Restart: the PIN unlocks"
adb shell am force-stop "$PKG"; sleep 2
adb shell am start -n "$PKG/com.example.MainActivity"
wait_for_text 'Unlock AtomicVault' 30
enter_pin
wait_for_text 'resource-id="screen_home"' 30
echo "Unlocked with the screen lock"

echo "Restart and cancel the prompt: the password field must stay usable"
adb shell am force-stop "$PKG"; sleep 2
adb shell am start -n "$PKG/com.example.MainActivity"
wait_for_text 'Unlock AtomicVault' 30
adb shell input keyevent 4
sleep 2
wait_for_text 'resource-id="screen_unlock"' 15
dump || fail "UI dump failed after cancelling"
C=$(nth_edit_center 1); [ -n "$C" ] || fail "No password field after cancelling the prompt"
adb shell input tap $C; sleep 2
adb shell input text "$PASSWORD"; sleep 1
if adb shell dumpsys input_method | grep -q "mInputShown=true"; then
  adb shell input keyevent 4; sleep 1
fi
tap_node text "Unlock" exact 1
wait_for_text 'resource-id="screen_home"' 60
echo "Password unlock still works after cancelling the prompt"

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::A crash was logged during the screen-lock check"
  grep -n -A14 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  exit 1
fi
echo "Screen-lock check passed"
