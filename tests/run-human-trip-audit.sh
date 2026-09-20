#!/usr/bin/env bash
set -euo pipefail

gradle connectedQualityAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.ctuprotebe.vyletnikviz.TripExperienceAuditTest

mkdir -p app/build/human-audit-screens
SCREEN_DIR=$(adb shell 'find /sdcard/Android/data -type d -name test-screens 2>/dev/null | head -1' | tr -d '\r')
if [ -n "$SCREEN_DIR" ]; then
  adb pull "$SCREEN_DIR/." app/build/human-audit-screens/
else
  echo "UX test passed, but no screenshot directory was produced."
fi
