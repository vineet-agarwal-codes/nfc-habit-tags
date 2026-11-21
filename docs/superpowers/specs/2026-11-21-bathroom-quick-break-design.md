# Bathroom "Quick break" — Design note

Date: 2026-11-21. Approved by Vineet (chat, 2026-11-21).

- New recipe `bathroom-break` ("Bathroom: Quick break"), space Bathroom (colour picked automatically), Discover group "For the bathroom".
- Tap in: the mode starts, but social media, video and games are only blocked after **Lock apps after** (default 5 min, 0 = "Instantly", up to 10). No Do Not Disturb.
- Tap out: ends it any time (`tagCanEnd` true), before or after the lock.
- Forgotten tap out: **Ends by itself after** 30 min (15–60). End early and the emergency exit still work.
- Engine: recipe field `blockAfterMinutes` (param-bindable) → `ActiveMode.blockFrom`; blocked apps, Do Not Disturb and reach counts only apply once `blockFrom` has passed. Schema 4: older apps show "Update the app".
- While waiting, the card, running screen and notification read "Apps lock in 4:12" / "Then blocking …" / "apps lock at 10:05".
- Params gain an optional `zeroLabel` ("Instantly") for how 0 reads on a slider; sliders with a range ≤ 30 move in steps of 1.
- Production: migration 0011 (published now, so Discover shows it as new).
