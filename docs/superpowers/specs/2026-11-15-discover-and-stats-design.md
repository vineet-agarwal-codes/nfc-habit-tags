# Discover, My Recipes and Usage Stats — Design

Date: 2026-11-15. Builds on `2026-11-14-nfc-habit-tags-design.md` (approved). Status: draft for Vineet's review.

## Intent

Drive adoption: people should keep discovering and installing more recipes. The app opens on a list of recipes the user hasn't installed yet, ranked by what other people actually use, with social proof on each card. Installed recipes move out of the way to their own tab.

## Decisions made with Vineet

- **Popularity = number of phones that used the recipe in the last 7 days** (at least one tap). Discover is sorted by it, highest first.
- **Discover shows only recipes not installed on this phone.** Installed recipes live in **My recipes**.
- **Bottom navigation bar**: Discover, My recipes. A **Profile** tab comes later (app-group editing, optional login for sync/restore, personal metrics) — not in this build.
- Stats are collected from every user's app and shown to everyone.
- The old Home screen and the separate "Write to a tag" entry point are removed; writing a tag happens inside a recipe.

## Goals

1. Discover tab: not-installed recipes, sorted by weekly active phones, each card showing "Used by N people this week · M installs".
2. Recipe detail → Install → settings → apps to block → permissions → Write to a tag → recipe appears in My recipes.
3. My recipes tab: installed recipes with Edit (settings, linked tags, write another tag, rename/rewrite/remove a tag, uninstall recipe).
4. Active modes stay visible: a strip at the top of both tabs ("Desk: Deep Work on · ends 11:30 · End now") while any mode is on.
5. Anonymous stats collection and an online catalog (recipes + stats) served from Supabase, so recipe edits and new recipes reach phones without an app update.

## Non-goals

- Profile tab, login, sync/restore, personal metrics (next build).
- Paid recipes, ratings, user-published recipes.
- Per-user analytics dashboards for the team beyond what Supabase's table editor/SQL gives.

## Anti-goals

- Collecting anything that identifies the person or reveals their phone usage beyond recipe-level counts. No account link, no blocked-app lists, no exact tap times, no IP stored in our tables.
- Ranking that is trivially gamed by one device (one device counts at most once per recipe per week).
- Discover breaking offline: no network still shows recipes (bundled/cached), just without stats.

## Approaches considered

- **A. Supabase tables + a write-only RPC + stats view (chosen).** Same backend already chosen for the catalog; the app reads aggregated stats with the public anon key; raw rows are never readable by clients.
- B. Third-party analytics (Firebase Analytics, PostHog). Good dashboards, but the app needs the aggregates back to sort and display them, which these tools don't serve to clients; also adds a second vendor and more data sharing.
- C. Count on our own tiny server. More to run and secure than Supabase for no gain.

## How it works

### Anonymous device ID

On first launch the app generates a random UUID (`deviceId`), stored only on the phone. Not linked to login (future login does not attach it). Reinstalling the app creates a new ID (accepted inflation of installs).

### What the phone sends

Once a day (WorkManager, on any network, plus once on app open if >24 h since the last send), the app calls one RPC:

```
report_usage(device_id uuid, installed text[], taps jsonb /* {"<recipeId>": <count>, ...} for each full local day since the last successful send, keyed by date */)
```

