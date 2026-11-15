-- Public, aggregated stats per published recipe. Test devices (00000000-0000-0000-0000-…) are excluded.
create extension if not exists pg_cron;

create materialized view public.recipe_stats as
select r.id as recipe_id,
       coalesce(u.weekly_active, 0)::int as weekly_active,
       coalesce(i.installs, 0)::int as installs,
       now() as refreshed_at
from public.recipes r
left join (
  select recipe_id, count(distinct device_id) as weekly_active
  from public.daily_usage
  where taps > 0
    and day >= (now() at time zone tz)::date - 6
    and device_id::text not like '00000000-0000-0000-0000-%'
  group by recipe_id
) u on u.recipe_id = r.id
left join (
  select recipe_id, count(distinct device_id) as installs
  from public.device_installs
  where last_seen >= current_date - 30
    and device_id::text not like '00000000-0000-0000-0000-%'
  group by recipe_id
) i on i.recipe_id = r.id
where r.published;

create unique index recipe_stats_recipe on public.recipe_stats(recipe_id);
revoke all on public.recipe_stats from anon, authenticated;
grant select on public.recipe_stats to anon, authenticated;

select cron.schedule('refresh-recipe-stats', '5 * * * *', 'refresh materialized view concurrently public.recipe_stats');
select cron.schedule('purge-old-usage', '17 3 * * *', $$
  delete from public.events where received_at < now() - interval '90 days';
  delete from public.daily_usage where day < current_date - 90;
$$);
