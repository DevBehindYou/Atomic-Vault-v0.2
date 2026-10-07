#!/usr/bin/env bash
# Upgrade check (run by android-ci.yml on pull requests): the base branch's
# build creates a vault holding one login; this pull request's build is then
# installed over it (data kept, as a store update would) and must unlock that
# vault with the master password and still show the login. Guards storage,
# key-store and database-library changes against locking people out.
#   usage: emulator_upgrade_check.sh <old.apk> <new.apk>
# Both APKs must be signed with the same key (build them in one job).
set -euo pipefail

OLD_APK="$1"
NEW_APK="$2"
PKG="${PKG:-com.atomicvault.android.debug}"
PASSWORD=CorrectHorse9Battery
PROBE=UpgradeProbe42
OUT=emulator-artifacts
mkdir -p "$OUT"

# dump, find_center, nth_edit_center, nth_edit_bottom, fail, tap_node, wait_for_text
source "$(dirname "$0")/emulator_lib.sh"

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

hide_keyboard() {
  if adb shell dumpsys input_method | grep -q "mInputShown=true"; then
    adb shell input keyevent 4
    sleep 1
  fi
}

echo "Installing the base branch build"
adb install "$OLD_APK"
adb logcat -c

echo "Creating a vault with the base build"
adb shell am start -n "$PKG/com.example.MainActivity"
# The base may be the 0.2.x release on main: same flow, but its screens do
# not expose test tags as resource ids. Recognise it by its heading.
LEGACY=0
deadline=$((SECONDS + 30))
while [ $SECONDS -lt $deadline ]; do
  if dump; then
    if grep -q 'resource-id="screen_onboarding"' "$OUT/ui.xml"; then break; fi
    if grep -q 'text="Create your vault"' "$OUT/ui.xml"; then LEGACY=1; break; fi
  fi
  sleep 2
done
[ "$LEGACY" = 1 ] || wait_for_text 'resource-id="screen_onboarding"' 5

if [ "$LEGACY" = 1 ]; then
  echo "Base build has the 0.2.x screens: create the vault by label, skip the login"
  dump || fail "UI dump failed on the old onboarding screen"
  C=$(nth_edit_center 1); [ -n "$C" ] || fail "No password field (old onboarding)"
  adb shell input tap $C; sleep 2
  adb shell input text "$PASSWORD"; sleep 1
  dump || fail "UI dump failed after typing (old onboarding)"
  C=$(nth_edit_center 2); [ -n "$C" ] || fail "No confirm field (old onboarding)"
  adb shell input tap $C; sleep 1
  adb shell input text "$PASSWORD"; sleep 1
  adb shell input keyevent 4
  sleep 1
  adb shell input swipe 540 1800 540 600 300
  sleep 1
  tap_node text "Create vault" exact 1
  # Argon2id runs first; with no fingerprint enrolled the old build skips arming.
  wait_for_text 'text="Lock"' 90
  echo "Base build (0.2.x): vault created"
fi

if [ "$LEGACY" = 0 ]; then
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
tap_node text "Create vault" contains 1
wait_for_text 'resource-id="screen_home"' 60

echo "Adding a login with the base build"
tap_node resource-id "fab_add_credential" exact 2
tap_node resource-id "add_login" exact 3
wait_for_text 'resource-id="screen_login_editor"' 15
tap_node resource-id "editor_title_input" exact 1
adb shell input text "$PROBE"
sleep 1
hide_keyboard
tap_node resource-id "editor_save_button" exact 3
wait_for_text 'resource-id="screen_home"' 20
dump || fail "UI dump failed on Home"
grep -q "$PROBE" "$OUT/ui.xml" || fail "The new login is not on Home before the upgrade"
echo "Base build: vault created with one login"
fi

echo "Installing this pull request's build over it (data kept)"
adb shell am force-stop "$PKG"
adb install -r "$NEW_APK"

echo "Unlocking with the new build"
adb shell am start -n "$PKG/com.example.MainActivity"
wait_for_text 'resource-id="screen_unlock"' 45
dump || fail "UI dump failed on the unlock screen"
C=$(nth_edit_center 1); [ -n "$C" ] || fail "No password field on the unlock screen after the upgrade"
adb shell input tap $C; sleep 2
adb shell input text "$PASSWORD"; sleep 1
hide_keyboard
tap_node text "Unlock" exact 1
wait_for_text 'resource-id="screen_home"' 60
dump || fail "UI dump failed on Home after the upgrade"
if [ "$LEGACY" = 0 ]; then
  grep -q "$PROBE" "$OUT/ui.xml" || fail "The login made before the upgrade is missing"
fi
echo "New build: unlocked the old vault$([ "$LEGACY" = 0 ] && echo ' and the login is still there')"

echo "Restart once more: the vault must still open"
adb shell am force-stop "$PKG"; sleep 2
adb shell am start -n "$PKG/com.example.MainActivity"
wait_for_text 'resource-id="screen_unlock"' 30
dump || fail "UI dump failed on the unlock screen"
C=$(nth_edit_center 1); [ -n "$C" ] || fail "No password field"
adb shell input tap $C; sleep 2
adb shell input text "$PASSWORD"; sleep 1
hide_keyboard
tap_node text "Unlock" exact 1
wait_for_text 'resource-id="screen_home"' 60

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::A crash was logged during the upgrade check"
  grep -n -A14 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
  exit 1
fi
echo "Upgrade check passed"
