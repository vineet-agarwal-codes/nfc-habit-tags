# Google Play Data safety form — answers for Mise 1.0.0

Based on what the app actually sends (`report_events`: installed recipe IDs, daily tap counts, daily blocked-app reach counts, time zone, random device ID).

**Does your app collect or share any of the required user data types?** Yes (collects; does not share).

**Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS to Supabase).

**Do you provide a way for users to request that their data is deleted?** Yes: by email with the device ID shown in the app (any space → About).

## Data types collected
| Category → type | Collected | Shared | Ephemeral | Required/optional | Purposes |
|---|---|---|---|---|---|
| App activity → App interactions | Yes | No | No | Required | Analytics, App functionality (popularity counts shown in Discover) |
| Device or other IDs → Device or other IDs | Yes (random app-generated ID) | No | No | Required | Analytics, App functionality |

Not collected: location, personal info, financial info, health, messages, photos/videos, audio, files, calendar, contacts, web browsing, installed apps (the list of apps the user blocks stays on the device), crash logs, diagnostics.

Notes for the form:
- Collection is not optional in 1.0 (no opt-out switch); the anonymous counts power Discover's popularity. Answer "Required" honestly.
- Usage access and the overlay are processed on the device only; that's not "collection".
- Processor: Supabase (service provider acting on our behalf) — a service provider is not "sharing" under Play's definition.
