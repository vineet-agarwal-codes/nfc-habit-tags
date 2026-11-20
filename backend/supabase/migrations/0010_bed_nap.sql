-- "Start anyway" on Bed outside its hours is a nap: it ends after napMinutes (default 60), not at the
-- next wake-up. Older apps ignore the extra fields.
update recipes set doc = doc || jsonb_build_object(
  'version', (doc->>'version')::int + 1,
  'params', (doc->'params') || '[{"key": "napMinutes", "type": "duration", "label": "Nap length", "default": 60, "min": 15, "max": 240}]'::jsonb,
  'endPolicy', (doc->'endPolicy') || '{"nap": "{{napMinutes}}"}'::jsonb
), updated_at = now()
where id = 'bed-sleep' and not (doc->'params') @> '[{"key": "napMinutes"}]'::jsonb;
