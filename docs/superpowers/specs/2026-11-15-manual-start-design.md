# Manual Start — Design

Date: 2026-11-15. Status: approved by Vineet 2026-11-15. Builds on the Discover/My recipes design.

## Intent

Make the app a one-stop control: a user without the physical tag at hand can still start a recipe from the app.

## Behaviour (as approved)

- Every recipe in My recipes gets **Start now** (toggle recipes) or **Run now** (one-shot recipes) on its card and on its Edit screen. Hidden while that recipe's mode is on.
- It behaves exactly like a tag tap: same time-of-day rule selection, the user's settings (auto-off, strict), the "On now" strip.
- Works for recipes installed without a tag.
- Ending: tapping the recipe's tag still ends it; without the tag, only the existing typing gate; auto-off still applies; strict minimum still applies (only the gate during strict).
- Stats: a manual start counts as a use (a tap). Each tap log entry records its source, `tag` or `app` (kept on the phone, not reported).
- A recipe runs at most once: with two tags, Start now starts it once, as if the first tag were tapped.

## Approach

Reuse `ModeEngine.onTap` with the recipe's first tag (insertion order) as the mode key, or a virtual tag `app:<recipeId>` for tag-less installs, rather than a parallel start path. One code path means rules, params, strict and auto-off can't drift between tag and app starts.

Rejected: a separate `startManually` engine path (duplicates the rule/params/strict logic).

## Calls made (Claude's, for Vineet to check)

- **A recipe is the unit that's on or off.** Tapping any tag of a recipe whose mode is already on (started by another tag or by the app) acts on that running mode — it ends it, or is refused during strict — instead of starting a second copy. Before this change two tags of the same recipe ran independent modes.
- **Settings for tag-less installs** live in a new per-recipe map (`recipeParams`). Install → "Skip for now" saves the chosen settings there; the Edit screen's sliders now show even without tags and write to both the recipe's tags and `recipeParams`. Writing a new tag copies the current settings.
- Pressing Start now while the recipe is already on (e.g. a stale screen) shows "Already on" and changes nothing.
- Start now first checks the recipe's permissions (usage access, overlay, DND access). A recipe installed with "Skip for now" may lack them; the Edit screen then shows the permission cards and enables Start now once they're granted.
