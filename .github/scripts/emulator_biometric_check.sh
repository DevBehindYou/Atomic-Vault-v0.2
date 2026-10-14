#!/usr/bin/env bash
# Fingerprint unlock on an emulator (TASKS T2), run by android-ci.yml:
#   1. set a screen-lock PIN and enrol fingerprint 1 through Settings, using
#      the emulator's virtual sensor (`adb emu finger touch`),
#   2. create a vault with fingerprint unlock on and confirm it with finger 1,
#   3. restart: the unlock prompt opens by itself; an unknown finger (2) is
#      rejected and the vault stays locked; finger 1 opens Home,
#   4. restart again and cancel the prompt: the password field stays usable,
#   5. nothing crashes (logcat is scanned at the end).
# The app sets FLAG_SECURE, so screenshots are blank; UI dumps are asserted.
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="${PKG:-com.atomicvault.android.debug}"
PIN=1111
PASSWORD=CorrectHorse9Battery
OUT=emulator-artifacts
mkdir -p "$OUT"

# dump, find_center, nth_edit_center, nth_edit_bottom, fail, tap_node, wait_for_text
source "$(dirname "$0")/emulator_lib.sh"

touch_finger() {  # touch_finger <id>
  adb emu finger touch "$1" >/dev/null
  sleep 1
  adb emu finger remove "$1" >/dev/null 2>&1 || true
  sleep 1
}

enrolled_count() {
  # dumpsys fingerprint prints {"prints":[{"id":0,"count":N,...}]}
  adb shell dumpsys fingerprint 2>/dev/null | grep -oE '"count":[0-9]+' | head -1 | cut -d: -f2
}

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

echo "Setting a screen-lock PIN (fingerprints need one)"
adb shell locksettings set-pin "$PIN"

echo "Enrolling fingerprint 1"
adb shell am start -a android.settings.FINGERPRINT_ENROLL >/dev/null
sleep 3
for step in $(seq 1 60); do
  [ "${COUNT:-0}" -gt 0 ] 2>/dev/null && break
  dump || { sleep 2; continue; }
  if c=$(find_center text "Done" exact) && [ -n "$c" ]; then
    adb shell input tap $c; sleep 2
  elif grep -qiE 'password_entry|text="Confirm your PIN"|Re-enter your PIN|Enter your PIN' "$OUT/ui.xml"; then
    adb shell input text "$PIN"
    adb shell input keyevent 66
    sleep 3
  elif c=$(find_center text "I agree" exact) && [ -n "$c" ]; then
    adb shell input tap $c; sleep 2
  elif c=$(find_center text "Agree" exact) && [ -n "$c" ]; then
    adb shell input tap $c; sleep 2
  elif c=$(find_center text "More" exact) && [ -n "$c" ]; then
    adb shell input tap $c; sleep 2
  elif c=$(find_center text "Next" exact) && [ -n "$c" ]; then
    adb shell input tap $c; sleep 2
  else
    touch_finger 1
  fi
  COUNT=$(enrolled_count || echo 0)
done
adb shell dumpsys fingerprint > "$OUT/fingerprint_dump.txt" || true
COUNT=$(enrolled_count || echo 0)
[ "${COUNT:-0}" -gt 0 ] || fail "Fingerprint enrolment did not finish (see fingerprint_dump.txt and failure_ui.xml)"
echo "Fingerprint enrolled ($COUNT)"
adb shell input keyevent 3   # HOME: leave Settings
sleep 2

echo "Installing $APK"
adb install -r "$APK"
adb logcat -c

echo "Creating a vault with fingerprint unlock on"
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
adb shell input keyevent 4   # close the keyboard
sleep 1
adb shell input swipe 540 1800 540 600 300
sleep 1
dump || fail "UI dump failed before the fingerprint switch"
grep -qi 'resource-id="onboarding_biometric_toggle"' "$OUT/ui.xml" \
  || fail "Onboarding shows no fingerprint switch although a fingerprint is enrolled"
grep -qi 'resource-id="onboarding_biometric_toggle"[^>]*checked="true"' "$OUT/ui.xml" \
  || tap_node resource-id "onboarding_biometric_toggle" exact 1
tap_node text "Create vault" contains 1

echo "Confirming fingerprint unlock with finger 1"
wait_for_text 'Enable biometric unlock' 60   # Argon2id runs first
touch_finger 1
wait_for_text 'resource-id="screen_home"' 30
echo "Vault created with fingerprint unlock armed"

echo "Restart: an unknown finger must not unlock"
adb shell am force-stop "$PKG"; sleep 2
adb shell am start -n "$PKG/com.example.MainActivity"
wait_for_text 'Unlock AtomicVault' 30
touch_finger 2
sleep 2
dump || fail "UI dump failed after the wrong finger"
if grep -qi 'resource-id="screen_home"' "$OUT/ui.xml"; then
  fail "An unenrolled finger unlocked the vault"
fi
echo "Unknown finger rejected"

echo "Finger 1 unlocks"
touch_finger 1
wait_for_text 'resource-id="screen_home"' 30
echo "Unlocked with the fingerprint"

echo "Restart and cancel the prompt: the password field must stay usable"
adb shell am force-stop "$PKG"; sleep 2
adb shell am start -n "$PKG/com.example.MainActivity"
wait_for_text 'Unlock AtomicVault' 30
adb shell input keyevent 4   # BACK cancels the prompt
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
  echo "::error::A crash was logged during the fingerprint check"
  grep -n -A14 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  exit 1
fi
echo "Fingerprint check passed"
