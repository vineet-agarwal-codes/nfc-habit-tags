# Discover, My Recipes and Usage Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the catalog online (near-instant updates), collect anonymous usage events every 6 hours, rank recipes by weekly active phones, and replace Home with a Discover / My recipes bottom-nav app.

**Architecture:** Supabase (project `bfwyytbghnfwnikpcugx`, Mumbai) holds `recipes` / `app_groups` as JSON documents, a raw `events` store plus two derived tables written only by the `report_events` RPC, and an hourly-refreshed `recipe_stats` materialized view. Everything platform-neutral (state v2 + migration, event building, remote catalog decoding/merging, Discover ordering and labels, Realtime protocol messages) goes in the shared KMP `:core` so iOS reuses it. The Android app only does HTTP (OkHttp), the Realtime websocket, WorkManager scheduling, caching to a file, and the new Compose screens. CI never talks to the production project: debug builds can switch to a fixture backend that reads bundled JSON and writes reports to a local file.

**Tech Stack:** existing stack + OkHttp 4.12, WorkManager 2.10, lifecycle-process 2.8.7; Supabase Postgres (pg_cron, Realtime, RLS); Supabase Management API (`/v1/projects/{ref}/database/query`) for migrations and SQL tests; Node 22 for small backend scripts.

**Spec:** `docs/superpowers/specs/2026-11-15-discover-and-stats-design.md` (builds on `docs/superpowers/specs/2026-11-14-nfc-habit-tags-design.md`)

## Global Constraints

- Supabase URL `https://bfwyytbghnfwnikpcugx.supabase.co` and publishable key `sb_publishable_eKASnJMTC64q14v01LMnEQ_IgEQVup8` are public: committed as `BuildConfig.SUPABASE_URL` / `SUPABASE_KEY` and sent as the `apikey` header.
- `SUPABASE_ACCESS_TOKEN` lives only in `~/.config/habit-tags/supabase.env` on the build VM. Never in the repo, CI, logs or commit messages. Scripts `source` that file.
- Clients can read: published `recipes`, `app_groups`, `recipe_stats`. Clients can execute: `report_events`. Nothing else (RLS + revoked grants).
- Events carry no times of day, tag IDs, labels or app lists. Days are the phone's local date `YYYY-MM-DD`.
- Report cadence: WorkManager periodic every 6 h (network connected) + on app foreground when the last success was more than 6 h ago.
- Catalog freshness: fetch on every app foreground; Realtime subscription on `recipes` and `app_groups` while in foreground; any change → refetch (debounced 2 s).
- `recipe_stats` refreshed hourly by pg_cron; raw `events`, `daily_usage` older than 90 days deleted daily.
- Device IDs beginning `00000000-0000-0000-0000-` are test devices: accepted by the RPC, excluded from `recipe_stats`.
- New-recipe boost: up to 3 recipes published in the last 14 days occupy the top of Discover, newest first; the rest sort by `weekly_active` desc, `installs` desc, `sort_hint` asc, title.
- Stat labels: "Used by N people this week" only if N ≥ 5; "N installs" only if N ≥ 5 (grouped digits, e.g. `1,240`); if both hidden → "New". Offline/no stats → no label.
- RPC limits: ≤ 500 events per call, payload ≤ 4 KB, `installed_snapshot` ≤ 200 IDs, `tap_count` count 0..500, day within [today−14, today+1] (server date), else the event is skipped (not the whole call).
- Existing Maestro assertions keep working: the active-mode strip shows the heading "On now" and an "End now" button per mode.
- Debug-only code lives in `src/debug/` with a `src/release/` counterpart, following `nfc/TagIoFactory.kt`.

## Review Focus

1. A remote recipe document that fails to parse or needs a newer schema → that recipe is skipped (installed copies show "Update the app to use this"), the rest of the catalog still loads (Task 6 table cases).
2. An installed recipe is unpublished or deleted by the author → taps keep working from the last cached copy; it stays in My recipes and never shows in Discover (Task 6 `mergeCatalog` cases).
3. The same report is retried after the server already applied it → `daily_usage` and `device_installs` are unchanged, not doubled (Task 2 SQL test `retry is idempotent`).
4. Taps around local midnight and after a timezone change → counted on the phone's local date at the time of the tap (Task 5 cases with taps at 23:59 / 00:01 and a TZ change).
5. First launch offline → bundled catalog, no stats labels, no crash; the report stays pending and is sent later (Task 8 e2e with fixture backend in `offline` mode).

