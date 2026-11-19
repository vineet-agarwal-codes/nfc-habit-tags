#!/usr/bin/env bash
# Fails if the release bundle's merged manifest carries debug-only parts (Zone lab, debug links)
# or permissions Mise must not ship, or if the special-use service lacks its declared subtype.
# Usage: check-release-manifest.sh path/to/app-release.aab   (needs bundletool on PATH or BUNDLETOOL=jar)
set -euo pipefail
aab=$1
if [ -n "${BUNDLETOOL:-}" ]; then bt=(java -jar "$BUNDLETOOL"); else bt=(bundletool); fi
manifest=$("${bt[@]}" dump manifest --bundle "$aab")
status=0
for bad in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION NEARBY_WIFI_DEVICES ACTIVITY_RECOGNITION ACCESS_BACKGROUND_LOCATION \
  ZoneTrackingService ZoneLabActivity ZoneResumeReceiver DebugTapReceiver DebugLinkActivity QUERY_ALL_PACKAGES habittags-debug; do
  if grep -q "$bad" <<<"$manifest"; then echo "release manifest contains $bad"; status=1; fi
done
grep -q "FOREGROUND_SERVICE_SPECIAL_USE" <<<"$manifest" || { echo "missing FOREGROUND_SERVICE_SPECIAL_USE"; status=1; }
grep -q "PROPERTY_SPECIAL_USE_FGS_SUBTYPE" <<<"$manifest" || { echo "special-use service has no PROPERTY_SPECIAL_USE_FGS_SUBTYPE"; status=1; }
grep -q 'package="app.mise"' <<<"$manifest" || { echo "applicationId is not app.mise"; status=1; }
[ $status = 0 ] && echo "release manifest OK"
exit $status
