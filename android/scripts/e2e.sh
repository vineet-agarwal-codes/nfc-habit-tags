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
# Before the app's first launch: start in the debug "offline" backend so no test run ever
# reaches the production project (debug builds default to the real backend).
adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && echo \"<map><string name=\\\"backend\\\">offline</string></map>\" > shared_prefs/debug.xml'"
adb shell run-as $PKG cat shared_prefs/debug.xml

adb shell am instrument -w -r $PKG.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrumented.txt" 2>&1
cat "$OUT/instrumented.txt"
grep -q "OK (" "$OUT/instrumented.txt" || fail "instrumented tests"

grant
adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | grep "packageName=" | sort -u > "$OUT/launcher_apps.txt"
debug_link() { adb shell am start -W -a android.intent.action.VIEW -d "habittags-debug://$1" $PKG > /dev/null; }
reports() { adb shell run-as $PKG cat files/debug_reports.jsonl > "$OUT/debug_reports.jsonl" 2>/dev/null; }

# First launch offline: bundled recipes, no stats, and the report stays unsent.
flow 05_offline
debug_link report-now
reports && fail "a report was written while offline"

# From here on: fixture recipes/stats, reports written to a local file.
debug_link backend/fixture
debug_link report-now
reports || fail "pending report not sent once back online"
flow 10_first_run
flow 15_discover
flow 20_tap_toggle

debug_link report-now
reports
node -e '
  const lines = require("fs").readFileSync(process.argv[1], "utf8").trim().split("\n").filter(Boolean);
  const events = lines.flatMap((l) => JSON.parse(l).events);
  const snap = events.find((e) => e.type === "installed_snapshot" && e.payload.recipes.includes("desk-deep-work"));
  const taps = events.find((e) => e.type === "tap_count" && e.payload.recipe === "desk-deep-work" && e.payload.count >= 2 && e.tz);
  process.exit(snap && taps ? 0 : 1);
' "$OUT/debug_reports.jsonl" || fail "usage report missing installed_snapshot or tap_count for desk-deep-work"
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
# YouTube is done with; left running it can pop its own "new version available" screen over the
# app mid-flow (seen in CI), which the active mode then covers with the block overlay.
adb shell am force-stop com.google.android.youtube
adb shell cmd appops set com.google.android.youtube RUN_ANY_IN_BACKGROUND ignore

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

# A mode ending must hand back the user's own DND, not switch it off.
adb shell am start -a android.intent.action.VIEW -d "habittags-debug://param/last/strictMinutes/0" $PKG
adb shell cmd notification set_dnd priority
sleep 2
[ "$(zen)" != "0" ] || fail "could not turn on user DND (zen_mode=$(zen))"
flow 20_tap_toggle
sleep 3
[ "$(zen)" != "0" ] || fail "user's own DND was turned off when the mode ended (zen_mode=$(zen))"
adb shell cmd notification set_dnd off
flow 90_uninstall
flow 95_manual_start
adb shell dumpsys activity services $PKG > "$OUT/services_after_manual.txt"
grep -q "BlockService" "$OUT/services_after_manual.txt" && fail "BlockService still running after manual mode auto-off"
flow 97_bed_hours

adb logcat -d > "$OUT/logcat.txt"
# Screenshot locations differ between Maestro versions; collect them wherever they landed.
mkdir -p "$OUT/screenshots"
find . "$HOME/.maestro" -name "*.png" -not -path "./build/e2e/*" -newer "$OUT/launcher_apps.txt" -exec cp {} "$OUT/screenshots/" \; 2>/dev/null || true
cp -r "$HOME/.maestro/tests" "$OUT/maestro-tests" 2>/dev/null || true
exit $status