---

## File Structure

```
backend/supabase/migrations/0001_catalog.sql      recipes, app_groups, RLS, realtime publication
backend/supabase/migrations/0002_events.sql       events, device_installs, daily_usage, report_events RPC
backend/supabase/migrations/0003_stats.sql        recipe_stats view, pg_cron jobs
backend/supabase/seed/catalog_seed.sql            generated from android assets catalog.json
backend/supabase/tests/*.sql                      assertions, each run inside BEGIN … ROLLBACK
backend/scripts/sql.sh                            run a SQL file via Management API (sources the env file)
backend/scripts/migrate.sh                        apply pending migrations, track in public._migrations
backend/scripts/test.sh                           run every tests/*.sql wrapped in a transaction, fail on error
backend/scripts/gen_seed.mjs                      catalog.json → catalog_seed.sql
backend/scripts/realtime_smoke.mjs                subscribe, touch a test row, expect a change in 10 s
android/core/src/commonMain/kotlin/com/habittags/core/
  store/StateStore.kt        (modify) AppState v2 + migrate()
  usage/UsageEvents.kt       UsageEvent model + buildReport()
  catalog/RemoteCatalog.kt   CatalogSnapshot, decodeRemoteCatalog(), mergeCatalog()
  catalog/Discover.kt        discoverOrder(), statLabel()
  realtime/Realtime.kt       Phoenix join/heartbeat messages, isCatalogChange()
android/app/src/main/kotlin/com/habittags/app/
  backend/Backend.kt         interface Backend + SupabaseBackend (OkHttp)
  backend/CatalogRepository.kt  cache file, foreground refresh, debounce
  backend/RealtimeWatcher.kt websocket lifecycle
  backend/UsageReportWorker.kt
  ui/ DiscoverScreen, RecipeScreen, MyRecipesScreen, RecipeEditScreen, SetupTagScreen, ActiveModeStrip (Home/Catalog screens removed)
android/app/src/debug/kotlin/com/habittags/app/backend/BackendFactory.kt   fixture/offline/real
android/app/src/release/kotlin/com/habittags/app/backend/BackendFactory.kt real only
android/app/src/debug/assets/fixture_backend/{recipes,app_groups,recipe_stats}.json
```

---

### Task 1: Backend catalog schema, seed, scripts and access tests

**Files:**
- Create: `backend/scripts/sql.sh`, `backend/scripts/migrate.sh`, `backend/scripts/test.sh`, `backend/scripts/gen_seed.mjs`, `backend/supabase/migrations/0001_catalog.sql`, `backend/supabase/seed/catalog_seed.sql`, `backend/supabase/tests/01_catalog_access.sql`, `backend/README.md` (how to run the scripts; where the token lives)

**Interfaces:**
- Produces:
  - `sql.sh <file>`: POSTs the file's contents as `{"query": …}` to `https://api.supabase.com/v1/projects/$SUPABASE_PROJECT_REF/database/query` with `Authorization: Bearer $SUPABASE_ACCESS_TOKEN`; prints the JSON result; exits non-zero on HTTP ≥ 400.
  - `migrate.sh`: creates `public._migrations(name text primary key, applied_at timestamptz default now())` if missing, applies each `migrations/*.sql` not yet recorded (in name order), records it.
  - `test.sh`: for each `tests/*.sql`, sends `begin; <file>; rollback;`; any `raise exception` → fail with the file name.
  - Tables: `recipes(id text primary key, doc jsonb not null, published bool not null default false, published_at timestamptz, sort_hint int not null default 100, updated_at timestamptz not null default now(), check (doc->>'id' = id))`; `app_groups(id text primary key, doc jsonb not null, updated_at timestamptz not null default now())`; `updated_at` trigger on both.
  - RLS on; policy `anon, authenticated` select on `recipes` where `published`; select on all `app_groups`; no insert/update/delete policies.
  - `alter publication supabase_realtime add table recipes, app_groups`.
  - Seed: every recipe in `android/app/src/main/assets/catalog.json` upserted with `published = true`, `published_at = '2026-11-01'`, `sort_hint` = its index × 10; every app group as `doc`.
