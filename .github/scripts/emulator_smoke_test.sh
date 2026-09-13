#!/usr/bin/env bash
set -e
echo "Installing debug APK on emulator..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
echo "Launching MainActivity..."
adb shell am start -n com.atomicvault.android.debug/com.example.MainActivity
sleep 5
PID=$(adb shell pidof com.atomicvault.android.debug)
if [ -z "$PID" ]; then
  echo "::error::MainActivity crashed or failed to start!"
  adb logcat -d -t 200
  exit 1
fi
echo "Smoke test passed! MainActivity is alive with PID: $PID"
