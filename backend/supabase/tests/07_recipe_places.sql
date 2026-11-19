-- Every published recipe has a Discover place group and a space name.
do $$
declare missing text;
begin
  select string_agg(id, ', ') into missing from public.recipes
    where published and not (doc ? 'place' and doc->'space' ? 'name');
  if missing is not null then raise exception 'recipes without place/space: %', missing; end if;
end $$;
