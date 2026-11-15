# Manual Start Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Start (or run) an installed recipe from the app without its tag, through the same engine path as a tag tap.

**Architecture:** Core gains recipe-level mode keys (`ModeEngine.modeKey`), an `AlreadyOn` result, `AppState.recipeParams`, `manualTag(...)` and a tap-log `source`. The Android `TapHandler` gains `startManually(context, recipeId)` sharing the tag-tap result handling; UI adds the buttons and recipe-level "on" checks.

**Tech Stack:** Kotlin Multiplatform core (kotlin.test), Android Compose, Maestro on the CI emulator.

**Spec:** `docs/superpowers/specs/2026-11-15-manual-start-design.md`

## Global Constraints

- One engine path: manual start calls `ModeEngine.onTap`; no duplicated rule/params/strict logic.
- Manual stop only via the existing friction gate; strict respected.
- Tap-log `source` is `"tag"` or `"app"`; never added to usage events.
- State stays `stateVersion = 2` (new fields are additive with defaults).

## Review Focus

1. Tag tapped while the same recipe is on under another key → ends that mode (or strict-refused), never a second mode (Task 1 test `tapActsOnRecipesRunningMode`).
2. Start now pressed twice / from a stale screen → `AlreadyOn`, state unchanged (Task 1 test `manualStartWhileOnIsRefused`).
3. Tag-less install keeps the settings chosen at install (Task 1 test `manualTagUsesRecipeParams`).
4. Labels for an `app:` mode in the strip, notification, overlay and gate show the recipe title, not an id (Task 2 Maestro asserts "End Desk: Deep Work early?").
5. Auto-off of an `app:` mode runs end actions with the recipe's params (Task 2 Maestro auto-off wait).

---

### Task 1: Core — recipe-level keys, manual tag, tap source

**Files:** Modify `core/.../mode/ModeEngine.kt`, `core/.../store/StateStore.kt`; Test `core/.../mode/ModeEngineTest.kt`, `core/.../store/StateCodecTest.kt`

**Interfaces (produces):**
- `TapResult.AlreadyOn` (data object)
- `ModeEngine.modeKey(active: Map<String, ActiveMode>, tag: InstalledTag, nowMs: Long): String` — `tag.tagId` if that mode is live, else the key of a live mode with the same `recipeId`, else `tag.tagId`. `onTap` uses it.
- `ModeEngine.startManually(active, tag: InstalledTag, recipe: Recipe?, now: Instant, timeZone, groups): TapResult` — `AlreadyOn` if a live mode for `recipe.id` exists, else `onTap(...)`.
- `AppState.recipeParams: Map<String, Map<String, Int>> = emptyMap()`
- `fun AppState.manualTag(recipeId: String, title: String): InstalledTag` — first tag of the recipe in insertion order, else `InstalledTag("app:$recipeId", recipeId, title, recipeParams[recipeId].orEmpty())`.
- `fun AppState.paramsFor(key: String, recipeId: String): Map<String, Int>` — `tags[key]?.params ?: recipeParams[recipeId].orEmpty()`.
- `TapLogEntry.source: String = "tag"`.

- [ ] Write failing tests: manual start of tag-less desk → `Started` keyed `app:desk`; again → `AlreadyOn`; tap of desk tag t1 while `app:desk` on → `Ended` removing `app:desk`; strict on `app:desk` → tag tap `StrictRefused`; two tags t1,t2 of desk: t1 on, tap t2 → `Ended`; gym manual → `Ran`; `manualTag` picks first-inserted tag, else `app:` with recipeParams; old state JSON without `recipeParams`/`source` decodes.
- [ ] `./gradlew :core:jvmTest` — FAIL. Implement. PASS. Commit.

### Task 2: Android — Start now / Run now

**Files:** Modify `TapHandler.kt`, `ui/MyRecipesScreen.kt`, `ui/RecipeEditScreen.kt`, `ui/InstallScreen.kt`, `ui/TagDetailScreen.kt`, `ui/ActiveModeStrip.kt`, `ui/GateScreen.kt`, `block/BlockService.kt`, `block/ModeExpiry.kt`, `HabitTagsApp.kt` (`modeLabel(mode)`), debug `DebugLinkActivity.kt` (`expire-soon` accepts `app:<id>`); Create `maestro/95_manual_start.yaml`; Modify `scripts/e2e.sh`.

- [ ] `TapHandler.startManually(context, recipeId)`; shared result handling keyed by `modeKey`; log `source = "app"`; "Already on" toast.
- [ ] Buttons on cards and Edit (hidden while recipe on; "Run now" for one-shot); recipe-level "on" checks; sliders without tags write `recipeParams`; Install "Skip for now" saves params; Uninstall clears `recipeParams`.
- [ ] Labels via `HabitTagsApp.modeLabel` (tag label, else recipe title).
- [ ] Maestro 95: Discover → Desk → Install → Skip for now → My recipes → Start now → "On now" visible, "Start now" not visible → End now → "End Desk: Deep Work early?" → back → `expire-soon/app:desk-deep-work` → wait ≤ 120 s → "On now" gone.
- [ ] Push; CI green. Commit.

## Deviations

- Added during self-review: a recipe installed with "Skip for now" may never have been granted its permissions, so **Start now** checks them first — on My recipes it opens the recipe's Edit screen, where the permission cards show and Start now stays disabled until they're granted.
