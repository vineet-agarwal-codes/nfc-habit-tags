-- Zone lab events are stored by report_events and survive the 90-day purge; other events don't.
create temp table t_ids as select '00000000-0000-0000-0000-00000000aaaa'::uuid as dev;
grant select on t_ids to anon;

set local role anon;
do $$
declare
  dev uuid := (select dev from t_ids);
  today text := to_char((now() at time zone 'Asia/Kolkata')::date, 'YYYY-MM-DD');
  n int;
begin
  n := report_events(dev, jsonb_build_array(
    jsonb_build_object('type','zone_recording','v',1,'day',today,'tz','Asia/Kolkata','at','2026-11-15T19:00:00Z',
      'payload', jsonb_build_object('rec','r1','spot','Desk','mode','resting','samples',4)),
    jsonb_build_object('type','zone_sample','v',1,'day',today,'tz','Asia/Kolkata','at','2026-11-15T19:00:00Z',
      'payload', jsonb_build_object('source','recording','rec','r1','spot','Desk','wifi', jsonb_build_array(jsonb_build_object('b','aa:bb','r',-50,'f',2412))))
  ));
  if n <> 2 then raise exception 'expected 2 stored zone events, got %', n; end if;
end $$;
reset role;

update public.events set received_at = now() - interval '120 days'
where device_id = (select dev from t_ids);
insert into public.events(device_id, type, v, payload, received_at)
values ((select dev from t_ids), 'future_thing', 1, '{}', now() - interval '120 days');

-- the purge job's own command
do $$ begin execute (select command from cron.job where jobname = 'purge-old-usage'); end $$;

do $$
declare zone_left int; other_left int;
begin
  select count(*) filter (where type like 'zone\_%'), count(*) filter (where type not like 'zone\_%')
    into zone_left, other_left from public.events where device_id = (select dev from t_ids);
  if zone_left <> 2 then raise exception 'zone events purged: % left', zone_left; end if;
  if other_left <> 0 then raise exception 'old non-zone events kept: %', other_left; end if;
end $$;
