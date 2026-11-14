# Android MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that lets a user pick a curated recipe, write it to any blank NFC tag, and tap the tag to switch the phone into that mode (block app groups, DND, timer, checklist, open app, log), with adjustable settings, strict minimums, auto-off and a typing gate to end a mode early.

**Architecture:** Gradle project under `android/` with two modules. `:core` is pure Kotlin/JVM and holds every rule that isn't Android-API-bound (recipe parsing, param binding, time windows, mode state machine, catalog merge, tag payload, friction-gate validator, state persistence) and is TDD'd locally. `:app` is the Android shell (NFC, foreground blocking service, DND, Compose UI). This VM is arm64 with no Android SDK, so `:app` is compiled and its tests run on GitHub Actions; Vineet sideloads the APK artifact and does the device checks. The catalog comes from a bundled `catalog.json` behind a `CatalogSource` interface; the Supabase source and optional login/sync are a separate, later plan.

**Tech Stack:** Kotlin 2.0, JDK 17, Gradle 8.10 (Kotlin DSL), AGP 8.7, kotlinx.serialization 1.7, JUnit 5 (core), Jetpack Compose (Material 3), GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-14-nfc-habit-tags-design.md`

## Global Constraints

- Package / applicationId: `com.habittags.app` (Claude's call; must be final before first Play upload).
- minSdk 26, targetSdk 35, compileSdk 35.
- No `QUERY_ALL_PACKAGES`, no AccessibilityService, no `SCHEDULE_EXACT_ALARM`.
- Blocking = UsageStats foreground-app polling (1 s) + overlay window; foreground service type `specialUse`.
- App works fully offline and without an account. Nothing about the user's apps, groups or usage leaves the device.
- Tags hold only the tagId. Settings/recipe changes never require rewriting a tag.
- tagId: 16 random bytes, base64url without padding (22 chars).
- NDEF layout: record 1 = NFC Forum external type `com.habittags.app:tag` with payload = tagId as ASCII; record 2 = Android Application Record `com.habittags.app`.
- Friction gate: ~100-word passage, paste disabled, any single edit that inserts more than 3 characters is rejected.
- Auto-off precision: within ~1 minute is acceptable (inexact).
- There is always an escape: the friction gate works even during a strict period.
- `local.properties` and signing keys are never committed.

## Review Focus

1. Phone reboots or app is killed while a mode is active → blocks resume after reboot; auto-off still ends the mode at the right time (Task 5 test `expire after restart`, Task 8 BootReceiver device check).
2. Two active modes block the same app group, one ends → that app stays blocked until the other ends (Task 3 table case).
3. Tap on a tagId this phone has never seen → "Set up this tag" flow, not a crash or silent nothing (Task 7 device check, Task 3 `UnknownTag` result).
4. Author updates a recipe and removes or narrows a param the user had set → user value dropped or clamped, never out of range (Task 4 table cases).
5. Tap at a time window boundary or across midnight (`22:00-06:00` at 23:59, 00:00, 05:59, 06:00) → correct rule chosen (Task 2 table cases).

---

## File Structure

```
.github/workflows/android.yml          CI: core tests, app unit tests, debug APK artifact
android/settings.gradle.kts            includes :app only when an Android SDK is present
android/build.gradle.kts, gradle/libs.versions.toml, gradlew
android/core/src/main/kotlin/com/habittags/core/
  recipe/Recipe.kt                     data model (serializable)
  recipe/RecipeParser.kt               parse, schema check, param binding
  rules/TimeWindow.kt                  "HH:MM-HH:MM" parsing + rule selection
  mode/ModeEngine.kt                   tap/expire/gate state machine, block union
  catalog/ParamMerge.kt                merge user params with updated recipe
  tag/TagPayload.kt                    tagId generation + payload validation
  gate/FrictionGate.kt                 passage generator + edit validator
  store/StateStore.kt                  JSON persistence of AppState to a File
