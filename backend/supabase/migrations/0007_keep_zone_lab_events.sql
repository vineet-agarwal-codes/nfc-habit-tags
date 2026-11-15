-- Zone lab data (zone_* events) is research data for designing software zones: keep it out of the
-- 90-day purge. Index it for the analysis script.
select cron.schedule('purge-old-usage', '17 3 * * *', $$
  delete from public.events where received_at < now() - interval '90 days' and type not like 'zone\_%';
  delete from public.daily_usage where day < current_date - 90;
$$);

create index if not exists events_zone_idx on public.events (type, received_at) where type like 'zone\_%';
