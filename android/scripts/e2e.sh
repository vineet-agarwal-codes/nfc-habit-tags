#!/usr/bin/env bash
# Runs on the CI emulator: installs the debug build, grants the permissions a user
# would grant by hand, then runs instrumented tests and the ordered Maestro flows,
# with adb checks between flows for things Maestro can't do (reboot, DND state).
set -uo pipefail
cd "$(dirname "$0")/.."
OUT=build/e2e
mkdir -p "$OUT"
PKG=com.habittags.app
status=0
fail() { echo "E2E FAILED: $*"; status=1; }

grant() {
  adb shell appops set $PKG GET_USAGE_STATS allow
  adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
  adb shell cmd notification allow_dnd $PKG
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
}

flow() {
  local name=$1
  maestro test --format junit --output "$OUT/$name.xml" --debug-output "$OUT/$name" "maestro/$name.yaml" || fail "flow $name"
  adb logcat -d > "$OUT/$name.logcat.txt"
  adb logcat -c
}

# Counts only if WindowManager reports the overlay on screen (system apps like Settings force-hide overlays).
overlay_visible() {
  adb shell dumpsys window windows > "$OUT/windows.txt"
  grep -A40 "Window{.* HabitTagsBlockOverlay}:" "$OUT/windows.txt" > "$OUT/overlay_window.txt"
  [ "$(grep -m1 -o "isVisible=[a-z]*" "$OUT/overlay_window.txt")" = "isVisible=true" ]
}

zen() { adb shell settings get global zen_mode | tr -d '\r'; }

adb logcat -c
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
grant

adb shell am instrument -w -r $PKG.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrumented.txt" 2>&1
cat "$OUT/instrumented.txt"
grep -q "OK (" "$OUT/instrumented.txt" || fail "instrumented tests"

grant
adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | grep "packageName=" | sort -u > "$OUT/launcher_apps.txt"
flow 10_first_run
flow 20_tap_toggle
flow 30_block
overlay_visible || fail "overlay not visible over YouTube"
adb shell dumpsys alarm > "$OUT/alarms_30.txt"
grep -q "com.habittags.app" "$OUT/alarms_30.txt" || fail "no auto-off alarm scheduled while a mode is active"
[ "$(zen)" != "0" ] || fail "DND not on while desk mode active (zen_mode=$(zen))"

adb reboot
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
sleep 20
flow 40_after_reboot

# An app update kills the service; it must come back on its own.
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
grant
sleep 10
adb shell dumpsys activity services $PKG > "$OUT/services_after_update.txt"
grep -q "BlockService" "$OUT/services_after_update.txt" || fail "BlockService not restored after app update"

flow 50_gate
flow 60_strict
flow 70_autooff
sleep 100
adb shell dumpsys activity services $PKG > "$OUT/services_after_autooff.txt"
grep -q "BlockService" "$OUT/services_after_autooff.txt" && fail "BlockService still running after auto-off"
[ "$(zen)" = "0" ] || fail "DND still on after auto-off (zen_mode=$(zen))"
flow 80_after_autooff

adb logcat -d > "$OUT/logcat.txt"
mv ./*.png "$OUT/" 2>/dev/null || true
exit $status
