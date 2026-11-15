# Habit Tags for iPhone

SwiftUI app; every rule comes from the shared Kotlin core (`android/core`, linked as `HabitCore.xcframework` through `AppEngine`). Swift only draws screens and talks to iOS (NFC, notifications, network, storage).

## Build locally (Mac)

```
cd android && ./gradlew :core:assembleHabitCoreReleaseXCFramework
cd ../ios && brew install xcodegen && xcodegen generate && open HabitTags.xcodeproj
```

## Free sideloading for friends (no paid Apple account)

Every push builds `HabitTags-unsigned.ipa` (GitHub Actions → `ios` run → artifact **habit-tags-unsigned-ipa**; unzip it to get the .ipa).

- **SideStore** (no computer after a one-time setup): install SideStore by following sidestore.io, then open the .ipa in SideStore and sign in with your own Apple ID. Apps signed with a free Apple ID expire after 7 days; SideStore refreshes them over Wi-Fi.
- **AltStore / Sideloadly**: same idea, using AltServer or Sideloadly on a Windows/Mac computer.
- iOS 16+: turn on Settings → Privacy & Security → Developer Mode. Then trust your Apple ID under Settings → General → VPN & Device Management.

What works when free-signed: Discover, My recipes, install, settings, Start now / Start anyway, the On now strip, the typing gate, timers, checklists, auto-off reminders, stats. Tag writing/reading and tag taps from the lock screen need the NFC and Associated Domains capabilities, which free Apple IDs may not grant; the app then says "Tag features need the full version".

## iPhone limits (all versions)

- **App blocking**: needs Apple's Family Controls approval (requested with the paid account). Until then recipes show "Blocking apps is coming soon on iPhone".
- **Do Not Disturb / Focus**: apps can't switch it. Use a Shortcut: Shortcuts → Automation → "App" → Habit Tags → "Is Opened" → Set Focus → Do Not Disturb → On (Run Immediately), and a second one for "Is Closed" if you like.

## TestFlight (once the paid account exists)

Add repo secrets `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_KEY_P8`, `TEAM_ID`; the `testflight` job then uploads every main build. Universal Links also need the `vineet-agarwal-codes.github.io` Pages repo with `/.well-known/apple-app-site-association` listing `<TEAM_ID>.com.habittags.app` for `/t/*`.
