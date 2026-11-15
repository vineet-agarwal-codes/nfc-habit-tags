-- report_events: validate time zones with one pg_timezone_names read per call (a per-event lookup
-- timed out on 500-event reports), and never let a stale lower tap count replace a newer one.
create or replace function public.report_events(device_id uuid, events jsonb) returns int
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  e jsonb;
  stored int := 0;
  ev_day date;
  ev_tz text;
  local_today date;
  ev_at timestamptz;
  known_zones text[];
begin
  if jsonb_typeof(events) <> 'array' then
    raise exception 'events must be an array';
  end if;
  if jsonb_array_length(events) > 500 then
    raise exception 'too many events';
  end if;

  for e in select value from jsonb_array_elements(events) loop
    continue when coalesce(
      jsonb_typeof(e) <> 'object'
      or jsonb_typeof(e->'type') <> 'string'
      or (e->>'v') !~ '^[0-9]{1,6}$'
      or jsonb_typeof(e->'payload') <> 'object'
      or octet_length((e->'payload')::text) > 4096,
      true);

    ev_day := null;
    ev_tz := e->>'tz';
    if e ? 'day' and e->>'day' is not null then
      continue when ev_tz is null;
      -- pg_timezone_names reads the zone files on every query; load it once per call, not per event.
      if known_zones is null then
        select array_agg(name) into known_zones from pg_timezone_names;
      end if;
      continue when not (ev_tz = any(known_zones));
      begin
        ev_day := (e->>'day')::date;
      exception when others then
        continue;
      end;
      local_today := (now() at time zone ev_tz)::date;
      continue when ev_day < local_today - 14 or ev_day > local_today + 1;
    end if;

    ev_at := null;
    if jsonb_typeof(e->'at') = 'string' then
      begin
        ev_at := (e->>'at')::timestamptz;
      exception when others then
        continue;
      end;
    end if;

    insert into public.events(device_id, type, v, day, tz, at, payload)
    values (report_events.device_id, e->>'type', (e->>'v')::int, ev_day, ev_tz, ev_at, e->'payload');
    stored := stored + 1;

    if e->>'type' = 'installed_snapshot' and (e->>'v')::int = 1 and jsonb_typeof(e->'payload'->'recipes') = 'array' then
      delete from public.device_installs d where d.device_id = report_events.device_id;
      insert into public.device_installs(device_id, recipe_id, last_seen)
      select report_events.device_id, r.id, current_date
      from (select value #>> '{}' as id from jsonb_array_elements(e->'payload'->'recipes') where jsonb_typeof(value) = 'string' limit 200) ids
      join public.recipes r on r.id = ids.id
      on conflict do nothing;
    elsif e->>'type' = 'tap_count' and (e->>'v')::int = 1 and ev_day is not null
      and (e->'payload'->>'count') ~ '^[0-9]{1,3}$'
      and (e->'payload'->>'count')::int between 0 and 500
      and exists (select 1 from public.recipes r where r.id = e->'payload'->>'recipe') then
      insert into public.daily_usage(device_id, recipe_id, day, tz, taps)
      values (report_events.device_id, e->'payload'->>'recipe', ev_day, ev_tz, (e->'payload'->>'count')::int)
      -- Counts for a day only grow; a stale lower count arriving late must not replace a newer one.
      on conflict (device_id, recipe_id, day) do update
        set taps = greatest(daily_usage.taps, excluded.taps),
            tz = case when excluded.taps >= daily_usage.taps then excluded.tz else daily_usage.tz end;
    end if;
  end loop;
  return stored;
end $$;

revoke all on function public.report_events(uuid, jsonb) from public;
grant execute on function public.report_events(uuid, jsonb) to anon, authenticated;