- Tests use `set local role anon;` to act as a client.

- [ ] **Step 1:** Write `tests/01_catalog_access.sql` (each check a `do $$ … raise exception … $$`):

```sql
-- as anon: count(*) from recipes = count of published seeded recipes (4)
-- as anon: an inserted unpublished row (inserted as postgres first) is not visible
-- as anon: insert into recipes … fails with insufficient_privilege / RLS violation
-- as anon: select from app_groups returns 4 rows
-- insert with doc->>'id' <> id fails the check constraint
```
- [ ] **Step 2:** `backend/scripts/test.sh` → FAIL (relation "recipes" does not exist).
- [ ] **Step 3:** Write the scripts, `0001_catalog.sql`, `gen_seed.mjs`; run `node backend/scripts/gen_seed.mjs > backend/supabase/seed/catalog_seed.sql`; `backend/scripts/migrate.sh`; `backend/scripts/sql.sh backend/supabase/seed/catalog_seed.sql`.
- [ ] **Step 4:** `backend/scripts/test.sh` → PASS. Also `curl -s "$SUPABASE_URL/rest/v1/recipes?select=id" -H "apikey: $SUPABASE_PUBLISHABLE_KEY"` returns the 4 seed IDs.
- [ ] **Step 5:** `git grep -n "sbp_\|SUPABASE_ACCESS_TOKEN=" -- . ':!backend/README.md'` returns nothing. Commit.

### Task 2: Events store and `report_events` RPC

**Files:**
- Create: `backend/supabase/migrations/0002_events.sql`, `backend/supabase/tests/02_report_events.sql`

**Interfaces:**
- Consumes: `recipes` (Task 1)
- Produces:
  - `events(id bigserial primary key, device_id uuid not null, type text not null, v int not null, day date, payload jsonb not null, received_at timestamptz not null default now())`, index on `(type, day)`.
  - `device_installs(device_id uuid, recipe_id text, last_seen date not null, primary key (device_id, recipe_id))`.
  - `daily_usage(device_id uuid, recipe_id text, day date, taps int not null, primary key (device_id, recipe_id, day))`.
  - All three: RLS on, no policies, `revoke all … from anon, authenticated`.
  - `report_events(device_id uuid, events jsonb) returns int` (count of events stored) — `security definer`, `set search_path = public`, `grant execute … to anon, authenticated`. Per event: skip if not an object, payload > 4 KB, or `day` outside [current_date − 14, current_date + 1]; insert into `events`; then:
    - `installed_snapshot` v1: `delete from device_installs where device_id = $1`; insert one row per ID in `payload.recipes` (first 200, only IDs present in `recipes`), `last_seen = current_date`.
    - `tap_count` v1: if `payload.recipe` exists in `recipes` and `0 ≤ count ≤ 500` → upsert `daily_usage` setting `taps = excluded.taps`.
    - other types: stored in `events` only.
  - More than 500 events in one call → raise exception `too many events`.

- [ ] **Step 1:** `tests/02_report_events.sql` (as `anon`, calling the RPC, then checking as postgres):

```sql
-- snapshot ['desk-deep-work','gym-bag'] → device_installs has those 2 rows for the device
-- second snapshot ['gym-bag'] → only gym-bag remains
-- tap_count desk 3 for today, then desk 5 for today → daily_usage.taps = 5
-- retry is idempotent: same call twice → daily_usage and device_installs identical after both
-- unknown recipe 'nope' in snapshot and tap_count → ignored in derived tables, event row stored
-- day = current_date - 20 → skipped (return value excludes it, no row)
-- type 'future_thing' → stored in events, no derived rows
-- 501 events → exception
-- as anon: select from events / daily_usage / device_installs → permission denied
```
- [ ] **Step 2:** `test.sh` → FAIL. **Step 3:** Write `0002_events.sql`; `migrate.sh`. **Step 4:** `test.sh` → PASS. **Step 5:** Commit.