android/core/src/main/resources/gate_sentences.txt
android/core/src/test/kotlin/...       one test file per main file
android/app/src/main/
  AndroidManifest.xml
  assets/catalog.json                  seed catalog (4 recipes, 4 app groups)
  kotlin/com/habittags/app/
    HabitTagsApp.kt                    Application: wires StateStore, catalog
    catalog/CatalogSource.kt           interface + BundledCatalogSource
    nfc/TagWriter.kt, nfc/TapActivity.kt
    block/BlockService.kt, block/BlockOverlay.kt, block/BootReceiver.kt
    effects/EffectRunner.kt            DND, timer, checklist, open app, log
    ui/ (MainActivity + screens)
```

---

### Task 1: Project skeleton, CI, and recipe model + parser

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml`, `android/gradle.properties` (`org.gradle.jvmargs=-Xmx1536m`, `org.gradle.workers.max=1`), gradle wrapper, `android/core/build.gradle.kts`, `android/app/build.gradle.kts` (empty Compose app with `MainActivity` showing "Habit Tags"), `.github/workflows/android.yml`, `.gitignore`
- Create: `core/.../recipe/Recipe.kt`, `core/.../recipe/RecipeParser.kt`
- Test: `core/src/test/kotlin/com/habittags/core/recipe/RecipeParserTest.kt`

**Interfaces:**
- Produces:
  - `const val SUPPORTED_SCHEMA_VERSION = 1`
  - `@Serializable data class Catalog(val recipes: List<Recipe>, val appGroups: List<AppGroup>)`
  - `@Serializable data class AppGroup(val id: String, val title: String, val androidPackages: List<String>)`
  - `@Serializable data class Recipe(val id: String, val schemaVersion: Int, val version: Int, val title: String, val description: String, val placementHint: String, val mode: Mode, val params: List<ParamSpec> = emptyList(), val autoOffMinutes: JsonElement? = null, val strictMinutes: JsonElement? = null, val rules: List<Rule>, val endActions: List<Action> = emptyList(), val price: Int? = null)`
  - `enum class Mode { toggle, oneShot }`
  - `@Serializable data class ParamSpec(val key: String, val type: String /* "duration" */, val label: String, val default: Int, val min: Int, val max: Int)`
  - `@Serializable data class Rule(val `when`: When? = null, val actions: List<Action>)`, `@Serializable data class When(val time: String)`
  - `@Serializable sealed interface Action` with `@SerialName` subtypes, discriminator `"type"`: `BlockGroups(groups: List<String>)` = `"blockGroups"`, `Dnd` = `"dnd"`, `Timer(minutes: JsonElement)` = `"timer"`, `Checklist(title: String, items: List<String>)` = `"checklist"`, `OpenApp(androidPackage: String?, uri: String?)` = `"openApp"`, `Log(label: String)` = `"log"`; unknown types decode to `Unsupported(type: String)`.
  - `fun parseCatalog(json: String): Catalog`
  - `fun isUsable(recipe: Recipe): Boolean` = `recipe.schemaVersion <= SUPPORTED_SCHEMA_VERSION && no Unsupported actions`
  - `fun bindInt(value: JsonElement?, spec: List<ParamSpec>, userValues: Map<String, Int>): Int?` — number → itself; string matching `^\{\{(\w+)\}\}$` → user value for that key, else the param default, clamped to [min,max]; null → null; any other string → `IllegalArgumentException`.

- [ ] **Step 1:** Install toolchain locally: `sudo apt-get install -y openjdk-17-jdk-headless`; download Gradle 8.10 distribution to `/tmp`, run `gradle wrapper --gradle-version 8.10` inside `android/`. `settings.gradle.kts` includes `:app` only if `System.getenv("ANDROID_HOME") != null || file("local.properties").exists()`.
- [ ] **Step 2:** Write `RecipeParserTest` as a table-driven `@ParameterizedTest`:

