#!/usr/bin/env bash
# Runs inside the emulator job (.github/workflows/emulator.yml).
# 1) The on-device tests (app/src/androidTest) against the debug app.
# 2) A smoke test of the real signed release APK: install, start, no crash.
# Screenshots and logs go to ./emulator-results for upload.
set -u
API="$1"
PKG=io.github.kiraherk.smarttodo
OUT=emulator-results
mkdir -p "$OUT"
status=0

adb logcat -c || true
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r "$PKG.debug.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrument-api$API.txt"
if ! grep -q "^OK (" "$OUT/instrument-api$API.txt"; then
  echo "::error::On-device tests failed on Android API $API"
  grep -E "^(There w|[0-9]+\) |INSTRUMENTATION_|FAILURES)" "$OUT/instrument-api$API.txt" | head -40
  status=1
fi

# Screenshots taken by the tests (stored inside the debug app)
for f in $(adb shell run-as "$PKG.debug" ls files/screenshots 2>/dev/null | tr -d '\r'); do
  adb exec-out run-as "$PKG.debug" cat "files/screenshots/$f" > "$OUT/$f"
done

RELEASE=app/build/outputs/apk/release/app-release.apk
if [ -f "$RELEASE" ]; then
  adb logcat -c || true
  adb install -r "$RELEASE" || status=1
  adb shell am start -W -n "$PKG/.MainActivity"
  sleep 12
  adb exec-out screencap -p > "$OUT/api$API-release-app-start.png"
  if ! adb shell pidof "$PKG" > /dev/null; then
    echo "::error::The release app is not running after start on API $API"
    status=1
  fi
  if adb logcat -d | grep -A25 "FATAL EXCEPTION" | grep -q "$PKG"; then
    echo "::error::The release app crashed on API $API"
    adb logcat -d | grep -A40 "FATAL EXCEPTION"
    status=1
  fi
else
  echo "Release APK not signed (no secrets available), skipping release smoke test"
fi

adb logcat -d > "$OUT/logcat-api$API.txt" || true
exit $status
