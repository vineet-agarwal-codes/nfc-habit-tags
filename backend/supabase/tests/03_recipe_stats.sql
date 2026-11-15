-- recipe_stats: weekly active phones (each day judged in its own zone) and recent installs; test devices excluded.
-- Compared against a baseline so real production usage doesn't affect the test.
refresh materialized view recipe_stats;
create temp table t_base as select recipe_id, weekly_active, installs from recipe_stats;
insert into daily_usage(device_id, recipe_id, day, tz, taps)
select ('22222222-0000-0000-0000-00000000000' || g)::uuid, 'desk-deep-work', (now() at time zone 'Asia/Kolkata')::date, 'Asia/Kolkata', 2
from generate_series(1, 6) g;
insert into daily_usage values ('33333333-0000-0000-0000-000000000001', 'desk-deep-work', (now() at time zone 'Asia/Kolkata')::date - 8, 'Asia/Kolkata', 4);
insert into daily_usage values ('00000000-0000-0000-0000-000000000009', 'desk-deep-work', (now() at time zone 'Asia/Kolkata')::date, 'Asia/Kolkata', 4);
insert into daily_usage values ('33333333-0000-0000-0000-000000000002', 'desk-deep-work', (now() at time zone 'Asia/Kolkata')::date, 'Asia/Kolkata', 0);

insert into device_installs
select ('44444444-0000-0000-0000-00000000000' || g)::uuid, 'desk-deep-work', current_date from generate_series(1, 6) g;
insert into device_installs values ('44444444-0000-0000-0000-000000000099', 'desk-deep-work', current_date - 31);
insert into device_installs values ('00000000-0000-0000-0000-000000000009', 'desk-deep-work', current_date);

refresh materialized view recipe_stats;

do $$
begin
  if (select s.weekly_active - b.weekly_active from recipe_stats s join t_base b using (recipe_id) where recipe_id = 'desk-deep-work') <> 6 then
    raise exception 'desk weekly_active should grow by 6';
  end if;
  if (select s.installs - b.installs from recipe_stats s join t_base b using (recipe_id) where recipe_id = 'desk-deep-work') <> 6 then
    raise exception 'desk installs should grow by 6';
  end if;
  if (select (s.weekly_active - b.weekly_active, s.installs - b.installs) from recipe_stats s join t_base b using (recipe_id) where recipe_id = 'front-door') <> row(0, 0) then
    raise exception 'untouched recipe changed';
  end if;
  if (select count(*) from cron.job where jobname in ('refresh-recipe-stats', 'purge-old-usage')) <> 2 then
    raise exception 'cron jobs missing';
  end if;
end $$;

set local role anon;
do $$
begin
  if (select count(*) from recipe_stats) < 4 then raise exception 'anon cannot read recipe_stats'; end if;
end $$;
reset role;