```kotlin
// bindInt cases: (value, userValues, expected)
"90"                     , {}                    -> 90
"\"{{autoOffMinutes}}\"" , {}                    -> 90    // default
"\"{{autoOffMinutes}}\"" , {autoOffMinutes=240}  -> 240
"\"{{autoOffMinutes}}\"" , {autoOffMinutes=9999} -> 480   // clamped to max
"\"{{autoOffMinutes}}\"" , {autoOffMinutes=1}    -> 15    // clamped to min
"\"{{missing}}\""        -> throws IllegalArgumentException
null                     -> null
// spec: autoOffMinutes default 90 min 15 max 480
```
Plus: `parseCatalog` on a fixture with one recipe of each action type round-trips titles and action subtypes; a recipe with `schemaVersion: 2` → `isUsable == false`; an action `{"type":"flashlight"}` decodes to `Unsupported("flashlight")` and `isUsable == false`.
- [ ] **Step 3:** Run `cd android && ./gradlew :core:test` — expect FAIL (unresolved references).
- [ ] **Step 4:** Implement `Recipe.kt` and `RecipeParser.kt` (Json with `ignoreUnknownKeys = true`, `classDiscriminator = "type"`; `Unsupported` via a custom `JsonContentPolymorphicSerializer`).
- [ ] **Step 5:** Run `./gradlew :core:test` — expect PASS.
- [ ] **Step 6:** Write `.github/workflows/android.yml`: on push/PR, ubuntu-latest, `actions/setup-java@v4` (temurin 17), `android-actions/setup-android@v3`, run `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` in `android/`, upload `app/build/outputs/apk/debug/app-debug.apk` as artifact `habit-tags-debug-apk`.
- [ ] **Step 7:** Commit and push; run `gh run watch --exit-status` (in background) — expect success and the artifact present (`gh run view --json artifacts`).

### Task 2: Time windows and rule selection

**Files:**
- Create: `core/.../rules/TimeWindow.kt`
- Test: `core/.../rules/TimeWindowTest.kt`

**Interfaces:**
- Consumes: `Rule`, `When` (Task 1)
- Produces: `fun selectRule(rules: List<Rule>, now: LocalTime): Rule?` — first rule whose `when` is null or whose window contains `now`; window `"HH:MM-HH:MM"` is start-inclusive, end-exclusive, and wraps past midnight when end ≤ start. Malformed window → `IllegalArgumentException` at parse.

- [ ] **Step 1:** Table test, rules = [`22:00-06:00` → A, `06:00-22:00` → B]:

```kotlin
23:59 -> A ; 00:00 -> A ; 05:59 -> A ; 06:00 -> B ; 21:59 -> B ; 22:00 -> A
// rules = [ "09:00-17:00" -> A ] : 08:59 -> null ; 17:00 -> null
// rules = [ "09:00-17:00" -> A, no-when -> B ] : 18:00 -> B
// "25:00-06:00" -> throws
```
- [ ] **Step 2:** `./gradlew :core:test --tests '*TimeWindowTest'` — FAIL.
- [ ] **Step 3:** Implement `TimeWindow.kt`.
- [ ] **Step 4:** Run — PASS.
- [ ] **Step 5:** Commit.

### Task 3: Mode engine

**Files:**
- Create: `core/.../mode/ModeEngine.kt`
- Test: `core/.../mode/ModeEngineTest.kt`

**Interfaces:**
- Consumes: `Recipe`, `Action`, `bindInt` (Task 1), `selectRule` (Task 2)
- Produces:
  - `@Serializable data class InstalledTag(val tagId: String, val recipeId: String, val label: String, val params: Map<String, Int>)`
  - `@Serializable data class ActiveMode(val tagId: String, val recipeId: String, val startedAt: Long /*epoch ms*/, val autoOffAt: Long?, val strictUntil: Long?, val blockedGroups: Set<String>, val dnd: Boolean)`
  - `sealed interface TapResult { UnknownTag; RecipeUnavailable; Started(state, effects: List<Action>); Ended(state, effects: List<Action>); StrictRefused(remainingMs: Long); Ran(effects: List<Action>) }`
  - `object ModeEngine`:
    - `fun onTap(active: Map<String, ActiveMode>, tag: InstalledTag?, recipe: Recipe?, now: ZonedDateTime): TapResult`
    - `fun expire(active: Map<String, ActiveMode>, nowMs: Long, recipes: (String) -> Recipe?): Pair<Map<String, ActiveMode>, List<Action>>` — removes modes past `autoOffAt`, returns their recipes' `endActions`
    - `fun endViaGate(active: Map<String, ActiveMode>, tagId: String): Map<String, ActiveMode>` — ignores strict, runs no endActions
    - `fun blockedPackages(active: Map<String, ActiveMode>, groups: Map<String, Set<String>>): Set<String>` — union
    - `fun dndWanted(active: Map<String, ActiveMode>): Boolean` — any
