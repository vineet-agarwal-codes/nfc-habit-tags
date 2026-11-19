# Mise Android Launch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the working tag app into "Mise: Shape your spaces", a designed Android app ready for a Google Play closed test.

**Architecture:** Restyle in place (spec approach A). Compose screens in `android/app/src/main/kotlin/com/habittags/app/ui/` get a new theme, type and layouts. Navigation keeps its NavHost: tab routes `discover` and `my` become `discover` and `home`. Space name, colour and place are optional recipe fields plus per-install state in the KMP core (`AppState`). The block service gains a reach counter. Release signing, the privacy page and the store assets are added around the app.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (Material 3 as a base, themed), KMP `:core` with kotlin.test, Supabase (Management API for migrations), GitHub Actions (unit tests and APK on every push; the emulator suite only with `[e2e]`), GitHub Pages.

**Spec:** `docs/superpowers/specs/2026-11-19-mise-android-launch-design.md`

## Global Constraints

- App label "Mise"; Play title "Mise: Shape your spaces"; applicationId is **`app.mise`** (Vineet, 2026-11-19; final after the first Play upload; Kotlin namespace stays `com.habittags.app`; new tags carry an `app.mise` application record, the external type stays `com.habittags.app:tag` so older tags keep working); `versionName = "1.0.0"`.
- Vocabulary in all copy: **space** (a place running a recipe), **recipe**, "Add to <Space>", "Start now", "End early". Sentence case. No tiny uppercase labels.
- Colour is 80-15-5. Base `#F5F5F2` light and `#151716` dark. Ink near-black and near-white. One surface tone step. Accent tomato `#E5482F`, adjusted only for AA contrast, used on the primary action, the mark and live indicators.
- Six space colours, assigned automatically. Desk is tomato, bed is deep blue, workout is green, then three more. Each must pass AA for text on its own field in light and dark.
- No gradients, glass, shadows at rest (only on sheets and dialogs), mascots, streaks, emoji or purple. Surfaces are flat and separated by a tone step.
- Fonts are bundled in `res/font`, never downloaded. One family.
- Every motion has a reduced-motion fallback: check `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and skip it.
- CI: every push runs `:core:jvmTest :app:testDebugUnitTest :app:assembleDebug`. The emulator suite runs only with `[e2e]` in the commit message. Use `[e2e]` only on tasks marked **[e2e]** below, and batch commits before pushing.
- Release builds must contain no Zone lab code and no location permissions. Secrets (upload key, keystore passwords) are never committed.
- Public contact: vineet140502@gmail.com. The developer account is in Vineet's mother's name.

## Review Focus

1. An existing user upgrading with installed recipes and tags lands on Home with every space named and coloured, and with an active mode still running. Covered by the Task 3 migration tests.
2. Two spaces would get the same colour. Each new space takes the first unused colour, and wraps around after six. Covered by Task 3 table tests.
3. A recipe without `space` or `place` fields, as in old cached catalogs, still shows. Its name comes from the title prefix and it goes under "More recipes". Covered by Tasks 3 and 5.
4. Dark mode with every space colour keeps the running-screen text readable. Covered by the Task 1 contrast unit test over the palette.
5. The in-app reader on Home and a tag tapped from outside the app produce the same result, and there are no double starts. Covered by the Task 4 instrumented test.

---

## File Structure (new or substantially changed)

```
android/app/src/main/res/font/                 bundled family (regular, medium, bold)
android/app/src/main/res/mipmap-anydpi-v26/    adaptive + monochrome icon (vector)
android/app/src/main/res/drawable/ic_mise_*.xml   mark foreground/monochrome
ui/theme/MiseTheme.kt, ui/theme/Palette.kt, ui/theme/Type.kt   (replace ui/Theme.kt)
ui/components/ (SpaceCard, PrimaryButton, SettingRow, ScanTarget, EmptyState, Skeleton)
ui/HomeScreen.kt          (replaces MyRecipesScreen.kt)
ui/DiscoverScreen.kt      (grouped)
ui/RecipeScreen.kt, ui/AddFlow.kt   (replaces InstallScreen.kt; reuses TagWriteFlow)
ui/RunningScreen.kt       (new; ActiveModeStrip becomes the running SpaceCard)
ui/SpaceSettingsScreen.kt (replaces RecipeEditScreen.kt + TagDetailScreen.kt entry)
ui/OnboardingScreen.kt
block/BlockOverlay.kt     (restyle + reach count)
core/.../space/Spaces.kt  (names, colours, places — pure logic)
core/.../store/StateStore.kt  (AppState v3)
backend/supabase/migrations/0008_mise_space_place.sql
site/ (in vineet-agarwal-codes.github.io repo): index.html, privacy.html
store/ listing.md, data-safety.md, declarations.md, screenshots/, feature-graphic.png
```

---

### Task 1: Theme, palette, type (identity lands first)

**Files:** Create `ui/theme/Palette.kt`, `ui/theme/Type.kt`, `ui/theme/MiseTheme.kt`, `res/font/*`. Delete `ui/Theme.kt`. Replace every `HabitTheme` call with `MiseTheme`, including the debug Zone lab, which also keeps its `Surface`. Test: `android/app/src/test/.../theme/PaletteContrastTest.kt` (JVM unit test).

**Interfaces:**
- Produces: `enum class SpaceColour(val light: Color, val dark: Color, val onField: Color) { Tomato, DeepBlue, Green, Ochre, Teal, Plum }`. Names are final; the builder tunes values for contrast. Also `object MisePalette { base; baseDark; ink; inkDark; surface; surfaceDark; accent }`, `val MiseTypography: Typography`, and `@Composable fun MiseTheme(content: @Composable () -> Unit)`.

- [ ] Render a sample Home card and Running screen in **Bricolage Grotesque** and in **Instrument Sans**. Use a Compose `@Preview` captured with Paparazzi, or one `[e2e]` emulator screenshot run. Look at both and pick one. Record the choice and the reason in the plan's notes. Bundle regular, medium and bold weights under the SIL OFL, with the licence file in `res/raw/ofl.txt`.
- [ ] Write `PaletteContrastTest`. For each `SpaceColour` in light and dark, check `contrast(onField, field) >= 4.5`. Check `contrast(ink, base) >= 7`. Check `contrast(accent, base) >= 3` (large text and UI). Run `./gradlew :app:testDebugUnitTest` and confirm it fails.
- [ ] Implement the theme. The type scale uses three working sizes: display (about 44–56sp, tracking −0.02em), title (about 22sp) and body (16sp). Labels are 14sp in sentence case.
- [ ] Run the tests. Push without e2e. Commit `feat(theme): Mise palette, type and theme`.

### Task 2: Mark and launcher icon

**Files:** `res/drawable/ic_mise_foreground.xml`, `ic_mise_monochrome.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`. Also `res/values/strings.xml`, where `app_name` becomes "Mise". Keep the notification small icon in line with the mark.

- [ ] Draw the mark as a vector: a lowercase "m" whose last stroke ends in a notched tag edge, tomato on base. Supply a monochrome layer for themed icons. Export a 512×512 PNG to `store/icon-512.png` for Play.
- [ ] Check it on the emulator at 48dp, as a themed icon, and on a dark wallpaper. This is the screenshot batch for the next `[e2e]` run. Vineet approves the mark from that screenshot before Task 13.
- [ ] Commit `feat(brand): Mise mark and adaptive icon`.

### Task 3: Spaces in the core (names, colours, places, migration)

**Files:** Create `android/core/src/commonMain/kotlin/com/habittags/core/space/Spaces.kt`. Modify `recipe/Recipe.kt` and `store/StateStore.kt` (`STATE_VERSION = 3`). Test: `commonTest/.../space/SpacesTest.kt`, and extend `StateStoreTest`.

**Interfaces:**
- `Recipe` gains `val space: SpaceHint? = null` and `val place: String? = null`, with `@Serializable data class SpaceHint(val name: String, val colour: String? = null)`.
- `@Serializable data class Space(val recipeId: String, val name: String, val colour: String)`. `AppState` gains `spaces: Map<String, Space> = emptyMap()`, keyed by recipeId.
- `object Spaces`:
  - `fun defaultName(recipe: Recipe): String` returns `space.name`, else the title before ":", else the title.
  - `fun nextColour(used: Collection<String>, hint: String?): String` returns the hint if it isn't already used, else the first unused colour of the six, else wraps around by count.
  - `fun install(state: AppState, recipe: Recipe): AppState` adds the recipe to `installedRecipes` and adds a `Space`.
  - `fun uninstall(state: AppState, recipeId: String): AppState` removes it.
- The v2→v3 migration creates a `Space` for every recipe in `installedRecipes`. The caller passes recipes in, as `migrate(state, recipeLookup)`. Where the catalog isn't loaded yet, the name is recipeId-based and repaired the first time the catalog loads, using `Spaces.repairNames(state, recipes)`.

- [ ] Table tests:
  - `defaultName` for "Desk: Deep Work" returns "Desk".
  - `defaultName` with `space.name = "Bed"` returns "Bed".
  - `defaultName` for "Morning" returns "Morning".
  - `nextColour(["tomato"], "tomato")` returns "deep_blue".
  - `nextColour` with all six used returns the 7th wrapped to "tomato".
  - The migration of a v2 state with 2 installed recipes, 1 active mode and tags keeps all three, plus 2 spaces with distinct colours.
  - `repairNames` fixes the placeholder names.
- [ ] Watch the tests fail. Implement. Watch them pass with `./gradlew :core:jvmTest`. Wire `Spaces.install` and `Spaces.uninstall` into `AppEngine` and the existing app install and uninstall paths, so behaviour is unchanged and only the space is added. Commit.

### Task 4: Home — your spaces + in-app reader **[e2e]**

**Files:** Create `ui/HomeScreen.kt`, `ui/components/SpaceCard.kt` and `ui/components/ScanTarget.kt`. Delete `MyRecipesScreen.kt`. In `AppNav.kt`, rename the tab route `my` to `home`, make Home the start destination, and title the tabs "Home" and "Discover". Running state replaces `ActiveModeStrip` on Home, but the strip remains on Discover in a compact form. Test: an instrumented `HomeReaderTest`, and Maestro flows updated for the new copy.

**Interfaces:**
- `@Composable fun HomeScreen(onOpenSpace: (recipeId: String) -> Unit, onOpenRunning: (tagId: String) -> Unit, onBrowse: () -> Unit)`.
- Space card state is computed in core as `fun spaceStatus(state: AppState, recipe: Recipe, now: Instant, tz: TimeZone): SpaceStatus`, with `sealed interface SpaceStatus { Running(endsAt, remainingMs, strictMs); StartsAt(time); TapToRun; Idle(lastUsedMs?) }`. Unit-tested in core: Bed before 21:00 is `StartsAt("21:00")`, a one-shot recipe is `TapToRun`, an active mode is `Running`.
- In-app reader: Home enables `NfcAdapter.enableReaderMode` (NFC-A, skip NDEF check off) on resume and disables it on pause. It calls `TapPayload.parse` and then `TapHandler.handle(context, tagId, "tag")`, the same entry point the external tap uses. While Home is resumed, a tap from outside the app is consumed by reader mode, so there's no double handling.

- [ ] Write `SpacesStatusTest` (core) and watch it fail. Implement `spaceStatus`.
- [ ] Build `SpaceCard`. Idle cards use the pale tint of the space colour, with the space name in the title size, the recipe name, the status and the tag count. A running card fills the whole card with the field colour and shows the countdown at display size, "ends HH:mm", "End early", and the reach count if it's above 0. "Start now" or "Run now" stays on the card. Add the empty state "Set up your first space" with a primary action that opens Discover.
- [ ] Add a `ScanTarget` at the bottom: "Hold your phone to a tag", pulsing while reader mode is on. If NFC is off, show "Turn on NFC" with a link to settings. If the phone has no NFC, show "This phone can't read tags; start spaces from the app".
- [ ] Write `HomeReaderTest`. Inject a fake tag dispatch through the debug `habittags-debug://tap/<id>` path, check the running card appears, and check the tap log gains exactly one entry.
- [ ] Update the Maestro flows that reference "My recipes", "On now" or "Start now" to the new copy. Commit with `[e2e]`.

### Task 5: Discover grouped by place

**Files:** Modify `ui/DiscoverScreen.kt` and `core/.../catalog/Discover.kt`. Test: extend `DiscoverTest`.

**Interfaces:**
- `fun discoverSections(snapshot: CatalogSnapshot, installed: Set<String>, now: Instant): List<Pair<String, List<Recipe>>>` returns ordered sections: "New recipes" (the existing boost, if any), then the places in this order — desk "For your desk", bed "For bedtime", leaving "For leaving home", workout "For workouts" — then "More recipes". Each section is sorted by the existing popularity order. Empty sections are dropped.
- The recipe card shows the title in the title size, one line of description, the duration from `autoOffMinutes` (resolved with defaults), usage when it's at least 5 (the existing `statLabel`), and one primary button, "Add to <Space name>".

- [ ] Table test: two desk recipes, one bed recipe, one with no place, and one new recipe should produce the sections ["New recipes", "For your desk", "For bedtime", "More recipes"] in that order. Implement, then build the UI. Commit.

### Task 6: Recipe page + 3-step add flow (auto colour)

**Files:** Modify `ui/RecipeScreen.kt`. Create `ui/AddFlow.kt`, which replaces `InstallScreen.kt` and reuses `TagWriteFlow.kt` and the permission helpers. Update the `AppNav` route `install/{recipeId}` to `add/{recipeId}`.

- [ ] Recipe page: the name at display size, the placement hint, what it does in plain words (generated from actions: "Blocks social and video apps, turns on Do Not Disturb, ends after 90 min"), usage, and "Add to <Space>".
- [ ] Add flow, steps 1/3 to 3/3 shown as progress, with no index labels:
  1. **Settings.** Defaults are preset. Duration sliders and time pickers reuse the existing components restyled. The space name is editable inline. There is no colour step.
  2. **Permissions.** Show only what this recipe needs. Each is a card with one sentence and a button that opens the system screen. Skip the whole step if everything is already granted.
  3. **Tag.** "Hold your phone to a blank tag", or "Skip, start from the app".
- [ ] Finishing calls `Spaces.install`, which picks the colour automatically, and lands on Home with the new card highlighted once.
- [ ] Update the Maestro `10_first_run.yaml` copy. This is batched into Task 7's `[e2e]` push. Commit without pushing, or push without `[e2e]`.

### Task 7: Running screen, reach count, block overlay **[e2e]**

**Files:** Create `ui/RunningScreen.kt` (route `running/{tagId}`). Modify `block/BlockService.kt` and `block/BlockOverlay.kt`, `core/.../mode/ModeEngine.kt` (`ActiveMode.reaches: Int = 0`) and `core/.../usage/UsageEvents.kt`. Test: core `ReachTest` and the Maestro `30_block.yaml` update.

**Interfaces:**
- `fun ModeEngine.recordReach(active: Map<String, ActiveMode>, blockedPackage: String): Map<String, ActiveMode>` increments `reaches` on every running mode whose `blockedPackages` contains the package.
- `BlockService` calls it only when the foreground package changes to a blocked one (the previous foreground differed). It is never called per poll.
- `fun UsageEvents.blockReach(recipeId: String, count: Int, day: LocalDate, tz: TimeZone): UsageEvent` is type `block_reach` v1 with payload `{recipe, count}`. When a mode ends, its reaches are emitted as one event (the count only, no app names) through the existing outbox.

- [ ] Core tests:
  - A reach on a package in two modes increments both.
  - A package not blocked changes nothing.
  - When a mode ends with reaches of 3, it queues one `block_reach` with a count of 3.
  - When a mode ends with 0 reaches, it queues nothing.
- [ ] Running screen: the whole screen takes the space's field colour. It shows the space name, the countdown at display size, "ends HH:mm", the icons of the paused apps (up to 8, then "+N"), "You reached for blocked apps N times" (hidden at 0), the strict remaining time, and "End early" (which opens the gate route). Opened from the running card. Status bar colour matches.
- [ ] Block overlay: the same field colour and type. It shows "<Space> is on", "ends HH:mm" and the reach count, with the single button "Go to home screen". No shaming words.
- [ ] Update the Maestro block flow to assert "Desk is on". Commit with `[e2e]` (this covers Tasks 6 and 7).

### Task 8: End early (gate) restyle

**Files:** Modify `ui/GateScreen.kt`. Keep the behaviour and the `GateEditText` rules as they are.

- [ ] Restyle the header in the space colour: "End <Space> early?". Use the new type for the passage card, the word counter and the input box. Change the copy "Copy the text below…" to "Type the text below to end <Space> now. Or tap your tag." The existing Espresso `GateTextFieldTest` must stay green. Commit, batched with Task 9.

### Task 9: Space settings

**Files:** Create `ui/SpaceSettingsScreen.kt` (route `space/{recipeId}`). It absorbs `RecipeEditScreen` and links to the existing tag detail screen, restyled.

- [ ] Sections:
  - Name (inline edit).
  - Colour (the six swatches; optional, already set).
  - Recipe settings.
  - Apps to block (the existing `GroupEditor`, restyled).
  - Tags (write another, rewrite, remove).
  - "Remove space", disabled while the space is running, with the existing reason text.
- [ ] Core: `fun Spaces.rename(state, recipeId, name)` and `fun Spaces.recolour(state, recipeId, colour)`, with tests. A blank name falls back to `defaultName`.
- [ ] Commit and push without e2e.

### Task 10: Onboarding, states, dark mode, motion **[e2e]**

**Files:** Create `ui/OnboardingScreen.kt`, shown when `AppState.onboarded == false` (new field, default false; migrated users are set to true), and `ui/components/EmptyState.kt` and `Skeleton.kt`. Touch every screen for dark mode.

- [ ] Onboarding has 3 screens: "Shape your spaces"; "Tags make it a tap" (buy any NTAG213/215/216 sticker, or start from the app); and "Set up your first space", which opens Discover scrolled to "For your desk". It asks for no permissions.
- [ ] States:
  - Loading cards show as tone skeletons.
  - Offline shows a quiet line, "Showing saved recipes".
  - Errors are in plain words with "Try again".
  - Banners cover NFC being off or missing.
- [ ] Motion, each with the reduced-motion fallback:
  - The card fill expands from the tap point when a mode starts (about 300ms).
  - The countdown digits roll.
  - The scan target pulses.
  - Screen entries rise and fade, staggered by about 60ms.
- [ ] Maestro: a new `01_onboarding.yaml` (fresh install shows the 3 screens, then lands on Discover). Push with `[e2e]` and capture dark-mode screenshots for the Task 13 review.

### Task 11: Catalog fields in production

**Files:** Create `backend/supabase/migrations/0008_mise_space_place.sql`. Modify `android/app/src/main/assets/catalog.json`, the debug fixture `recipes.json` and `seed/catalog_seed.sql`.

- [ ] Add `space` and `place` to each seed recipe:
  - desk-deep-work: Desk, tomato, desk
  - bed-sleep: Bed, deep_blue, bed
  - gym-bag: Gym bag, green, workout
  - front-door: Front door, leaving
  Bump each `version`. Set `updated_at = now()` so Realtime and refresh pick it up. `schemaVersion` doesn't change.
- [ ] Add SQL test 07: every published recipe has `doc ? 'place'`. Apply it via the Management API with the token from `~/.config/habit-tags/supabase.env`, never printed. Run SQL tests 01–07.
- [ ] Check the existing core `SeedCatalogTest` and validate pass with the new fields. Commit.

### Task 12: Release build, signing, manifest check

**Files:** Modify `android/app/build.gradle.kts` (release `signingConfig` read from env vars `MISE_UPLOAD_STORE_FILE`, `MISE_UPLOAD_STORE_PASSWORD`, `MISE_UPLOAD_KEY_ALIAS`, `MISE_UPLOAD_KEY_PASSWORD`; unsigned when absent; `versionName "1.0.0"`; minify off) and `.github/workflows/android.yml` (add a `release` job on `workflow_dispatch` only: decode the base64 keystore from secrets, run `:app:bundleRelease`, run the manifest check, upload the AAB artifact `mise-release-aab`). Create `android/scripts/check-release-manifest.sh`.

- [ ] Generate the upload key on the VM:
  `keytool -genkeypair -keystore ~/.config/habit-tags/mise-upload.jks -alias mise-upload -keyalg RSA -keysize 4096 -validity 9125`
  Use passwords from `openssl rand`, stored in `~/.config/habit-tags/release.env` with permissions 600. Add `*.jks` and `release.env` to the ignore patterns. Vineet keeps a backup copy offline.
- [ ] `check-release-manifest.sh <aab>` extracts the merged manifest with `bundletool dump manifest` and fails if it finds any of these: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `NEARBY_WIFI_DEVICES`, `ACTIVITY_RECOGNITION`, `ZoneTrackingService`, `ZoneLabActivity`, `DebugTapReceiver`, `DebugLinkActivity`, or `QUERY_ALL_PACKAGES`. It also fails if `FOREGROUND_SERVICE_SPECIAL_USE` is missing its `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property.
- [ ] Add the GitHub secrets with `gh secret set`. Values are read from `release.env` and the keystore base64. Never echo them.
- [ ] Run the `release` job once with `gh workflow run`. Check that it produces a signed AAB (`jarsigner -verify`) and that the manifest check passes. Commit.

### Task 13: Privacy policy, listing, declarations, screenshots

**Files:**
- In the `vineet-agarwal-codes.github.io` repo: `index.html` (a short Mise page: what it is and a link to the policy) and `privacy.html`.
- In the app repo: `store/listing.md`, `store/data-safety.md`, `store/declarations.md`, `store/screenshots/*.png` (1080×2400) and `store/feature-graphic.png` (1024×500). Add a "Privacy policy" link in the app at the Discover footer and in Space settings.

- [ ] **`privacy.html`** covers:
  - Who: developer name as on Play (Vineet's mother's name, filled in at upload), contact vineet140502@gmail.com and Vineet's mobile.
  - What's collected:
    - A random device ID.
    - Installed recipe IDs.
    - Daily tap counts per recipe with the time zone.
    - Block reach counts.
    - No app lists, no names, no location, and no account in 1.0.
  - Processor: Supabase (Mumbai region).
  - Retention: 90 days for raw events.
  - Deletion: email us your device ID, shown in Space settings → About. Add that line in the app.
  - Children: not directed to children under 13.
  - Last updated.
- [ ] **`listing.md`**: a short description (80 characters or fewer) such as "Tap a tag at your desk, bed or door. Your phone switches into the right mode.", plus a full description (4000 characters or fewer), category Productivity and tags.
- [ ] **`data-safety.md`**:
  - Data collected: "App activity → App interactions" and "Device or other IDs".
  - Not shared.
  - Encrypted in transit: yes.
  - Deletion request: yes, by email.
  - Collection is not optional. The anonymous stats are required for Discover popularity; say so honestly.
- [ ] **`declarations.md`**:
  - Usage access: shown to detect when a blocked app opens during a mode the user started.
  - Overlay: displays the "<Space> is on" screen over a blocked app.
  - Foreground service `specialUse`: subtype text "Keeps a user-started focus mode running to block chosen apps until it ends", plus a script for a 30-second screen recording.
  - DND access.
  - Notifications.
- [ ] **Screenshots**: a Maestro `store_screenshots.yaml`, run once with `[e2e]` on a release-like debug build with seed data and the fake tag. Capture Home (Desk running), Home (idle spaces), Discover, Recipe page, Add flow step 3, Running screen, Block overlay and End early, in light mode plus one dark. Download the artifact into `store/screenshots/`.
- [ ] **Feature graphic**: the mark plus "Shape your spaces" on base, made with the same font. Render it as SVG to PNG with the system Chromium (Playwright).
- [ ] Commit and push both repos.

### Task 14: Final review and hand-off

- [ ] Dispatch one fresh reviewer (model opus) over the diff since the plan commit. Fix the confirmed findings.
- [ ] Run the full `[e2e]` run once on the final commit, then the `release` job. Both must be green.
- [ ] Write `store/README.md`: the step-by-step Play Console checklist for Vineet and his mother.
  1. Create the account in her name: ID verification, $25, her payments profile.
  2. Invite Vineet as admin.
  3. Create the app "Mise: Shape your spaces", default language English (India), free.
  4. Enrol in Play App Signing and upload the AAB.
  5. Fill the listing, data safety, content rating, target audience and declarations from `store/`.
  6. Set up a closed testing track with 12 or more tester emails, and send the opt-in link.
  7. After 14 days, apply for production access.

---

## Notes (filled during execution)

- Font chosen: **Bricolage Grotesque** (OFL), bundled as three static cuts made with fontTools (regular wght 400/opsz 14, medium 500/24, bold 700/72). Picked without the two-font emulator comparison, to save CI runs: its character carries the display sizes the identity leans on; Instrument Sans read as a generic grotesque.
- Mark approved by Vineet (2026-11-19) as is: `docs/brand/mise-mark.png`, store icon `store/icon-512.png`.
- Space colour final values (field / text on field / light tint / dark tint): tomato #C93A22/white/#EFDBD5/#4B221A, deep_blue #23408E/white/#D8DCE4/#19233A, green #1D6E41/white/#D7E2D9/#173123, ochre #E2A93B/ink/#F2EAD8/#524321, teal #0E6E72/white/#D5E2E0/#133132, plum #8A2C50/white/#E6D9DB/#381D27. Accent #C93A22 on light, #FF7A5C as text on dark. All pairs ≥ 4.5:1 (PaletteContrastTest).
- applicationId changed to `app.mise` (Vineet). New tags get an `app.mise` application record; the `com.habittags.app:tag` record type is kept.
- Deviations:
  - Reaches are reported as a daily `block_reach` count per recipe (like `tap_count`) instead of one event when a mode ends; simpler and survives crashes.
  - Spaces are kept complete by `Spaces.ensure` on every state update (no separate v2→v3 name repair).
  - Release signing: the auto-mode safety check refused uploading the upload key to GitHub secrets, so CI builds an **unsigned** bundle (`release` workflow, manual) and `android/scripts/sign-release.sh` signs it on the VM. The key never leaves the VM.
  - Space settings is reachable from Home (idle card) and from the running screen ("<Space> settings").
  - Store screenshots come from the e2e run (`store_*.png` in the `e2e-report` artifact) rather than a separate flow.
