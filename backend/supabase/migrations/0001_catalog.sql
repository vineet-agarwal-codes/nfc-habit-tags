-- Catalog: each recipe / app group is one JSON document; columns only for what we query.
create table public.recipes (
  id text primary key,
  doc jsonb not null,
  published boolean not null default false,
  published_at timestamptz,
  sort_hint int not null default 100,
  updated_at timestamptz not null default now(),
  check (doc->>'id' = id)
);

create table public.app_groups (
  id text primary key,
  doc jsonb not null,
  updated_at timestamptz not null default now()
);

create function public.touch_updated_at() returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

create trigger recipes_touch before update on public.recipes for each row execute function public.touch_updated_at();
create trigger app_groups_touch before update on public.app_groups for each row execute function public.touch_updated_at();

alter table public.recipes enable row level security;
alter table public.app_groups enable row level security;

revoke all on public.recipes, public.app_groups from anon, authenticated;
grant select on public.recipes, public.app_groups to anon, authenticated;

create policy recipes_read_published on public.recipes for select to anon, authenticated using (published);
create policy app_groups_read on public.app_groups for select to anon, authenticated using (true);

alter publication supabase_realtime add table public.recipes, public.app_groups;
