#!/usr/bin/env bash
# Runs the shared tests and the screen smoke test on the emulator the workflow started. It lives outside the
# workflow file so what runs on the device can change without editing the workflow.
set -uo pipefail

adb logcat -c || true
gradle connectedDebugAndroidTest --no-daemon --stacktrace
status=$?

if [ "$status" -ne 0 ]; then
  mkdir -p app/build/device
  adb logcat -d > app/build/device/logcat.txt || true
  echo "---- crashes in logcat ----"
  grep -E -A 40 "FATAL EXCEPTION" app/build/device/logcat.txt | head -150 || true
fi
exit "$status"
