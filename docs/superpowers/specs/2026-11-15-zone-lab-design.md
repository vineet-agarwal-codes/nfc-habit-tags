# Zone Lab (measuring prototype) — Design

Date: 2026-11-15. Status: approved by Vineet 2026-11-15. Debug builds only.

## Intent

Before designing software-only "zones" (a spot in the home that triggers a recipe without an NFC tag), measure how well a phone can tell Vineet's spots apart with Wi-Fi signal strength plus phone sensors, both when the phone is resting there and when it's in a pocket. The lab collects data from real daily use and uploads it so Claude can analyse it directly.

## Decisions (Vineet)

- Lives in the test (debug) APK only; release builds don't contain it or its permissions.
- No report screen and no export: every recording, tracked sample and feedback answer uploads to Supabase automatically.
- Privacy restrictions on this data are lifted; a privacy policy and Play data-safety update are required before public release.

## Behaviour

- **Entry:** a separate "Zone lab" launcher icon (debug builds only).
- **Teach:** pick a spot (Bed, Desk, Kitchen, Washroom, Sofa, or custom) and a mode ("resting" = phone put down there, "in pocket" = you're there with the phone on you); Record runs until 4 fresh Wi-Fi scans ("3 of 4 scans"). Android limits apps to 4 scans per 2 minutes, so a second recording may wait.
- **Where am I?:** one fresh sample → guess with confidence per spot → Right / Wrong (Wrong → pick the actual spot).
- **Lab tracking:** a foreground service (type location, started from the app; no background-location permission). After the step detector sees movement and the phone then stays still for 20 s, it samples, guesses and stores the result. At most once an hour it posts "Are you at X?" with Yes / No (No → pick the spot).
- **Each sample:** Wi-Fi list `{b: BSSID, r: RSSI dBm, f: MHz}` (no network names), scan age and whether the scan request was accepted, magnetometer magnitude mean/std (µT), gravity vector, light (lux), charging, screen on. One aggregated record per sample, not raw sensor streams.

## How it works

- **Classifier (shared core):** weighted k-nearest neighbours (k = 5, weight 1/(1+d)) over every recorded sample. Wi-Fi distance = RMS difference in dBm over the union of seen BSSIDs, a missing AP counting as −100 dBm. Optional features add the magnet-magnitude difference (µT) and the angle between gravity vectors (÷10 per degree). Feature sets: Wi-Fi only, + magnet, + magnet + orientation. Confidence = a spot's share of the neighbour weight.
- **Upload:** through the existing `report_events` RPC as new event types (v1), each with `day` + `tz` + UTC `at`: `zone_recording` (metadata), `zone_sample` (one per sample; recording samples carry the recording id, tracking samples carry the guess), `zone_guess_feedback`. A local outbox is sent right after each recording and each feedback answer (and when tracking samples accumulate), in chunks of ≤ 500 (the RPC rejects larger calls). The Wi-Fi list is trimmed to the strongest APs so a payload stays under the RPC's 4 KB limit.
- **Retention:** migration 0007 keeps `zone_*` events out of the 90-day purge.
- **Analysis:** `backend/analysis/zone_lab.py` pulls `zone_*` events and prints leave-one-recording-out accuracy per spot × mode × feature set (same classifier as the app), feedback accuracy, and fresh-vs-cached scan stats.

## Claude's calls

- Spot guesses ignore mode (a spot's resting and pocket recordings share one label); accuracy is reported per mode.
- Bluetooth is left out (phones randomise their addresses).
- If the phone has no step detector, tracking samples every 15 minutes instead.
- CI uses a debug fake scan source; the real scanner is only exercised on Vineet's phone.

## Not in scope

Recipes triggered by zones, iPhone, any user-facing (release) UI.
