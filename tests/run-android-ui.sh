#!/usr/bin/env bash
set +e
gradle connectedQualityAndroidTest
quiz_test_status=$?
mkdir -p app/build/ui-screenshots
SCREEN_DIR=$(adb shell 'find /sdcard/Android/data -type d -name test-screens 2>/dev/null | head -1' | tr -d '\r')
if [ -n "$SCREEN_DIR" ]; then
  adb pull "$SCREEN_DIR/." app/build/ui-screenshots/
else
  echo "No emulator screenshots directory found"
fi
if [ "$quiz_test_status" -ne 0 ]; then
  adb logcat -d > app/build/emulator-logcat.txt
fi
exit "$quiz_test_status"
