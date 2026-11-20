# Bedtime that adapts, and tags that can't end a mode — Design note

Date: 2026-11-20. Status: approved by Vineet in chat (2026-11-20). Builds on the Mise launch design.

## What changes

1. **`tagCanEnd`** (recipe field, default `true`). When `false`, tapping the tag while the mode is on
   doesn't end it: the app says "Desk is on until 11:30. To stop early, use End early in the app."
   The mode ends only at its end time or through End early (typing gate). Desk: Deep Work and
   Bed: Sleep Mode set it to `false`; their "Can't turn off with the tag for" (strict) setting is
   removed, since the tag can't end them at all. Strict stays supported for other recipes.
2. **Wake-up end policy** (recipe field `endPolicy`), replacing Bed's fixed 8-hour auto-off:
   ```json
   "endPolicy": { "type": "wake", "wakeAt": "{{wakeAt}}", "usual": "{{usualBedtime}}", "latest": "{{latestWake}}" }
   ```
   Params: usual bedtime 23:00, wake up at 05:00, latest wake-up 08:00 (time params).
   End = the next wake-up time; if that leaves less than the usual sleep (usual bedtime → wake-up,
   6 h by default), end = start + usual sleep, capped at the latest wake-up of that morning.
   21:00 → 05:00 · 23:00 → 05:00 · 01:00 → 07:00 · 03:00 → 08:00. Computed with real instants in the
   phone's time zone, so DST nights come out right.
3. **Offsets in time windows:** a window side may be `{{param±minutes}}`. Bed's tag window is
   `{{usualBedtime-180}}-{{wakeAt}}` (20:00–05:00 by default).
4. **Tag tapped outside the hours:** a small Mise-styled dialog opens over whatever is on screen:
   "Bed normally starts from 20:00. It's 15:10 now." — **Start anyway** / **Not now**. Start anyway
   is the same forced start as in the app. (Previously a toast only.)
5. **Copy:** "Turns on from 20:00 · ends by 05:00 (up to 08:00 if you sleep late)".
6. **Tags on the lock screen** (Vineet, 2026-11-20): with the screen on and the phone locked, a tag
   tap starts the mode straight away without asking to unlock. The tap screen and the "Outside its
   usual hours" dialog show over the lock screen (`showWhenLocked`; the screen isn't turned on and the
   keyguard isn't dismissed), so Start anyway works there too. A tag Mise doesn't know still opens the
   app, which needs unlocking.
7. **"Tags without unlocking" tip:** Android's Secure NFC ("Require device unlock for NFC") makes the
   phone ignore tags while locked. Only when the phone supports it and it's on (API 29+), a tip says
   "Want tags to work without unlocking? Turn off 'Require device unlock for NFC'." with
   **Open NFC settings**; if the phone won't open that screen: "Your phone doesn't let apps open this
   setting. Look for it under Settings > Connected devices > NFC." It shows in the add flow's tag step,
   and once on Home after the first tag (with "Not now", remembered). It's re-checked whenever the
   screen comes back, and disappears once the setting is off. The show/hide rule is a pure function in
   the shared core with tests.

## Compatibility

- Recipes using `endPolicy` or window offsets are `schemaVersion` 3; older apps show them as
  "Update the app to use this" instead of misreading them. `SUPPORTED_SCHEMA_VERSION` becomes 3.
- Param specs may say `renamedFrom`; installed values carry over (Bed's `wakeAt` takes the old
  `sleepEnd`). Old Bed params with no successor (sleepStart, autoOffMinutes, strictMinutes) are dropped.
- Production catalog updated by migration 0009 (version bumped so phones refetch).
