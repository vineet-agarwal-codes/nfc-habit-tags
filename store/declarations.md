# Play Console declarations — Mise 1.0.0

## Foreground service: specialUse (BlockService)
**Type:** Special use · **Subtype (manifest):** "Enforces the focus mode the user switched on by tapping an NFC tag: blocks chosen apps until the mode ends."
**Why a foreground service:** while a mode the user started is on, the app must keep checking which app is in front (once a second) and cover blocked apps immediately, and end the mode at its auto-off time. This can't be deferred (WorkManager) or done only while the app is visible. The service runs only while a mode is on and stops itself when the last mode ends. The user sees an ongoing notification listing the running spaces.
**Video (≈30 s) to record on a phone:** open Mise → Desk → Start now → the ongoing notification appears → open Instagram/YouTube → the Desk cover appears → tap the tag (or End early) → notification disappears.

## Usage access (PACKAGE_USAGE_STATS)
Not a restricted Play permission, but explain in the listing (done): "to know when a blocked app opens during a mode you started". Used only on the device.

## Display over other apps (SYSTEM_ALERT_WINDOW)
Used to draw "<Space> is on" over a blocked app the user chose to block, while a mode they started is on. Asked for just in time, with an explanation, only for recipes that block apps.

## Do Not Disturb access (ACCESS_NOTIFICATION_POLICY)
For recipes that silence notifications; uses an automatic Do Not Disturb rule so the user's own DND state is restored when the mode ends.

## Not used
No AccessibilityService, no QUERY_ALL_PACKAGES (a `<queries>` launcher intent is declared instead), no location, no SMS/call log, no exact alarms.

## Content rating (IARC questionnaire)
Utility/productivity; no violence, sexuality, language, controlled substances, gambling, user-generated content or user-to-user communication; no location sharing; no purchases. Expected: Everyone / 3+.

## Target audience
18+ (or 13+); not designed for children. Not in the Families program.

## Ads
No ads.
