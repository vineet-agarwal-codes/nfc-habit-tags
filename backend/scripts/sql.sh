#!/usr/bin/env bash
# Runs SQL (a file, or stdin with "-") on the Supabase project via the Management API.
set -euo pipefail
set -a; . "$HOME/.config/habit-tags/supabase.env"; set +a
src=${1:--}
query=$(cat "$src")
body=$(node -e 'process.stdout.write(JSON.stringify({query: require("fs").readFileSync(0, "utf8")}))' <<<"$query")
out=$(mktemp)
code=$(curl -sS -o "$out" -w '%{http_code}' -X POST \
  "https://api.supabase.com/v1/projects/$SUPABASE_PROJECT_REF/database/query" \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" -H 'Content-Type: application/json' \
  --data-binary "$body")
cat "$out"; echo
rm -f "$out"
[ "$code" -lt 400 ] || { echo "sql.sh: HTTP $code" >&2; exit 1; }