- `installed` = the full current set of recipe IDs installed on this phone (snapshot, so missed uninstall events can't skew counts).
- `taps` = per-day, per-recipe tap counts from the local tap log, only for days not yet reported. No times of day, no tag IDs, no labels.
- Failed sends retry next time; unsent days accumulate (cap 14 days).

### Backend (Supabase)

Tables (no client read access via RLS; written only through the RPC, `security definer`):
- `device_installs(device_id uuid, recipe_id text, last_seen date)`, primary key (device_id, recipe_id). Each report replaces this device's rows with the new snapshot.
- `daily_usage(device_id uuid, recipe_id text, day date, taps int)`, primary key (device_id, recipe_id, day), upsert.

Readable by clients:
- `recipe_stats(recipe_id, weekly_active int, installs int, refreshed_at)` — a materialized view: `weekly_active` = distinct devices with taps > 0 in the last 7 days; `installs` = distinct devices whose snapshot includes the recipe and `last_seen` within 30 days. Refreshed every 3 hours by `pg_cron`.
- `recipes(id, doc jsonb, published bool, sort_hint int, updated_at)` and `app_groups(id, doc jsonb)` — read-only for clients; authors edit in the Supabase dashboard.

RPC input checks: at most 200 installed IDs, at most 14 days × 200 recipes of taps, taps per day per recipe capped at 500, unknown recipe IDs ignored. Raw usage older than 90 days is deleted by a daily cron job.

### Online catalog

On app open (and at most every 6 h in the background) the app fetches published `recipes`, `app_groups` and `recipe_stats`, caches them on the phone, and falls back to the cache, then to the bundled `catalog.json`. Installed recipes follow the author's latest version via the existing `ParamMerge` rules. Recipes the app can't run (newer schema) are hidden from Discover; if already installed they show "Update the app to use this" in My recipes.

### Screens

```
┌ Desk: Deep Work on · ends 11:30   [End now] ┐   ← only while a mode is on
│ Discover                                    │
│ ┌──────────────────────────────┐            │
│ │ Bed: Sleep & Morning          │            │
│ │ Used by 320 people this week  │            │
│ │ 1,240 installs                │            │
│ └──────────────────────────────┘            │
│ ...                                         │
├─────────────────────────────────────────────┤
│   Discover          My recipes              │
└─────────────────────────────────────────────┘
```

- **New-recipe boost (Vineet, 2026-11-15):** recipes published in the last 14 days are placed in the top 3 of Discover (newest first) regardless of stats, so they get a chance to collect usage; after 14 days they're ranked normally. Needs a `published_at` column on `recipes`.
- **Discover:** other cards sorted by `weekly_active` desc, then `installs` desc, then `sort_hint`. Recipes with `weekly_active < 5` show "New" instead of small numbers (avoids "Used by 1 person"). No stats available (offline/never fetched) → bundled order, no numbers. When everything is installed: "You've installed every recipe — more coming soon."
- **Recipe detail (not installed):** description, placement hint, stats, **Install** → settings (sliders) → apps to block (installed apps only, as now) → just-in-time permissions → **Write to a tag** (or "Skip for now" — recipe is still installed with no tag yet) → lands on its My recipes entry.
- **My recipes:** installed recipes (installed = recipe has been installed on this phone, with or without tags). Each shows linked tags and whether a mode is on. **Edit** opens: settings, apps to block, tags list (rename, rewrite, remove), **Write another tag**, **Uninstall** (removes its tags' links; disabled while its mode is on).
- **Unknown tag tapped:** "Set up this tag" picker listing all usable recipes (installed first), then the same settings flow without writing.
- Active-mode strip: one row per active mode; End now → existing friction gate.

## Constraints

- Supabase project must be created by Vineet (free tier). Project URL and anon key ship in the app (safe: clients can only read published recipes and aggregates, and call the RPC). The database password / access token for migrations stays on the build VM, never in the repo or CI.
- Supabase's API gateway logs request IPs for a limited time outside our control; our tables store none. The Play Store "Data safety" form must declare anonymous app-activity collection.
- Stats lag up to ~27 h (daily send + 3-hourly refresh). Acceptable.

## Calls made after builder review (Claude's calls — Vineet to check)

- **Installed recipes** become their own set in the saved app state (`installedRecipes`), separate from tags. On upgrade, every recipe that already has a tag counts as installed. Picking a recipe in "Set up this tag" installs it. Uninstalling removes its tag links; those tags then lead to "Set up this tag" if tapped.
- **What counts as a use (a "tap")**: any tap on a linked tag that reaches its recipe — turning on, turning off, a one-shot run, or a tap refused by strict mode. Auto-off and End now are not taps. The tap log gains the recipe ID; entries from before this change aren't reported.
- **Days are the phone's local date.** The server accepts dates from 14 days ago up to tomorrow and ignores others; the 7-day window uses server dates. Today's partial day is sent the next day.
- **Abuse:** accepted for now. A script could mint device IDs and inflate counts; the RPC limits what one call can do but can't stop that. If it happens, add a server-side rate limit per IP or move the RPC behind an edge function. Worth revisiting before any public launch push.
- **Small numbers:** each stat line is shown only when it is ≥ 5; if both are hidden the card shows "New".
- **Catalog source:** the first migration loads every bundled recipe as published, so all are counted. When online, the app shows the published remote catalog; the bundled file is only the offline fallback.
- **Networking:** plain HTTPS calls to Supabase's REST/RPC endpoints from the Android app (no Supabase SDK). Request/response models, the report builder and the Discover sort live in the shared Kotlin core so iOS reuses them; only the HTTP call and scheduling are per-platform.
- **End-mode UI** (friction gate) moves unchanged into the active-mode strip.

## Left to the builder

- Compose structure for the nav bar and the shared active-mode strip.
- Local cache format and WorkManager constraints.
- Migration file layout under `backend/supabase/`.
- Exact card styling (follow the existing theme).
