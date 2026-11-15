-- Clients (anon) see only published recipes and all app groups, and cannot write.
insert into recipes(id, doc, published) values ('hidden-test', '{"id":"hidden-test"}', false);

set local role anon;
do $$
begin
  if (select count(*) from recipes) <> 4 then
    raise exception 'anon should see 4 published recipes, saw %', (select count(*) from recipes);
  end if;
  if exists (select 1 from recipes where id = 'hidden-test') then
    raise exception 'anon can see an unpublished recipe';
  end if;
  if (select count(*) from app_groups) <> 4 then
    raise exception 'anon should see 4 app groups, saw %', (select count(*) from app_groups);
  end if;
  begin
    insert into recipes(id, doc, published) values ('evil', '{"id":"evil"}', true);
    raise exception 'anon insert into recipes succeeded';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

do $$
begin
  begin
    insert into recipes(id, doc) values ('mismatch', '{"id":"other"}');
    raise exception 'doc id mismatch was accepted';
  exception when check_violation then null;
  end;
end $$;
