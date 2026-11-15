-- report_events stores every event and keeps the derived install/usage tables in sync.
create temp table t_ids as select '11111111-1111-1111-1111-111111111111'::uuid as dev;
grant select on t_ids to anon;

set local role anon;
do $$
declare
  dev uuid := (select dev from t_ids);
  today text := to_char((now() at time zone 'Asia/Kolkata')::date, 'YYYY-MM-DD');
  n int;
begin
  n := report_events(dev, jsonb_build_array(
    jsonb_build_object('type','installed_snapshot','v',1,'day',today,'tz','Asia/Kolkata','payload', jsonb_build_object('recipes', jsonb_build_array('desk-deep-work','gym-bag','nope'))),
    jsonb_build_object('type','tap_count','v',1,'day',today,'tz','Asia/Kolkata','payload', jsonb_build_object('recipe','desk-deep-work','count',3)),
    jsonb_build_object('type','tap_count','v',1,'day',today,'tz','Asia/Kolkata','payload', jsonb_build_object('recipe','nope','count',2)),
    jsonb_build_object('type','tap_count','v',1,'day',to_char(current_date - 20, 'YYYY-MM-DD'),'tz','Asia/Kolkata','payload', jsonb_build_object('recipe','gym-bag','count',1)),
    jsonb_build_object('type','tap_count','v',1,'day',today,'payload', jsonb_build_object('recipe','gym-bag','count',1)),
    jsonb_build_object('type','tap_count','v',1,'day',today,'tz','Mars/Olympus','payload', jsonb_build_object('recipe','gym-bag','count',1)),
    jsonb_build_object('type','future_thing','v',1,'payload', jsonb_build_object('x',1))
  ));
  if n <> 4 then raise exception 'expected 4 stored events, got %', n; end if;

  -- same desk count again plus growth: latest count wins, retry changes nothing else
  n := report_events(dev, jsonb_build_array(
    jsonb_build_object('type','tap_count','v',1,'day',today,'tz','Asia/Kolkata','payload', jsonb_build_object('recipe','desk-deep-work','count',5))));
  n := report_events(dev, jsonb_build_array(
    jsonb_build_object('type','tap_count','v',1,'day',today,'tz','Asia/Kolkata','payload', jsonb_build_object('recipe','desk-deep-work','count',5))));

  begin
    perform report_events(dev, (select jsonb_agg(jsonb_build_object('type','future_thing','v',1,'payload','{}'::jsonb)) from generate_series(1, 501)));
    raise exception 'accepted 501 events';
  exception when raise_exception then
    if sqlerrm <> 'too many events' then raise; end if;
  end;

  begin
    perform count(*) from events;
    raise exception 'anon can read events';
  exception when insufficient_privilege then null;
  end;
  begin
    perform count(*) from daily_usage;
    raise exception 'anon can read daily_usage';
  exception when insufficient_privilege then null;
  end;
  begin
    perform count(*) from device_installs;
    raise exception 'anon can read device_installs';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

do $$
declare dev uuid := (select dev from t_ids);
begin
  if (select array_agg(recipe_id order by recipe_id) from device_installs where device_id = dev) <> array['desk-deep-work','gym-bag'] then
    raise exception 'device_installs wrong: %', (select array_agg(recipe_id) from device_installs where device_id = dev);
  end if;
  if (select taps from daily_usage where device_id = dev and recipe_id = 'desk-deep-work') <> 5 then
    raise exception 'desk taps should be 5';
  end if;
  if (select tz from daily_usage where device_id = dev and recipe_id = 'desk-deep-work') <> 'Asia/Kolkata' then
    raise exception 'daily_usage tz not stored';
  end if;
  if (select count(*) from daily_usage where device_id = dev) <> 1 then
    raise exception 'unexpected daily_usage rows: %', (select count(*) from daily_usage where device_id = dev);
  end if;
  if (select count(*) from events where device_id = dev and type = 'future_thing') <> 1 then
    raise exception 'unknown event type not stored';
  end if;
end $$;

set local role anon;
do $$
declare dev uuid := (select dev from t_ids);
begin
  perform report_events(dev, jsonb_build_array(
    jsonb_build_object('type','installed_snapshot','v',1,'day',to_char(current_date, 'YYYY-MM-DD'),'tz','UTC','payload', jsonb_build_object('recipes', jsonb_build_array('gym-bag')))));
end $$;
reset role;

do $$
begin
  if (select array_agg(recipe_id) from device_installs where device_id = (select dev from t_ids)) <> array['gym-bag'] then
    raise exception 'second snapshot should leave only gym-bag';
  end if;
end $$;
