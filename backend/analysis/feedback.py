#!/usr/bin/env python3
"""List in-app feedback and download its attachments for review.

    python3 backend/analysis/feedback.py                    # feedback from the last 7 days
    python3 backend/analysis/feedback.py --since 2026-11-20  # since a date or ISO timestamp
    python3 backend/analysis/feedback.py --all
    python3 backend/analysis/feedback.py --delete-device 00000000-0000-0000-0000-000000002012

Reads ~/.config/habit-tags/supabase.env (Management API token). The service key is fetched at run time
and never printed. Attachments are saved under ~/work/nfc-habit-tags-feedback/<date>_<id>/ (outside the
repo); each message's text and metadata is printed with the local paths of its files.
"""
import argparse
import datetime as dt
import json
import os
import sys
import urllib.request

OUT = os.path.expanduser("~/work/nfc-habit-tags-feedback")


def env():
    vals = {}
    with open(os.path.expanduser("~/.config/habit-tags/supabase.env")) as f:
        for line in f:
            if "=" in line:
                k, v = line.strip().split("=", 1)
                vals[k] = v
    return vals


def mgmt(e, path, body=None):
    req = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{e['SUPABASE_PROJECT_REF']}{path}",
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": f"Bearer {e['SUPABASE_ACCESS_TOKEN']}", "Content-Type": "application/json",
                 "User-Agent": "mise-feedback"},
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def sql(e, query):
    return mgmt(e, "/database/query", {"query": query})


def service_key(e):
    keys = mgmt(e, "/api-keys?reveal=true")
    for k in keys:
        if k.get("name") == "service_role" or k.get("type") == "secret":
            return k["api_key"]
    sys.exit("no service key available from the Management API")


def storage(e, key, method, path, body=None):
    req = urllib.request.Request(
        f"{e['SUPABASE_URL']}/storage/v1/{path}", method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"apikey": key, "Authorization": f"Bearer {key}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req) as r:
        return r.read()


def literal(s):
    return "'" + s.replace("'", "''") + "'"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--since", help="date or ISO timestamp (default: 7 days ago)")
    p.add_argument("--all", action="store_true")
    p.add_argument("--no-download", action="store_true")
    p.add_argument("--delete-device", help="delete all feedback and files from this device id (tests)")
    a = p.parse_args()
    e = env()

    if a.delete_device:
        dev = a.delete_device
        rows = sql(e, f"select attachments from public.feedback where device_id = {literal(dev)}::uuid")
        paths = [x["path"] for r in rows for x in (r["attachments"] or [])]
        listed = sql(e, f"select name from storage.objects where bucket_id = 'feedback' and name like {literal(dev + '/%')}")
        paths = sorted(set(paths) | {r["name"] for r in listed})
        if paths:
            storage(e, service_key(e), "DELETE", "object/feedback", {"prefixes": paths})
        sql(e, f"delete from public.feedback where device_id = {literal(dev)}::uuid")
        print(f"deleted {len(rows)} feedback rows and {len(paths)} files for {dev}")
        return

    since = None if a.all else (a.since or (dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=7)).isoformat())
    where = f"where created_at >= {literal(since)}::timestamptz" if since else ""
    rows = sql(e, "select id::text, device_id::text, created_at, text, attachments, app_version, android_version, "
                  f"device_model, from_screen from public.feedback {where} order by created_at")
    print(f"{len(rows)} feedback message(s)" + (f" since {since}" if since else ""))
    key = None
    for r in rows:
        print("-" * 60)
        print(f"{r['created_at']}  device {r['device_id'][:8]}  app {r['app_version']}  Android {r['android_version']}  "
              f"{r['device_model']}  from {r['from_screen']}")
        if r["text"]:
            print(r["text"])
        for att in r["attachments"] or []:
            line = f"  [{att.get('type')}] {att.get('path')} ({att.get('bytes', '?')} bytes)"
            if not a.no_download:
                key = key or service_key(e)
                folder = os.path.join(OUT, f"{r['created_at'][:10]}_{r['id'][:8]}")
                os.makedirs(folder, exist_ok=True)
                dest = os.path.join(folder, os.path.basename(att["path"]))
                if not os.path.exists(dest):
                    with open(dest, "wb") as f:
                        f.write(storage(e, key, "GET", f"object/feedback/{att['path']}"))
                line += f" -> {dest}"
            print(line)


if __name__ == "__main__":
    main()
