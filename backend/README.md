# Backend (Supabase)

Project `bfwyytbghnfwnikpcugx` (Mumbai). Scripts use the Supabase Management API and read
`SUPABASE_ACCESS_TOKEN`, `SUPABASE_PROJECT_REF`, `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from
`~/.config/habit-tags/supabase.env` on the build VM. The access token never goes in this repo or CI.

- `scripts/migrate.sh` — apply pending `supabase/migrations/*.sql` (tracked in `public._migrations`).
- `scripts/sql.sh <file|->` — run SQL.
- `scripts/test.sh` — run `supabase/tests/*.sql`, each inside `begin … rollback`.
- `node scripts/gen_seed.mjs > supabase/seed/catalog_seed.sql` then `scripts/sql.sh supabase/seed/catalog_seed.sql` — load the app's bundled recipes.

## Publishing a recipe

1. Supabase dashboard → Table editor → `recipes` → Insert row.
2. `id`: the recipe id; `doc`: the full recipe JSON (same format as `android/app/src/main/assets/catalog.json`,
   and `doc.id` must equal `id`); `published`: true; `published_at`: now (it's boosted in Discover for 14 days);
   `sort_hint`: lower ranks earlier among recipes with equal usage.
3. Save. Open apps pick it up within seconds (Realtime); others on their next open.

Editing `doc` updates everyone's installed copy on their next catalog fetch; users keep their own settings.
Unpublishing hides it from Discover; phones that installed it keep it as "No longer offered".

## Stats

`recipe_stats` refreshes hourly (pg_cron). Phones report every ~6 h. Device ids starting
`00000000-0000-0000-0000-` are test devices and never counted.
