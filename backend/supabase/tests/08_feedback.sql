-- submit_feedback accepts text and/or attachments under the device's folder, rejects the rest, and
-- anon can't read feedback back.
do $$
declare dev uuid := '00000000-0000-0000-0000-00000000f00d';
begin
  set local role anon;
  perform public.submit_feedback('10000000-0000-0000-0000-000000000001', dev, 'Love the desk tag', '[]', '1.0.0', '15', 'Pixel', 'home');
  perform public.submit_feedback('10000000-0000-0000-0000-000000000002', dev, null,
    jsonb_build_array(jsonb_build_object('path', dev::text || '/m2/0.jpg', 'type', 'image', 'bytes', 1200)), '1.0.0', '15', 'Pixel', 'home');
  -- the same id again is a no-op (retries)
  perform public.submit_feedback('10000000-0000-0000-0000-000000000001', dev, 'Love the desk tag', '[]', '1.0.0', '15', 'Pixel', 'home');
  begin
    perform public.submit_feedback(gen_random_uuid(), dev, '   ', '[]', null, null, null, null);
    raise exception 'empty feedback was accepted';
  exception when raise_exception then if sqlerrm = 'empty feedback was accepted' then raise; end if;
  end;
  begin
    perform public.submit_feedback(gen_random_uuid(), dev, repeat('x', 4001), '[]', null, null, null, null);
    raise exception 'long text was accepted';
  exception when raise_exception then if sqlerrm = 'long text was accepted' then raise; end if;
  end;
  begin
    perform public.submit_feedback(gen_random_uuid(), dev, 'hi',
      '[{"path": "someone-else/x.jpg", "type": "image"}]', null, null, null, null);
    raise exception 'foreign path was accepted';
  exception when raise_exception then if sqlerrm = 'foreign path was accepted' then raise; end if;
  end;
  begin
    perform count(*) from public.feedback;
    if (select count(*) from public.feedback) > 0 then raise exception 'anon can read feedback'; end if;
  exception when insufficient_privilege then null;
  end;
  reset role;
  if (select count(*) from public.feedback where device_id = dev) <> 2 then
    raise exception 'expected 2 feedback rows, got %', (select count(*) from public.feedback where device_id = dev);
  end if;
end $$;