- Behaviour: toggle + tag not active → select rule at `now.toLocalTime()`; `BlockGroups`/`Dnd` actions become mode state, other actions are returned as effects; `autoOffAt = now + bindInt(autoOffMinutes)`, `strictUntil = now + bindInt(strictMinutes)` when > 0. Toggle + tag active → if `now < strictUntil` → `StrictRefused`, else `Ended` with `endActions`. No rule matches → `Ran(emptyList())`. oneShot → `Ran(all actions of the selected rule)`; BlockGroups/Dnd in oneShot recipes are ignored.

- [ ] **Step 1:** Table tests (fixture: desk recipe toggle, blockGroups[social,video] + dnd, autoOff `{{autoOffMinutes}}` default 90, strict `{{strictMinutes}}` default 0; bed recipe with endActions [Checklist]):

```kotlin
tap unknown tag (tag=null)                      -> UnknownTag
tap with recipe=null                            -> RecipeUnavailable
tap desk at 10:00, params {}                    -> Started, autoOffAt = +90 min, strictUntil = null, blockedGroups {social, video}, dnd true
tap desk again at 10:05                         -> Ended, state empty
desk params {strictMinutes=45}: tap 10:00, tap 10:30 -> StrictRefused(remaining 15 min); tap 10:46 -> Ended
endViaGate during strict                        -> mode removed
expire at started+89min -> unchanged ; at +90min -> removed and bed endActions returned
two modes: desk{social,video} + bed{social}; end desk -> blockedPackages still contains social packages, not video
oneShot gym recipe -> Ran(effects = [OpenApp, Timer, Log]), state unchanged
```
- [ ] **Step 2:** Run — FAIL. **Step 3:** Implement. **Step 4:** Run — PASS. **Step 5:** Commit.

### Task 4: Param merge on recipe update, tag payload

**Files:**
- Create: `core/.../catalog/ParamMerge.kt`, `core/.../tag/TagPayload.kt`
- Test: `ParamMergeTest.kt`, `TagPayloadTest.kt`

**Interfaces:**
- Produces:
  - `fun mergeParams(userValues: Map<String, Int>, specs: List<ParamSpec>): Map<String, Int>` — keep values whose key still exists, clamped to the new min/max; drop removed keys; new keys are absent (defaults apply via `bindInt`).
  - `object TagPayload { const val EXTERNAL_DOMAIN = "com.habittags.app"; const val EXTERNAL_TYPE = "tag"; fun newTagId(random: java.security.SecureRandom = SecureRandom()): String; fun parse(payload: ByteArray): String? }` — `parse` returns the tagId only if it is exactly 22 chars of `[A-Za-z0-9_-]`, else null.

- [ ] **Step 1:** Tests:

```kotlin
mergeParams({a=60,b=10}, specs[a: 15..480])           -> {a=60}        // b removed
mergeParams({a=600}, specs[a: 15..480])               -> {a=480}       // narrowed
mergeParams({}, specs[a, c])                          -> {}
newTagId() x1000 -> all length 22, all match regex, all distinct
parse(newTagId bytes) -> same id ; parse("short") -> null ; parse("x".repeat(22)+"!") -> null
```
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Commit.

### Task 5: Friction gate and state store

