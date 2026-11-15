#!/usr/bin/env bash
# Applies migrations/*.sql not yet recorded in public._migrations, in name order.
set -euo pipefail
dir=$(cd "$(dirname "$0")/.." && pwd)/supabase/migrations
sql="$(dirname "$0")/sql.sh"
echo "create table if not exists public._migrations(name text primary key, applied_at timestamptz default now()); revoke all on public._migrations from anon, authenticated;" | "$sql" - >/dev/null
applied=$(echo "select coalesce(json_agg(name), '[]') as names from public._migrations;" | "$sql" -)
for f in "$dir"/*.sql; do
  name=$(basename "$f")
  if node -e 'const r=JSON.parse(process.argv[1]); process.exit(r[0].names.includes(process.argv[2]) ? 0 : 1)' "$applied" "$name"; then continue; fi
  echo "applying $name"
  { echo "begin;"; cat "$f"; echo; echo "insert into public._migrations(name) values ('$name');"; echo "commit;"; } | "$sql" - >/dev/null
done
echo "migrations up to date"