### Task 3: Stats view, cron jobs and Realtime smoke

**Files:**
- Create: `backend/supabase/migrations/0003_stats.sql`, `backend/supabase/tests/03_recipe_stats.sql`, `backend/scripts/realtime_smoke.mjs`

**Interfaces:**
- Consumes: Task 2 tables
- Produces:
  - `create extension if not exists pg_cron`.
  - Materialized view `recipe_stats(recipe_id text, weekly_active int, installs int, refreshed_at timestamptz)` with a unique index on `recipe_id`: one row per published recipe; `weekly_active` = distinct `device_id` in `daily_usage` with `taps > 0` and `day >= current_date - 6`; `installs` = distinct `device_id` in `device_installs` with `last_seen >= current_date - 30`; both exclude `device_id::text like '00000000-0000-0000-0000-%'`.
  - `grant select on recipe_stats to anon, authenticated`.
  - Cron jobs: `refresh-recipe-stats` `5 * * * *` → `refresh materialized view concurrently recipe_stats`; `purge-old-usage` `17 3 * * *` → delete `events` with `received_at < now() - interval '90 days'` and `daily_usage` with `day < current_date - 90`.
  - `realtime_smoke.mjs`: opens `wss://bfwyytbghnfwnikpcugx.supabase.co/realtime/v1/websocket?apikey=<publishable>&vsn=1.0.0` (Node 22 global `WebSocket`), joins `realtime:catalog` with `postgres_changes` for `recipes` and `app_groups`, then (via `sql.sh`) updates `updated_at` of a recipe; exits 0 if a `postgres_changes` message arrives within 10 s, else 1. Changes only reach the client if the row is visible under RLS, so it touches a published seed row.

- [ ] **Step 1:** `tests/03_recipe_stats.sql`:

```sql
-- 6 real devices tap desk today, 1 device tapped desk 8 days ago, 1 test device (00000000-…) taps desk today
--   → after refresh: desk weekly_active = 6
-- 7 devices with desk in snapshot, one with last_seen 31 days ago → installs = 6
-- a published recipe with no usage has a row with 0, 0
-- select cron.job where jobname in ('refresh-recipe-stats','purge-old-usage') → 2 rows
-- as anon: select from recipe_stats works
```
- [ ] **Step 2:** FAIL. **Step 3:** Write migration; `migrate.sh`. **Step 4:** `test.sh` → PASS; `node backend/scripts/realtime_smoke.mjs` → exit 0. **Step 5:** Commit.

### Task 4: Core — AppState v2 and migration

**Files:**
- Modify: `android/core/src/commonMain/kotlin/com/habittags/core/store/StateStore.kt`
- Test: `android/core/src/commonTest/kotlin/com/habittags/core/store/StateCodecTest.kt`

