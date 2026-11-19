-- Mise: each seed recipe names the space it belongs to and its Discover place group.
-- Older apps ignore the new fields; the update fires Realtime and touches updated_at.
update public.recipes set doc = doc || jsonb_build_object(
  'space', '{"name":"Desk","colour":"tomato"}'::jsonb, 'place', 'desk', 'version', (doc->>'version')::int + 1)
  where id = 'desk-deep-work';
update public.recipes set doc = doc || jsonb_build_object(
  'space', '{"name":"Bed","colour":"deep_blue"}'::jsonb, 'place', 'bed', 'version', (doc->>'version')::int + 1)
  where id = 'bed-sleep';
update public.recipes set doc = doc || jsonb_build_object(
  'space', '{"name":"Gym bag","colour":"green"}'::jsonb, 'place', 'workout', 'version', (doc->>'version')::int + 1)
  where id = 'gym-bag';
update public.recipes set doc = doc || jsonb_build_object(
  'space', '{"name":"Front door"}'::jsonb, 'place', 'leaving', 'version', (doc->>'version')::int + 1)
  where id = 'front-door';
