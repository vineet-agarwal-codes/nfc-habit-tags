-- Desk and Bed can't be ended by tapping the tag (tagCanEnd false; their strict setting is gone).
-- Bed ends at a wake-up time that adapts to a late bedtime (endPolicy "wake"), and its tag works from
-- 3 hours before the usual bedtime. Schema 3: older apps show both as "Update the app".
-- The update fires Realtime and touches updated_at.
update public.recipes set doc = (doc - 'strictMinutes') || jsonb_build_object(
  'schemaVersion', 3, 'tagCanEnd', false, 'version', (doc->>'version')::int + 1,
  'description', 'Tap when you sit down to work. Social media and video apps are blocked and notifications go quiet until the timer ends. Tapping again won''t stop it; End early in the app will.',
  'params', '[{"key": "autoOffMinutes", "type": "duration", "label": "Turn off automatically after", "default": 90, "min": 15, "max": 480}]'::jsonb)
  where id = 'desk-deep-work';
update public.recipes set doc = (doc - 'strictMinutes' - 'autoOffMinutes') || jsonb_build_object(
  'schemaVersion', 3, 'tagCanEnd', false, 'version', (doc->>'version')::int + 1,
  'description', 'Tap at bedtime to block distracting apps and silence notifications. Sleep mode ends at your wake-up time, or later if you went to bed late, and starts your morning routine.',
  'params', '[{"key": "usualBedtime", "type": "time", "label": "Usual bedtime", "default": 1380, "min": 0, "max": 1439}, {"key": "wakeAt", "type": "time", "label": "Wake up at", "default": 300, "min": 0, "max": 1439, "renamedFrom": "sleepEnd"}, {"key": "latestWake", "type": "time", "label": "Latest wake-up", "default": 480, "min": 0, "max": 1439}]'::jsonb,
  'endPolicy', '{"type": "wake", "wakeAt": "{{wakeAt}}", "usual": "{{usualBedtime}}", "latest": "{{latestWake}}"}'::jsonb,
  'rules', jsonb_set(doc->'rules', '{0,when}', '{"time":"{{usualBedtime-180}}-{{wakeAt}}"}'::jsonb))
  where id = 'bed-sleep';
