#!/usr/bin/env bash
set -euo pipefail

focus_emulator() {
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  sleep 2
}

focus_emulator
if ! gradle connectedQualityAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.ctuprotebe.vyletnikviz.TripExperienceAuditTest; then
  echo "Human audit failed once; refocusing the cloud emulator and retrying exactly once."
  focus_emulator
  gradle connectedQualityAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.ctuprotebe.vyletnikviz.TripExperienceAuditTest
fi

mkdir -p app/build/human-audit-screens
SCREEN_DIR=$(adb shell 'find /sdcard/Android/data -type d -name test-screens 2>/dev/null | head -1' | tr -d '\r')
if [ -n "$SCREEN_DIR" ]; then
  adb pull "$SCREEN_DIR/." app/build/human-audit-screens/
else
  echo "UX audit passed, but no screenshot directory was produced."
fi
