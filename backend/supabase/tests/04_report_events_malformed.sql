-- One malformed event is skipped; it never aborts the rest of the report.
create temp table t_ids2 as select '55555555-5555-5555-5555-555555555555'::uuid as dev;
grant select on t_ids2 to anon;
set local role anon;
do $$
declare
  dev uuid := (select dev from t_ids2);
  today text := to_char((now() at time zone 'UTC')::date, 'YYYY-MM-DD');
  n int;
begin
  n := report_events(dev, jsonb_build_array(
    jsonb_build_object('v',1,'payload','{}'::jsonb),                                           -- no type
    jsonb_build_object('type','x','payload','{}'::jsonb),                                      -- no v
    jsonb_build_object('type','x','v',1.5,'payload','{}'::jsonb),                              -- non-integer v
    jsonb_build_object('type','x','v',1,'at','not a time','payload','{}'::jsonb),              -- bad at
    jsonb_build_object('type','x','v',1,'day','2026-02-30','tz','UTC','payload','{}'::jsonb),  -- bad date
    jsonb_build_object('type','installed_snapshot','v',1,'day',today,'tz','UTC','payload', jsonb_build_object('recipes','oops')),
    jsonb_build_object('type','tap_count','v',1,'day',today,'tz','UTC','payload', jsonb_build_object('recipe','gym-bag','count','many')),
    jsonb_build_object('type','tap_count','v',1,'day',today,'tz','UTC','payload', jsonb_build_object('recipe','desk-deep-work','count',2))
  ));
  if n <> 3 then raise exception 'expected 3 stored events (bad snapshot, bad count, good count), got %', n; end if;
end $$;
reset role;
do $$
begin
  if (select taps from daily_usage where device_id = (select dev from t_ids2) and recipe_id = 'desk-deep-work') <> 2 then
    raise exception 'good tap_count after malformed events was not applied';
  end if;
  if exists (select 1 from daily_usage where device_id = (select dev from t_ids2) and recipe_id = 'gym-bag') then
    raise exception 'non-numeric count was applied';
  end if;
end $$;