**Interfaces:**
- Produces:
  - `TapLogEntry(atMs: Long, tagId: String, label: String, recipeId: String? = null)` — taps written by `TapHandler` set `recipeId`; `Log` action entries leave it null and are never reported.
  - `AppState` gains: `stateVersion: Int = 2`, `installedRecipes: Set<String> = emptySet()`, `deviceId: String? = null`, `lastReportMs: Long = 0`, `pendingEvents: List<UsageEvent> = emptyList()` (`UsageEvent` from Task 5 — define Task 5's model first if implementing in order).
  - `StateCodec.decode` applies `migrate(state)`: if the decoded JSON has no `stateVersion` (v1) → `installedRecipes = tags.values.map { it.recipeId }.toSet()`, `stateVersion = 2`. Decoding treats a missing `stateVersion` as 1 (the data class default stays 2 for fresh state).
  - `fun AppState.withDeviceId(newId: () -> String): AppState` — sets `deviceId` once if null.

- [ ] **Step 1:** Add table cases to `StateCodecTest`:

```kotlin
v1 JSON with tags {t1→desk, t2→desk, t3→gym} and no stateVersion → installedRecipes {desk, gym}, stateVersion 2
v2 JSON with installedRecipes {bed} and tags {t1→desk} → unchanged ({bed})
round-trip of a state with pendingEvents and recipeId on log entries → equal
withDeviceId on null → set ; on existing → unchanged
```
- [ ] **Step 2:** `./gradlew :core:jvmTest` → FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Commit.

### Task 5: Core — usage events and report building

**Files:**
- Create: `android/core/src/commonMain/kotlin/com/habittags/core/usage/UsageEvents.kt`
- Test: `android/core/src/commonTest/kotlin/com/habittags/core/usage/UsageEventsTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class UsageEvent(val type: String, val v: Int, val day: String?, val payload: JsonObject)`
  - `const val REPORT_INTERVAL_MS = 6 * 60 * 60 * 1000L`, `const val MAX_REPORT_DAYS = 14`
  - `fun buildReport(state: AppState, now: Instant, tz: TimeZone): List<UsageEvent>` = `state.pendingEvents` + one `installed_snapshot` (payload `{"recipes": [...sorted installedRecipes]}`, day = today) + one `tap_count` per (recipeId, local day) from `tapLog` entries with non-null `recipeId`, for days ≥ max(today − 13, local day of `lastReportMs` if > 0), count = number of entries that day (all days counted fully, so re-sends are idempotent).
  - `fun reportDue(state: AppState, nowMs: Long): Boolean` = `nowMs - state.lastReportMs >= REPORT_INTERVAL_MS`
  - `fun AppState.afterReportSent(nowMs: Long): AppState` = `copy(lastReportMs = nowMs, pendingEvents = emptyList())`

- [ ] **Step 1:** Table tests (tz `Asia/Kolkata` unless stated):

```kotlin
taps desk at 23:59 and 00:01 → tap_count desk day1=1, day2=1
3 taps desk + 1 Log entry (recipeId null) same day → desk count 3, no event for the log entry
lastReportMs at day D 10:00, taps on D-1 and D → only day D counted (full-day count for D)
lastReportMs = 0, taps 20 days ago and today → only today
tz change: tap at 2026-11-15T19:00Z built with Asia/Kolkata → day 2026-11-16 ; with America/New_York → 2026-11-15
installed {gym, desk} → installed_snapshot recipes ["desk","gym"]
pendingEvents [x] are included first; afterReportSent clears them and sets lastReportMs
reportDue: 5h59m → false, 6h → true
```
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Commit.

### Task 6: Core — remote catalog, merge and Discover ordering

**Files:**
- Create: `android/core/src/commonMain/kotlin/com/habittags/core/catalog/RemoteCatalog.kt`, `.../catalog/Discover.kt`
- Test: `RemoteCatalogTest.kt`, `DiscoverTest.kt`

**Interfaces:**
- Consumes: `Catalog`, `Recipe`, `AppGroup`, `isUsable`, `CatalogJson`/`parseCatalog` internals (Task 1 of the Android MVP)
- Produces:
  - `@Serializable data class RecipeMeta(val publishedAt: Instant? = null, val sortHint: Int = 100)`
  - `@Serializable data class RecipeStats(val weeklyActive: Int, val installs: Int)`
  - `@Serializable data class CatalogSnapshot(val catalog: Catalog, val meta: Map<String, RecipeMeta> = emptyMap(), val stats: Map<String, RecipeStats> = emptyMap(), val fetchedAtMs: Long = 0)`
  - `fun decodeRemoteCatalog(recipesJson: String, groupsJson: String, statsJson: String?, fetchedAtMs: Long): CatalogSnapshot` — inputs are PostgREST arrays: recipes `[{id, doc, published_at, sort_hint}]`, groups `[{id, doc}]`, stats `[{recipe_id, weekly_active, installs}]`. Each recipe/group doc decoded independently; a doc that fails to decode is dropped. Recipes needing a newer schema are kept (so installed copies can say "Update the app"). `statsJson` null or malformed → empty stats.
  - `fun mergeCatalog(previous: CatalogSnapshot?, fresh: CatalogSnapshot, installed: Set<String>): CatalogSnapshot` — fresh, plus any recipe in `installed` missing from fresh but present in `previous` (kept, with `meta.retired = true`… pin: add `val retired: Boolean = false` to `RecipeMeta`).
  - `fun discoverOrder(snapshot: CatalogSnapshot, installed: Set<String>, now: Instant): List<Recipe>` — usable, not installed, not retired; boost/sort per Global Constraints.
  - `fun statLabel(stats: RecipeStats?): List<String>` — `[]` if null; else per Global Constraints (`["Used by 320 people this week", "1,240 installs"]`, `["New"]`, …).

- [ ] **Step 1:** Tests:

```kotlin
decode: 4 valid recipes + 1 doc {"id":"bad"} (missing fields) → 4 recipes; group doc invalid → dropped
decode: recipe with schemaVersion 2 → present, isUsable false; statsJson "oops" → stats empty
merge: previous has desk, fresh lacks desk, installed {desk} → desk present, meta[desk].retired = true
merge: previous has front-door, fresh lacks it, not installed → absent
discoverOrder (now = 2026-11-15): stats gym 40/100, bed 30/500, desk 12/80, door 3/3; new1 published 2026-11-10, new2 2026-11-14, new3 2026-11-12, new4 2026-11-13, old ones 2026-11-01
  → [new2, new4, new3, gym, bed, desk, new1?...] — pin: new1 (oldest of 4 new) falls back to normal ranking by its stats (none → after all with stats, by sortHint)
discoverOrder excludes installed {gym} and unusable/retired recipes; all installed → empty list
statLabel: (320, 1240) → ["Used by 320 people this week", "1,240 installs"] ; (4, 1240) → ["1,240 installs"] ; (3, 3) → ["New"] ; null → []
```
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Commit.

### Task 7: Core — Realtime protocol messages

**Files:**
- Create: `android/core/src/commonMain/kotlin/com/habittags/core/realtime/Realtime.kt`
- Test: `RealtimeTest.kt`

**Interfaces:**
- Produces: `object Realtime { const val TOPIC = "realtime:catalog"; const val HEARTBEAT_MS = 25_000L; fun socketUrl(baseUrl: String, apiKey: String): String; fun joinMessage(ref: Int, apiKey: String): String; fun heartbeat(ref: Int): String; fun isCatalogChange(message: String): Boolean }`
  - `socketUrl("https://x.supabase.co", k)` = `wss://x.supabase.co/realtime/v1/websocket?apikey=k&vsn=1.0.0`
  - join: `{"topic":"realtime:catalog","event":"phx_join","ref":"<ref>","payload":{"config":{"postgres_changes":[{"event":"*","schema":"public","table":"recipes"},{"event":"*","schema":"public","table":"app_groups"}]},"access_token":"<apiKey>"}}`
  - heartbeat: `{"topic":"phoenix","event":"heartbeat","ref":"<ref>","payload":{}}`
  - `isCatalogChange`: true only for `event == "postgres_changes"` on `TOPIC`; false for replies, heartbeats, malformed JSON.

- [ ] **Step 1:** Tests for the exact strings above (parsed and compared as JSON) and `isCatalogChange` cases (change → true; `phx_reply` → false; `"garbage"` → false).
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS (and `node backend/scripts/realtime_smoke.mjs` still passes, it uses the same message shapes). **Step 5:** Commit.

### Task 8: Android — backend client, catalog repository, Realtime, reporting

**Files:**
- Create: `app/.../backend/Backend.kt`, `backend/CatalogRepository.kt`, `backend/RealtimeWatcher.kt`, `backend/UsageReportWorker.kt`, `app/src/debug/.../backend/BackendFactory.kt`, `app/src/release/.../backend/BackendFactory.kt`, `app/src/debug/assets/fixture_backend/{recipes,app_groups,recipe_stats}.json`
- Modify: `HabitTagsApp.kt` (catalog becomes `StateFlow<CatalogSnapshot?>`; `catalogNow()`/`recipe()`/`groups()` read `snapshot.catalog`; device ID set on create), `TapHandler.kt` (log `recipeId`), `DebugLinkActivity.kt` (new links), `app/build.gradle.kts` + `libs.versions.toml` (OkHttp 4.12.0, work-runtime-ktx 2.10.0, lifecycle-process 2.8.7, BuildConfig fields), `scripts/e2e.sh`
- Remove: `catalog/CatalogSource.kt` (bundled loading moves into `CatalogRepository` as the fallback)

**Interfaces:**
- Consumes: Tasks 4–7.
- Produces:
  - `interface Backend { suspend fun fetchCatalog(): CatalogSnapshot; suspend fun report(deviceId: String, events: List<UsageEvent>); val supportsRealtime: Boolean }` — `SupabaseBackend(baseUrl, key, client: OkHttpClient)`: three GETs (`/rest/v1/recipes?select=id,doc,published_at,sort_hint&published=is.true`, `/rest/v1/app_groups?select=id,doc`, `/rest/v1/recipe_stats?select=recipe_id,weekly_active,installs`) with `apikey` header, decoded by `decodeRemoteCatalog`; stats failure doesn't fail the catalog. `report` POSTs `{"device_id":…, "events":[…]}` to `/rest/v1/rpc/report_events`; non-2xx → `IOException`.
  - `BackendFactory.create(context): Backend` — release: `SupabaseBackend`. Debug: reads `DebugSettings.backendMode(context)` ∈ `real` (default) | `fixture` | `offline`; `fixture` serves the debug assets and appends each report as one JSON line to `filesDir/debug_reports.jsonl`, `supportsRealtime = false`; `offline` throws `IOException` on every call.
  - `CatalogRepository(app)`: on start loads `filesDir/catalog_cache.json` → else bundled `assets/catalog.json` (stats empty); `refresh()` = `backend.fetchCatalog()` → `mergeCatalog(previous, fresh, installedRecipes)` → save cache → publish; failures keep the current snapshot. Called on every `ProcessLifecycleOwner` `ON_START` and by `RealtimeWatcher` (debounced 2 s).
  - `RealtimeWatcher`: connects on `ON_START` (if `supportsRealtime`), closes on `ON_STOP`; heartbeat every `Realtime.HEARTBEAT_MS`; reconnect with backoff 2 s → 60 s; `isCatalogChange` → `CatalogRepository.refresh()`.
  - `UsageReportWorker` (`CoroutineWorker`): `buildReport(state, now, TimeZone.currentSystemDefault())` → `backend.report` → `afterReportSent`; failure → `Result.retry()`. Enqueued as unique periodic work `usage-report` every 6 h (`NetworkType.CONNECTED`) at app start, plus unique one-time work `usage-report-now` on `ON_START` when `reportDue`.
  - Debug links: `habittags-debug://backend/<real|fixture|offline>`, `habittags-debug://report-now` (runs the report inline).
  - Fixture data: the 4 seed recipes + `new-recipe-a` (published 2 days before the build's fixed fixture "now"? — pin: `published_at` = `"{{RECENT}}"` replaced at load with now − 2 days) and stats gym 40/100, bed 30/500, desk 12/80, door 3/3.

- [ ] **Step 1:** Implement; push; CI green (`gh run watch --exit-status` in background).
- [ ] **Step 2:** e2e additions in `scripts/e2e.sh` (fixture mode set before the first flow): after `20_tap_toggle`, open `report-now`, then `adb shell run-as com.habittags.app cat files/debug_reports.jsonl` must contain an `installed_snapshot` with `desk-deep-work` and a `tap_count` for `desk-deep-work` with `count` ≥ 2. Offline case: clear app data, set `offline`, launch → Discover shows the 4 bundled recipes with no "Used by" text (Maestro `05_offline.yaml`), `report-now` leaves `debug_reports.jsonl` absent; switch to `fixture`, `report-now` → file present.
- [ ] **Step 3: Production smoke (VM, not CI):** `backend/scripts/sql.sh` is not needed — call the RPC with curl and the publishable key as device `00000000-0000-0000-0000-000000000001` with one `installed_snapshot` and one `tap_count`; expect `1`/`2` returned and `recipe_stats` unchanged for real counts. Commit.

### Task 9: Android UI — Discover, My recipes, recipe flows, strip, setup picker

**Files:**
- Create: `ui/DiscoverScreen.kt`, `ui/RecipeScreen.kt`, `ui/MyRecipesScreen.kt`, `ui/RecipeEditScreen.kt`, `ui/SetupTagScreen.kt`, `ui/ActiveModeStrip.kt`
- Modify: `ui/AppNav.kt` (Scaffold with `NavigationBar`: "Discover", "My recipes"; strip above content on both tabs; start destination `discover`), `ui/InstallScreen.kt` (adds "Skip for now"; on finish adds to `installedRecipes` and navigates to `edit/{recipeId}`), `ui/TagDetailScreen.kt` (reached from edit), `ui/GroupEditor.kt` (unchanged behaviour)
- Remove: `ui/HomeScreen.kt`, `ui/CatalogScreen.kt`
- Modify/Create Maestro: `10_first_run.yaml`, new `15_discover.yaml`, `05_offline.yaml`; adjust `70_autooff.yaml` and any flow that navigates via "Add a tag"/"My tags".

**Interfaces:**
- Consumes: `discoverOrder`, `statLabel`, `CatalogRepository`, `HabitTagsApp.update`, existing install/write/gate flows.
- Screens and exact copy:
  - **Discover:** cards for `discoverOrder(...)`, each showing title, one-line description and `statLabel` lines. Empty → "You've installed every recipe — more coming soon." Tap card → `recipe/{id}`.
  - **Recipe** (`recipe/{id}`): title, description, placement hint, stat lines, button "Install" → existing install flow (settings → apps to block → permissions → "Write to a tag" or "Skip for now"). If already installed, the button reads "Edit" → `edit/{id}`.
  - **My recipes:** installed recipes (title, linked tag count "2 tags"/"No tag yet", "On" badge when any of its tags is active, "Update the app to use this" if unusable, "No longer offered" if retired). Tap → `edit/{id}`. Empty → "Nothing installed yet" + button "Browse recipes" → Discover.
  - **Edit** (`edit/{id}`): settings sliders (stored on each of the recipe's tags; a recipe with no tags keeps them as defaults for its next tag — pin: store as `InstalledTag.params` per tag only; with no tags the sliders are hidden and "Write to a tag" asks for settings first), "Apps to block" (GroupEditor per group used), "Tags" list → `tag/{tagId}`, "Write another tag", "Uninstall" (disabled with "Turn this mode off first" while any of its tags is active; else removes the recipe from `installedRecipes` and its tags from `tags`).
  - **ActiveModeStrip:** hidden when nothing is active; else heading "On now", then per active tag: "<label> · ends HH:MM" (or "· strict, N min left") + "End now" → existing `gate/{tagId}`.
  - **Set up this tag** (`setup/{tagId}`, opened for `UnknownTag`): heading "Set up this tag", list of usable recipes, installed first then `discoverOrder`; picking one → install flow with `setupTagId` (no write), which also adds it to `installedRecipes`.

- [ ] **Step 1:** Implement; push; CI green.
- [ ] **Step 2: Maestro (fixture backend):**
  - `10_first_run.yaml`: launch → "Discover" visible → tap "Desk: Deep Work" → "Install" → add Contacts via "Add app" → "Write to a tag" → lands on Edit with "1 tag" → back to "My recipes" tab shows "Desk: Deep Work"; Discover no longer lists it.
  - `15_discover.yaml`: order of the first four cards is `new-recipe-a` title, then Gym bag, Bed, (desk is installed so absent), Front door shows "New"; Gym bag shows "Used by 40 people this week" and "100 installs".
  - Existing flows pass unchanged except navigation steps (strip keeps "On now"/"End now").
  - Uninstall: with desk active, "Uninstall" disabled; after ending, uninstall → desk back in Discover.
- [ ] **Step 3:** Commit; push; CI green.

### Task 10: Wrap-up

- [ ] **Step 1:** Update `docs/superpowers/specs/2026-11-15-discover-and-stats-design.md` "Calls made" only if implementation forced a change; update `backend/README.md` with how authors publish (Table editor → `recipes` → paste doc JSON, set `published` and `published_at`).
- [ ] **Step 2:** Run `backend/scripts/test.sh`, `node backend/scripts/realtime_smoke.mjs`, `./gradlew :core:jvmTest`; CI green on the final push.
- [ ] **Step 3:** One fresh reviewer (most capable model) over the whole branch diff since the plan commit; fix confirmed findings; push; CI green.
