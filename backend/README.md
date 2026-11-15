# Backend (Supabase)

Project `bfwyytbghnfwnikpcugx` (Mumbai). Scripts use the Supabase Management API and read
`SUPABASE_ACCESS_TOKEN`, `SUPABASE_PROJECT_REF`, `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from
`~/.config/habit-tags/supabase.env` on the build VM. The access token never goes in this repo or CI.

- `scripts/migrate.sh` — apply pending `supabase/migrations/*.sql` (tracked in `public._migrations`).
- `scripts/sql.sh <file|->` — run SQL.
- `scripts/test.sh` — run `supabase/tests/*.sql`, each inside `begin … rollback`.
- `node scripts/gen_seed.mjs > supabase/seed/catalog_seed.sql` then `scripts/sql.sh supabase/seed/catalog_seed.sql` — load the app's bundled recipes.
