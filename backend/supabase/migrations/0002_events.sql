-- Usage events: every event is kept raw (extensible); known types also update derived tables.
create table public.events (
  id bigserial primary key,
  device_id uuid not null,
  type text not null,
  v int not null,
  day date,
  tz text,
  at timestamptz,
  payload jsonb not null,
  received_at timestamptz not null default now()
);
create index events_type_day on public.events(type, day);

create table public.device_installs (
  device_id uuid not null,
  recipe_id text not null,
  last_seen date not null,
  primary key (device_id, recipe_id)
);

-- day is the phone's local date in zone tz.
create table public.daily_usage (
  device_id uuid not null,
  recipe_id text not null,
  day date not null,
  tz text not null,
  taps int not null,
  primary key (device_id, recipe_id, day)
);

alter table public.events enable row level security;
alter table public.device_installs enable row level security;
alter table public.daily_usage enable row level security;
revoke all on public.events, public.device_installs, public.daily_usage from anon, authenticated;
revoke all on sequence public.events_id_seq from anon, authenticated;

create or replace function public.report_events(device_id uuid, events jsonb) returns int
language plpgsql security definer set search_path = public as $$
#variable_conflict use_column
declare
  e jsonb;
  stored int := 0;
  ev_day date;
  ev_tz text;
  local_today date;
begin
  if jsonb_typeof(events) <> 'array' then
    raise exception 'events must be an array';
  end if;
  if jsonb_array_length(events) > 500 then
    raise exception 'too many events';
  end if;

  for e in select value from jsonb_array_elements(events) loop
    continue when jsonb_typeof(e) <> 'object'
      or jsonb_typeof(e->'type') <> 'string'
      or jsonb_typeof(e->'v') <> 'number'
      or jsonb_typeof(e->'payload') <> 'object'
      or octet_length((e->'payload')::text) > 4096;

    ev_day := null;
    ev_tz := e->>'tz';
    if e ? 'day' and e->>'day' is not null then
      continue when ev_tz is null or not exists (select 1 from pg_timezone_names where name = ev_tz);
      begin
        ev_day := (e->>'day')::date;
      exception when others then
        continue;
      end;
      local_today := (now() at time zone ev_tz)::date;
      continue when ev_day < local_today - 14 or ev_day > local_today + 1;
    end if;

    insert into public.events(device_id, type, v, day, tz, at, payload)
    values (report_events.device_id, e->>'type', (e->>'v')::int, ev_day, ev_tz,
            case when jsonb_typeof(e->'at') = 'string' then (e->>'at')::timestamptz end, e->'payload');
    stored := stored + 1;

    if e->>'type' = 'installed_snapshot' and (e->>'v')::int = 1 then
      delete from public.device_installs d where d.device_id = report_events.device_id;
      insert into public.device_installs(device_id, recipe_id, last_seen)
      select report_events.device_id, r.id, current_date
      from (select value as id from jsonb_array_elements_text(coalesce(e->'payload'->'recipes', '[]'::jsonb)) limit 200) ids
      join public.recipes r on r.id = ids.id
      on conflict do nothing;
    elsif e->>'type' = 'tap_count' and (e->>'v')::int = 1 and ev_day is not null
      and jsonb_typeof(e->'payload'->'count') = 'number'
      and (e->'payload'->>'count')::int between 0 and 500
      and exists (select 1 from public.recipes r where r.id = e->'payload'->>'recipe') then
      insert into public.daily_usage(device_id, recipe_id, day, tz, taps)
      values (report_events.device_id, e->'payload'->>'recipe', ev_day, ev_tz, (e->'payload'->>'count')::int)
      on conflict (device_id, recipe_id, day) do update set taps = excluded.taps, tz = excluded.tz;
    end if;
  end loop;
  return stored;
end $$;

revoke all on function public.report_events(uuid, jsonb) from public;
grant execute on function public.report_events(uuid, jsonb) to anon, authenticated;
