-- In-app feedback: a chat-style message (text and/or attachments) from any phone.
-- Anyone with the app can submit; nobody can read it back through the API (Vineet reads it with the
-- service key via backend/analysis/feedback.py). Attachments live in the private "feedback" bucket
-- under <device_id>/<message_id>/..., insert-only for app clients.

create table if not exists public.feedback (
  id uuid primary key,
  device_id uuid not null,
  created_at timestamptz not null default now(),
  text text,
  attachments jsonb not null default '[]'::jsonb,
  app_version text,
  android_version text,
  device_model text,
  from_screen text
);
alter table public.feedback enable row level security;
create index if not exists feedback_created_at on public.feedback (created_at);

create or replace function public.submit_feedback(
  p_id uuid, p_device_id uuid, p_text text, p_attachments jsonb,
  p_app_version text, p_android_version text, p_device_model text, p_from_screen text
) returns boolean
language plpgsql security definer set search_path = public as $$
declare
  atts jsonb := coalesce(p_attachments, '[]'::jsonb);
  body text := nullif(btrim(coalesce(p_text, '')), '');
begin
  if body is not null and char_length(body) > 4000 then raise exception 'text over 4000 characters'; end if;
  if jsonb_typeof(atts) <> 'array' or jsonb_array_length(atts) > 6 then raise exception 'bad attachments'; end if;
  if body is null and jsonb_array_length(atts) = 0 then raise exception 'empty feedback'; end if;
  if exists (
    select 1 from jsonb_array_elements(atts) a
    where jsonb_typeof(a) <> 'object'
       or coalesce(a->>'path', '') not like p_device_id::text || '/%'
       or coalesce(a->>'type', '') not in ('image', 'audio')
  ) then raise exception 'bad attachment'; end if;
  insert into public.feedback (id, device_id, text, attachments, app_version, android_version, device_model, from_screen)
  values (p_id, p_device_id, body, atts, left(p_app_version, 40), left(p_android_version, 40), left(p_device_model, 80), left(p_from_screen, 40))
  on conflict (id) do nothing;
  return true;
end $$;
revoke all on function public.submit_feedback(uuid, uuid, text, jsonb, text, text, text, text) from public;
grant execute on function public.submit_feedback(uuid, uuid, text, jsonb, text, text, text, text) to anon, authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('feedback', 'feedback', false, 5242880, array['image/jpeg', 'image/png', 'image/webp', 'audio/mp4', 'audio/m4a', 'audio/aac'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "feedback uploads" on storage.objects;
create policy "feedback uploads" on storage.objects for insert to anon, authenticated
  with check (
    bucket_id = 'feedback'
    and (storage.foldername(name))[1] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and array_length(storage.foldername(name), 1) = 2
  );
