#!/usr/bin/env bash
# Runs the shared tests and the screen smoke test on the emulator the workflow started. It lives outside the
# workflow file so what runs on the device can change without editing the workflow.
set -uo pipefail

adb logcat -c || true
# The app stays installed after the run, so the widget pictures can be copied out of it below.
gradle :app:connectedDebugAndroidTest --no-daemon --stacktrace -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
status=$?

# Pictures of the month widget drawn by WidgetShotsTest, kept as a build artifact to look at.
mkdir -p app/build/widget-shots
adb exec-out run-as app.omnitask tar -c -C files widget-shots 2>/dev/null | tar -x -C app/build/ 2>/dev/null || true
ls app/build/widget-shots || true

if [ "$status" -ne 0 ]; then
  mkdir -p app/build/device
  adb logcat -d > app/build/device/logcat.txt || true
  echo "---- crashes in logcat ----"
  grep -E -A 40 "FATAL EXCEPTION" app/build/device/logcat.txt | head -150 || true
fi
exit "$status"