**Files:**
- Create: `core/.../gate/FrictionGate.kt`, `core/src/main/resources/gate_sentences.txt` (≥ 80 short, plain sentences about habits and focus, one per line, ASCII only, no digits), `core/.../store/StateStore.kt`
- Test: `FrictionGateTest.kt`, `StateStoreTest.kt`

**Interfaces:**
- Produces:
  - `object FrictionGate { const val TARGET_WORDS = 100; const val MAX_INSERT = 3; fun passage(random: Random): String; fun acceptEdit(before: String, after: String): Boolean; fun progress(typed: String, target: String): Int /*matched chars*/; fun isComplete(typed: String, target: String): Boolean }`
    - `passage`: shuffle sentences, append until word count ≥ 100, single spaces.
    - `acceptEdit`: false when `after.length - before.length > MAX_INSERT`.
    - Comparison for `progress`/`isComplete`: lowercase, collapse whitespace runs to one space, trim.
  - `@Serializable data class AppState(val tags: Map<String, InstalledTag> = emptyMap(), val active: Map<String, ActiveMode> = emptyMap(), val groupOverrides: Map<String, Set<String>> = emptyMap(), val tapLog: List<TapLogEntry> = emptyList())`, `@Serializable data class TapLogEntry(val atMs: Long, val tagId: String, val label: String)`
  - `class StateStore(file: File) { fun load(): AppState; fun save(state: AppState) }` — missing or corrupt file → `AppState()`; save writes to `file.tmp` then atomic rename.

- [ ] **Step 1:** Tests:

```kotlin
passage(Random(1)) word count in 100..130, deterministic for same seed, differs for Random(2)
acceptEdit("ab","abc") true ; ("ab","abcde") true (3 chars) ; ("ab","abcdef") false ; ("abc","ab") true (delete)
isComplete("The  cat\nsat ", "the cat sat") true ; isComplete("the cat", "the cat sat") false
progress("the cax", "the cat sat") == 6
StateStore: save then load equals ; load on missing file == AppState() ; load on "garbage" == AppState()
expire after restart: active desk mode saved, reloaded, ModeEngine.expire at +90min removes it
```
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Commit.

### Task 6: Seed catalog and catalog source

**Files:**
- Create: `app/src/main/assets/catalog.json`, `app/.../catalog/CatalogSource.kt`
- Test: `core/src/test/.../SeedCatalogTest.kt` (reads `../app/src/main/assets/catalog.json` by relative path so it runs locally)

**Interfaces:**
- Consumes: `parseCatalog`, `isUsable`
- Produces: `interface CatalogSource { suspend fun load(): Catalog }`, `class BundledCatalogSource(context: Context) : CatalogSource`

