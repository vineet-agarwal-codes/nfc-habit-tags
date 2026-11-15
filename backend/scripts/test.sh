#!/usr/bin/env bash
# Runs each tests/*.sql inside BEGIN … ROLLBACK; a raised exception fails the run.
set -uo pipefail
dir=$(cd "$(dirname "$0")/.." && pwd)/supabase/tests
sql="$(dirname "$0")/sql.sh"
status=0
for f in "$dir"/*.sql; do
  if out=$({ echo "begin;"; cat "$f"; echo; echo "rollback;"; } | "$sql" - 2>&1); then
    echo "PASS $(basename "$f")"
  else
    echo "FAIL $(basename "$f"): $out"; status=1
  fi
done
exit $status
