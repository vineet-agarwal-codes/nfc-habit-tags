#!/usr/bin/env bash
# Runs on the CI emulator: installs the debug build, grants the permissions a user
# would grant by hand, then runs instrumented tests and Maestro flows.
set -uo pipefail
cd "$(dirname "$0")/.."
OUT=build/e2e
mkdir -p "$OUT"
PKG=com.habittags.app
status=0

adb logcat -c
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

grant() {
  adb shell appops set $PKG GET_USAGE_STATS allow
  adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
  adb shell cmd notification allow_dnd $PKG
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
}
grant

adb shell am instrument -w -r $PKG.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrumented.txt" 2>&1
cat "$OUT/instrumented.txt"
grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED\|Process crashed" "$OUT/instrumented.txt" && status=1
grep -q "OK (" "$OUT/instrumented.txt" || status=1

# instrumentation may have cleared state; re-grant before flows
grant
for flow in maestro/*.yaml; do
  name=$(basename "$flow" .yaml)
  if ! maestro test --format junit --output "$OUT/$name.xml" --debug-output "$OUT/$name" "$flow"; then
    echo "FLOW FAILED: $name"
    status=1
  fi
done

adb logcat -d > "$OUT/logcat.txt"
exit $status