Seed content (exact ids; copy is the author's to refine later):
- App groups: `social` [com.instagram.android, com.facebook.katana, com.twitter.android, com.snapchat.android, in.mohalla.sharechat, com.reddit.frontpage], `video` [com.google.android.youtube, com.netflix.mediaclient, in.startv.hotstar, com.amazon.avod.thirdpartyclient], `messaging` [com.whatsapp, org.telegram.messenger], `games` [] (user picks).
- `desk-deep-work`: toggle; params autoOffMinutes (90, 15–480), strictMinutes (0, 0–240); rule (no when): blockGroups [social, video], dnd.
- `bed-sleep`: toggle; params autoOffMinutes (480, 240–720), strictMinutes (0, 0–480); rule `21:00-05:00`: blockGroups [social, video, games], dnd; rule (no when): log "Bed tap outside night hours"; endActions: checklist "Good morning" [Drink a glass of water, Make the bed, Put on workout clothes], openApp (androidPackage null, uri null — user may set later; skip if both null).
- `gym-bag`: oneShot; rule: timer 60, log "Workout", checklist "Before you go" [Water bottle, Headphones, Towel].
- `front-door`: oneShot; rule: checklist "Leaving home" [Keys, Wallet, Phone charger, Lights off].

- [ ] **Step 1:** Test: seed parses; 4 recipes all `isUsable`; every `blockGroups` group id exists in `appGroups`; every `{{key}}` reference has a param.
- [ ] **Step 2:** FAIL (no file). **Step 3:** Write `catalog.json` and `CatalogSource.kt`. **Step 4:** PASS locally; push, CI green. **Step 5:** Commit.

### Task 7: NFC write and tap handling

**Files:**
- Create: `app/.../nfc/TagWriter.kt`, `app/.../nfc/TapActivity.kt`, `app/.../HabitTagsApp.kt`
- Modify: `AndroidManifest.xml`

**Interfaces:**
- Consumes: `TagPayload`, `ModeEngine.onTap`, `StateStore`, `CatalogSource`
- Produces:
  - `sealed interface WriteResult { Written(tagId); NotWritable; TooSmall; Failed(message) }`, `object TagWriter { fun write(tag: android.nfc.Tag, tagId: String): WriteResult }` — NDEF message per Global Constraints; formats `NdefFormatable` tags.
  - `HabitTagsApp.state: MutableStateFlow<AppState>` and `HabitTagsApp.update(fn: (AppState) -> AppState)` (persists via StateStore), `HabitTagsApp.catalog: StateFlow<Catalog?>`
  - `TapActivity`: transparent, `excludeFromRecents`; intent filter `android.nfc.action.NDEF_DISCOVERED` with `<data android:scheme="vnd.android.nfc" android:host="ext" android:pathPrefix="/com.habittags.app:tag"/>`. Reads tagId → `onTap` → updates state, starts/stops `BlockService`, runs effects (Task 8), shows a toast ("Desk: Deep Work on · off at 11:30" / "Strict: 15 min left"). `UnknownTag` → opens `MainActivity` at the "Set up this tag" screen with the tagId.
  - Write flow uses `NfcAdapter.enableReaderMode` inside the install screen (Task 9).

- [ ] **Step 1:** Implement; `./gradlew` can't build `:app` here — push and `gh run watch --exit-status` until green.
- [ ] **Step 2: Device check (Vineet):** install the APK artifact; on a temporary debug screen ("Write test tag") write a blank NTAG213; lock screen off/unlocked, tap → toast "Desk: Deep Work on"; tap again → "off". Tap a tag written on another phone / after clearing app data → "Set up this tag" screen. Tag with existing non-app data → write asks to overwrite.
- [ ] **Step 3:** Commit.

### Task 8: Blocking service, DND and effects

**Files:**
- Create: `app/.../block/BlockService.kt`, `block/BlockOverlay.kt`, `block/BootReceiver.kt`, `effects/EffectRunner.kt`
- Modify: `AndroidManifest.xml` (permissions `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_NOTIFICATION_POLICY`; `<queries><intent>MAIN/LAUNCHER</intent></queries>`)

**Interfaces:**
- Consumes: `ModeEngine.expire/blockedPackages/dndWanted`, `HabitTagsApp.update`
- Produces:
  - `BlockService` (foreground, `specialUse`, ongoing notification listing active modes): runs while `active` is non-empty; every 1 s: `expire(...)` (runs returned endActions via `EffectRunner`), reads latest `UsageEvents.Event.ACTIVITY_RESUMED` package; if in `blockedPackages` → show `BlockOverlay` ("<mode title> is on · ends at HH:MM" + "Go to home screen" button that launches the HOME intent), else hide. Applies `dndWanted` via `NotificationManager.setInterruptionFilter` when policy access is granted, restoring `INTERRUPTION_FILTER_ALL` when no mode wants DND. Stops itself when `active` is empty.
  - `BootReceiver`: on `BOOT_COMPLETED`, if persisted `active` non-empty → start `BlockService`.
  - `object EffectRunner { fun run(context: Context, effects: List<Action>) }`: `Timer` → `AlarmManager.setWindow(RTC_WAKEUP, end, 60_000, pendingBroadcast)` posting a "Timer done" notification; `Checklist` → open `MainActivity` checklist screen; `OpenApp` → launch intent for package or `ACTION_VIEW` uri, silently skip if neither / not installed; `Log` → append `TapLogEntry`.
  - Helpers used by UI: `Permissions.hasUsageAccess(ctx)`, `hasOverlay(ctx)`, `hasDndAccess(ctx)` + intents to the matching settings pages.

- [ ] **Step 1:** Implement; push; CI green.
- [ ] **Step 2: Device check (Vineet):** grant usage + overlay + DND access; start desk mode → open Instagram/YouTube → overlay within ~1 s; WhatsApp opens normally; DND on. Reboot phone → open YouTube → still blocked. Set desk auto-off to 15 min, kill the app from recents → mode ends within ~1 min of 15 min, DND restored. Gym-bag tap → timer notification after 60 min (or temporarily edit to 1 min).
- [ ] **Step 3:** Commit.

### Task 9: UI — catalog, install, tag detail, home, gate

**Files:**
- Create: `app/.../ui/MainActivity.kt` (Compose NavHost), `ui/CatalogScreen.kt`, `ui/InstallScreen.kt`, `ui/TagDetailScreen.kt`, `ui/HomeScreen.kt`, `ui/GateScreen.kt`, `ui/ChecklistScreen.kt`, `ui/GroupEditor.kt`, `ui/GateTextField.kt`
- Remove: Task 7 debug write screen

**Interfaces:**
- Consumes: everything above.
- Screens:
  - **Home:** active modes (title, "ends HH:MM", strict remaining), each with "End now" → Gate; list of my tags; "Add a tag" → Catalog. Banner if NFC is off or missing ("This phone can't read NFC tags").
  - **Catalog:** recipe cards; unusable recipes greyed with "Update the app to use this".
  - **Install (also "Set up this tag" with a tagId):** placement hint; param sliders within min/max showing default; per app group used, the package list with toggles + "Add app" from the launcher-app list (writes `groupOverrides`); just-in-time permission prompts only for permissions this recipe needs (blockGroups → usage + overlay; dnd → DND access); then "Hold your phone to a blank tag" (reader mode) → `TagWriter.write` with a new tagId, or for the set-up flow, assign the recipe to the scanned tagId without writing. Tag type tip: "NTAG213/215/216 tags work best".
  - **Tag detail:** edit params (takes effect next tap), switch recipe, rename, "Rewrite this tag", remove.
  - **Gate:** passage from `FrictionGate.passage`; `GateTextField` = `AndroidView` `EditText` with `inputType = TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS or TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `privateImeOptions = "nm"`, `customSelectionActionModeCallback`/`customInsertionActionModeCallback` returning false, `onTextContextMenuItem` ignoring `android.R.id.paste`, and a `TextWatcher` that reverts any edit failing `acceptEdit`; progress bar from `progress`; on `isComplete` → `endViaGate` and stop the blocks. Rejected-insert message: "Type it letter by letter — voice and paste don't count."
  - **Checklist:** title + tick boxes, opened by effects.

- [ ] **Step 1:** Implement; push; CI green.
- [ ] **Step 2: Device check (Vineet), first-run timing:** fresh install → Catalog → Desk → set auto-off 2 h → grant permissions → write blank tag → tap → blocked. Target under 2 minutes. Then: Gate — paste is blocked, Gboard mic dictation is rejected, swipe-typed words are rejected, typing the passage ends the mode. Strict 45 min: second tap refused with time left, gate still works. Change desk auto-off from tag detail → next tap uses it without rewriting. Bed: night tap blocks; morning tap ends and shows the Good morning checklist.
- [ ] **Step 3:** Commit; tag `v0.1.0-android`; push.

---

## Out of this plan (next plans)

- Backend plan: Supabase project (Vineet creates it and provides URL, anon key, access token), `recipes`/`app_groups` read-only under RLS (anon key ships in the APK), `RemoteCatalogSource` with cache + `ParamMerge` on update, optional Google sign-in and `user_tags` sync.
- iOS plan: GitHub Pages association file, Swift app, Family Controls entitlement request (needs Vineet's Apple Developer account).
