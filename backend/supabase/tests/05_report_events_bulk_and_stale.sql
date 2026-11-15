-- A full 500-event report finishes within the anon role's 3 s statement timeout, and a stale
-- (lower) tap count arriving after a newer one never lowers the stored count.
create temp table t_ids3 as select '66666666-6666-6666-6666-666666666666'::uuid as dev;
grant select on t_ids3 to anon;
set local role anon;
set local statement_timeout = '3s';
do $$
declare
  dev uuid := (select dev from t_ids3);
  zones text[] := array['Asia/Kolkata','Europe/London','America/New_York','UTC','Asia/Tokyo'];
  evs jsonb := '[]'::jsonb;
  z text;
  n int;
begin
  for i in 1..500 loop
    z := zones[1 + i % 5];
    evs := evs || jsonb_build_array(jsonb_build_object(
      'type','tap_count','v',1,'tz',z,
      'day', to_char((now() at time zone z)::date - (i % 10), 'YYYY-MM-DD'),
      'payload', jsonb_build_object('recipe','desk-deep-work','count', 1 + i % 7)));
  end loop;
  n := report_events(dev, evs);
  if n <> 500 then raise exception 'expected 500 stored events, got %', n; end if;
end $$;
reset statement_timeout;

do $$
declare
  dev uuid := '77777777-7777-7777-7777-777777777777';
  today text := to_char((now() at time zone 'UTC')::date, 'YYYY-MM-DD');
begin
  perform report_events(dev, jsonb_build_array(jsonb_build_object('type','tap_count','v',1,'day',today,'tz','UTC',
    'payload', jsonb_build_object('recipe','gym-bag','count',5))));
  perform report_events(dev, jsonb_build_array(jsonb_build_object('type','tap_count','v',1,'day',today,'tz','UTC',
    'payload', jsonb_build_object('recipe','gym-bag','count',3))));
end $$;
reset role;
do $$
begin
  if (select taps from daily_usage where device_id = '77777777-7777-7777-7777-777777777777' and recipe_id = 'gym-bag') <> 5 then
    raise exception 'stale lower count overwrote the newer one';
  end if;
end $$;
