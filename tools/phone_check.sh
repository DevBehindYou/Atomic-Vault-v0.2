#!/usr/bin/env bash
# Runs the automated part of the phone test on YOUR OWN Android phone, from a
# computer with adb, gh (logged in) and python3. It reuses CI's emulator check
# (.github/scripts/emulator_check.sh), which on the phone:
#   - installs the latest green CI debug build (com.atomicvault.android.debug,
#     which sits beside the real app and never touches its data) and the small
#     autofilltest login app,
#   - creates a test vault, opens every tab and the "+" menu, restarts, unlocks
#     with the master password, and checks Autofill answers a real login form,
#   - fails if anything crashes.
#
# What it changes, and puts back afterwards: animation speeds, "show the
# keyboard with a hardware keyboard", the chosen Autofill service and the
# Autofill log level. It never touches the screen lock or fingerprints (CI's
# fingerprint and screen-lock checks set a lock-screen PIN, so they are NOT
# run here) and never touches the real AtomicVault app.
#
#   usage: tools/phone_check.sh [run-id]     (default: latest green CI run)
# Keep the phone unlocked, on, and on the home screen while it runs (~3 min).
set -euo pipefail

REPO=DevBehindYou/Atomic-Vault-v0.2
BRANCH=claude/stoic-lamport-t601ql
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/phone-check"
mkdir -p "$WORK"
cd "$WORK"

for tool in adb gh python3; do
  command -v "$tool" >/dev/null || { echo "Needs $tool on this computer."; exit 1; }
done

devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
count=$(printf '%s\n' "$devices" | grep -c . || true)
[ "$count" -eq 1 ] || { echo "Connect exactly one phone with USB debugging allowed (found $count)."; adb devices; exit 1; }
echo "Phone: $(adb shell getprop ro.product.model | tr -d '\r') (Android $(adb shell getprop ro.build.version.release | tr -d '\r'))"

RUN="${1:-$(gh run list -R "$REPO" --branch "$BRANCH" --event push --status success --limit 1 --json databaseId -q '.[0].databaseId')}"
echo "Using CI run $RUN"
rm -rf apk testapk
gh run download "$RUN" -R "$REPO" --pattern 'AtomicVault-Debug-APK-*' --dir apk
gh run download "$RUN" -R "$REPO" --pattern 'AtomicVault-AutofillTest-APK-*' --dir testapk
APK=$(find apk -name 'app-debug.apk' | head -1)
TEST_APK=$(find testapk -name '*.apk' | head -1)
[ -n "$APK" ] && [ -n "$TEST_APK" ] || { echo "Could not find the APKs in run $RUN."; exit 1; }

# Remember the settings the check changes, and put them back on exit.
get() { adb shell settings get "$1" "$2" | tr -d '\r'; }
put_back() {
  local ns key val
  while IFS='|' read -r ns key val; do
    if [ "$val" = "null" ]; then adb shell settings delete "$ns" "$key" >/dev/null 2>&1 || true
    else adb shell settings put "$ns" "$key" "$val" >/dev/null 2>&1 || true; fi
  done < "$WORK/saved-settings.txt"
  adb shell cmd autofill set log_level off >/dev/null 2>&1 || true
  adb uninstall com.atomicvault.autofilltest >/dev/null 2>&1 || true
  echo "Phone settings restored."
}
: > saved-settings.txt
for pair in global:window_animation_scale global:transition_animation_scale global:animator_duration_scale \
            secure:show_ime_with_hard_keyboard secure:autofill_service; do
  ns=${pair%%:*}; key=${pair#*:}
  echo "$ns|$key|$(get "$ns" "$key")" >> saved-settings.txt
done
trap put_back EXIT

# The check expects a fresh install of the TEST build (it creates a new vault).
adb uninstall com.atomicvault.android.debug >/dev/null 2>&1 || true
adb shell input keyevent 224   # wake
adb shell input keyevent 3     # home

PKG=com.atomicvault.android.debug bash "$ROOT/.github/scripts/emulator_check.sh" "$APK" "$TEST_APK"

echo
echo "Automated phone check passed. The test build stays installed as"
echo "\"AtomicVault (debug)\" with master password CorrectHorse9Battery; uninstall it"
echo "when done:  adb uninstall com.atomicvault.android.debug"
echo "Logs and UI dumps: $WORK/emulator-artifacts"
echo "Still by hand (docs/PHONE-TEST-CHECKLIST.md): fingerprint (1), Gboard chips in real"
echo "apps (3), screen-lock unlock (4a), CSV import (4b), history (4c), breach warning (4d),"
echo "auto-lock timings (5), large fonts (6), and how it looks (7)."
