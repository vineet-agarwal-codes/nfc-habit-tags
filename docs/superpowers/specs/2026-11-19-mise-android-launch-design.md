# Mise — Android Production UI and Play Store Launch — Design

Date: 2026-11-19. Status: approved by Vineet 2026-11-19. Builds on the approved specs of 2026-11-14 and 2026-11-15 (behaviour unchanged unless stated here).

## Intent

Ship the tag-based Android app on Google Play for real people to try. Today it works but looks like a default Material prototype. It needs an identity, screens people would enjoy and pay for later, and everything Google Play requires.

## Decisions made with Vineet

- **Name:** Mise (said "meez"). Play listing title: **Mise: Shape your spaces**. ("meez" spelling rejected: an existing culinary "recipes" product, getmeez.com.)
- **Vocabulary:** a **space** is a place in your home (Desk, Bed, Gym bag). A **recipe** is what a space runs. You *add a recipe to a space*; the space *runs* it. Discover is "Recipes for your desk", etc.
- **Colour rule 80-15-5:** 80% base (cool off-white #F5F5F2; charcoal #151716 in dark), 15% supporting (ink, one surface tone step, pale space tints), 5% accent **tomato red** (#E5482F, tuned for contrast) on the primary action, the brand mark and live indicators. Exception, on purpose: a running mode's screen is filled with its space colour.
- **Space colours, always picked automatically** (never a step for the user): Desk tomato, Bed deep blue, Gym bag green; other spaces take the next unused colour from a fixed set of six. Changeable later in the space's settings.
- **No emergency skips** for now. Ending early = tap the tag again (free) or the typing gate.
- **No streaks, no guilt, no paywall.** Everything stays free.
- Approach chosen from competitor research (Brick, Unpluq, Opal, one sec, Forest, Finch, Structured, NFC Tools, Apple Shortcuts): own what none of them do — tags tied to places, a free recipe catalog, three-tap tag setup.

## Goals

1. A first-time user goes from install to a working Desk space in under 2 minutes.
2. The app looks like one designed product: name, mark, type, colour, motion, light and dark.
3. Passes Google Play review; ready for the 14-day closed test (12+ testers) required for new personal accounts.

## Non-goals

- iOS redesign (later, sharing the same identity), paid features, emergency skips, streaks, Zone lab changes (stays debug-only), new recipe actions.

## Anti-goals

- A 20-step onboarding quiz, upfront paywall, mascots, gems, trees, gradients, glass, purple, rows of identical icon cards.
- A block screen that shames the user.
- A Play rejection over undeclared permissions.

## Approaches considered

- **A. Restyle in place (chosen):** keep the current Compose screens and navigation logic; replace theme, type, components and the screen layouts per this doc. Low risk; behaviour and tests survive.
- B. Rebuild the UI layer from scratch with a new navigation structure. More freedom, much more risk to working flows.
- C. Ship as-is with only an icon and name. Fails goal 2.

## Identity

- **Mark:** lowercase "m" whose last stroke ends in a tag's notched edge; tomato on off-white. Adaptive icon + monochrome themed icon.
- **Type:** one family, bundled (no network fonts). Builder picks between Bricolage Grotesque and Instrument Sans by rendering both on the Home and Active screens; very large for names and countdowns, regular for text. Sentence case everywhere, no tiny uppercase labels.
- **Surfaces:** flat. Separation by one tone step, not lines or shadows (shadows only on sheets and dialogs).
- **Motion:** short and purposeful: a card fills with its colour when a mode starts, the countdown ticks, the tag target pulses while scanning. All with reduced-motion fallbacks.

## Screens

Bottom navigation: **Home**, **Discover**. (Profile comes later.)

```
Home                                   Discover
 Mise                                   Recipes
 ┌────────────────────────────┐         For your desk
 │ Desk          on            │  red    ┌──────────────────────┐
 │ 1:24:10                     │  field  │ Deep work            │
 │ Deep work · ends 11:30      │         │ Blocks social, video │
 │ [End early]                 │         │ 90 min · 320 people  │
 └────────────────────────────┘         │ [Add to a space]     │
 ┌──────────────┐┌─────────────┐         └──────────────────────┘
 │ Bed          ││ Gym bag     │  tints  For bedtime …
 │ from 21:00   ││ tap to run  │         For leaving home …
 └──────────────┘└─────────────┘
 ( Hold your phone to a tag )   ← scan target
```

- **Home = your spaces.** One card per space (installed recipe), in its pale tint: space name large, recipe name, its state (on / starts 21:00 / tap to run / last used), tag count. A running space is a full-colour card at the top with the countdown. "Start now" lives on the card. Bottom: a scan target ("Hold your phone to a tag") that reads a tag in the app. Empty state: one large invitation to set up the first space.
- **Discover:** recipes grouped by place ("For your desk", "For bedtime", "For leaving home", "For workouts"), new recipes first within the existing popularity rules. A card says what it does in one line, how long, and usage when ≥5. One button: **Add to a space**.
- **Recipe page:** name very large, where to stick the tag, what it does in plain words, settings (sliders, time pickers) with defaults already set, **Add to Desk** (space name pre-filled from the recipe; editable).
- **Add flow (three steps, no colour step):** settings (optional, defaults fine) → permissions this recipe needs, each explained in one sentence on its own card → "Hold your phone to a blank tag" (or "Skip, start from the app"). Lands on Home with the new space.
- **Running mode screen:** opened from the running card or a tap. Whole screen in the space colour: space name, a very large countdown, "ends 11:30", what's paused (app icons), **"You reached for blocked apps 4 times"** (count since the mode started), Strict remaining if any, **End early**.
- **Block screen (overlay over a blocked app):** same colour and type as the running screen: "Desk is on", ends at, the reach count, **Go to home screen**. Never shaming copy.
- **End early (typing gate):** keeps the redesigned gate; restyled with the new type and the space colour.
- **Space settings:** rename, colour (optional), recipe settings, tags (write another, rewrite, remove), remove space.
- **Onboarding (first run only):** 3 screens: (1) "Shape your spaces" — one sentence on what Mise does, (2) "Tags make it a tap" — buy any NTAG213/215/216 sticker, or start from the app, (3) "Set up your first space" → Discover filtered "For your desk". Permissions are not asked here; only in the add flow, when needed.
- **States:** loading (skeleton tints, no spinners on cards), offline (cached recipes, "Showing saved recipes"), errors in plain words with a retry, NFC off / no NFC banners.
- **Dark mode** for every screen.

## Data changes

- A space = an installed recipe instance. Its name defaults to a new optional recipe field `space` (e.g. `{ "name": "Desk", "colour": "tomato" }`); recipes without it fall back to the text before ":" in the title, else the title. Space name and colour are stored with the installed recipe on the phone. Existing installs migrate automatically (name from the recipe, colour by the rule above).
- Discover grouping uses a new optional recipe field `place` ("desk", "bed", "leaving", "workout", …); ungrouped recipes go under "More recipes". Seed recipes and the production catalog get both fields (schemaVersion stays 2: unknown fields are ignored by older apps).
- Reach count: the block service increments a per-mode counter each time the overlay is shown for a new app open; stored with the active mode; also added to usage events as a count only (no app names) — new event type `block_reach` v1.

## Play Store readiness

- **Release signing:** an upload key generated on the VM, kept outside the repo (`~/.config/habit-tags/`), backed up by Vineet; Play App Signing enabled. CI builds signed release AABs only when the key is supplied as GitHub secrets; otherwise unsigned.
- **applicationId** is `app.mise` (Vineet, 2026-11-19; cannot change after the first upload). The Kotlin namespace stays `com.habittags.app`; tags keep the `com.habittags.app:tag` record type and gain an `app.mise` application record. App label "Mise".
- **Permissions and declarations:** usage access and overlay (explained in-app before the system screen), foreground service `specialUse` (Play declaration text + short screen recording), DND access, notifications, NFC. No accessibility service, no QUERY_ALL_PACKAGES. Zone lab code and its location permissions are debug-only and absent from the release build (verified on the built AAB's merged manifest).
- **Privacy policy:** a page on the GitHub Pages site describing the anonymous device ID, usage events, block reach counts, Supabase as processor, retention and deletion contact. Linked in the app (Discover footer / settings) and the listing.
- **Data safety form:** answers drafted from the actual events the app sends.
- **Store listing:** short and full description, 6–8 phone screenshots of the real app (taken on the emulator from the release build with seed data), feature graphic, icon, category Productivity, content rating questionnaire answers.
- **Testing track:** closed test with ≥12 testers for 14 days before applying for production; Vineet invites friends by email list.
- **Version:** `versionName 1.0.0`; versionCode from CI run number.

## Constraints

- Native Compose, existing architecture, KMP core untouched except new optional fields and the reach count.
- Bundled fonts only (works offline).
- Play policy on usage access and overlays is reviewed by humans; declarations must match in-app explanations word for word in spirit.
- Vineet must create the Play Console account ($25) and do identity verification; I can't.

## Calls made after builder review (Claude's calls — Vineet to check)

- **One space = one installed recipe.** A recipe can be in one space at a time (as today); a "mode" is simply a space that is on. Two Desks with the same recipe is out of scope.
- **The Home scan target is a real in-app reader** (NFC reader mode while Home is open) that runs the same tap handling as a tag tapped from outside the app.
- **One "reach"** = the foreground app switching to a blocked app while the mode is on (counted once per switch, not per second). `block_reach` events go through the existing report RPC, which already stores unknown event types; no backend change.
- **Release build:** no code shrinking (R8) for 1.0 to avoid breakage; the Supabase publishable key in the app is public by design.
- **Brand assets** (mark, feature graphic, screenshots) are drawn and captured by the builder; Vineet approves the mark and screenshots before upload.
- **Catalog edits** (`space`, `place` fields) are applied to the production database by migration, as before.
- **Retention:** raw usage events kept 90 days (existing cron); Zone lab data excluded as before.

## Left to the builder

- Exact type sizes, spacing, the six space colours' final values (contrast-checked in light and dark), icon drawing details, motion timings.
- Compose component structure.

## Account and contact (decided 2026-11-19)

- The Play developer account is an **individual account in Vineet's mother's name** from the start (her ID verification and payments profile, paid into her bank).
- **Public contact details** in the listing and privacy policy: Vineet's Gmail (vineet140502@gmail.com) and his mobile number.
- Vineet manages the Play Console day to day as an **invited user** on her account.
