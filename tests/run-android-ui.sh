#!/usr/bin/env bash
set +e
gradle connectedQualityAndroidTest
quiz_test_status=$?
mkdir -p app/build/ui-screenshots
adb pull /sdcard/Android/data/cz.ctuprotebe.vyletnikviz.quality/files/test-screens/. app/build/ui-screenshots/
if [ "$quiz_test_status" -ne 0 ]; then
  adb logcat -d > app/build/emulator-logcat.txt
fi
exit "$quiz_test_status"
