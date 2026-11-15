#!/usr/bin/env python3
"""Zone lab analysis: how well can Wi-Fi + phone sensors tell the user's spots apart?

Reads zone_* events (from Supabase via the Management API, or a JSON file with --input) and prints:
  * leave-one-recording-out accuracy per spot x mode x feature set, using the same weighted KNN as
    the app (android/core/.../zone/ZoneClassifier.kt — keep the two in step),
  * accuracy of the app's own guesses from Right/Wrong and notification feedback,
  * how often Wi-Fi scans were fresh vs throttled/cached.

Usage: python3 zone_lab.py [--input events.json] [--device <uuid>]
Live mode reads SUPABASE_ACCESS_TOKEN and SUPABASE_PROJECT_REF from ~/.config/habit-tags/supabase.env.
"""
import argparse
import json
import math
import os
import statistics
import urllib.request
from collections import defaultdict

MISSING_DBM = -100
K = 5
MAG_WEIGHT = 1.0
ORIENT_WEIGHT = 0.1
FEATURE_SETS = {"wifi": (False, False), "wifi+mag": (True, False), "wifi+mag+orient": (True, True)}


def wifi_distance(a, b):
    ra = {x["b"]: x["r"] for x in a}
    rb = {x["b"]: x["r"] for x in b}
    keys = set(ra) | set(rb)
    if not keys:
        return 0.0
    return math.sqrt(sum((ra.get(k, MISSING_DBM) - rb.get(k, MISSING_DBM)) ** 2 for k in keys) / len(keys))


def angle_degrees(a, b):
    if not a or not b or len(a) != 3 or len(b) != 3:
        return None
    na, nb = math.sqrt(sum(x * x for x in a)), math.sqrt(sum(x * x for x in b))
    if na == 0 or nb == 0:
        return None
    cos = max(-1.0, min(1.0, sum(x * y for x, y in zip(a, b)) / (na * nb)))
    return math.degrees(math.acos(cos))


def distance(a, b, magnet, orientation):
    total = wifi_distance(a.get("wifi", []), b.get("wifi", [])) ** 2
    if magnet and a.get("magMean") is not None and b.get("magMean") is not None:
        total += (MAG_WEIGHT * abs(a["magMean"] - b["magMean"])) ** 2
    if orientation:
        ang = angle_degrees(a.get("gravity"), b.get("gravity"))
        if ang is not None:
            total += (ORIENT_WEIGHT * ang) ** 2
    return math.sqrt(total)


def guess(train, sample, magnet, orientation, k=K):
    """train: list of (spot, sample). Returns (spot, confidence) or None."""
    neighbours = sorted(((distance(sample, s, magnet, orientation), spot) for spot, s in train), key=lambda x: x[0])[:k]
    if not neighbours:
        return None
    weights = defaultdict(float)
    for d, spot in neighbours:
        weights[spot] += 1.0 / (1.0 + d)
    total = sum(weights.values())
    spot = max(weights, key=weights.get)
    return spot, weights[spot] / total


def load_live(device):
    env = {}
    with open(os.path.expanduser("~/.config/habit-tags/supabase.env")) as f:
        for line in f:
            if "=" in line:
                k, v = line.strip().split("=", 1)
                env[k] = v
    where = "type like 'zone\\_%'"
    if device:
        where += f" and device_id = '{device}'::uuid"
    query = f"select device_id::text as device_id, type, at, payload from public.events where {where} order by id"
    req = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{env['SUPABASE_PROJECT_REF']}/database/query",
        data=json.dumps({"query": query}).encode(),
        headers={"Authorization": f"Bearer {env['SUPABASE_ACCESS_TOKEN']}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def analyse(rows):
    """Returns a dict of results; print_report formats it."""
    recordings = defaultdict(list)  # (device, rec) -> [(spot, mode, sample)]
    observed = {}  # (device, id) -> payload
    feedback = []
    for row in rows:
        p = row["payload"] if isinstance(row["payload"], dict) else json.loads(row["payload"])
        dev = row["device_id"]
        if row["type"] == "zone_sample" and p.get("source") == "recording":
            recordings[(dev, p["rec"])].append((p["spot"], p["mode"], p))
        elif row["type"] == "zone_sample":
            observed[(dev, p.get("id"))] = p
        elif row["type"] == "zone_guess_feedback":
            feedback.append((dev, p))

    loro = {}
    for name, (magnet, orientation) in FEATURE_SETS.items():
        cells = defaultdict(lambda: [0, 0])  # (spot, mode) -> [correct, total]
        for (dev, rec), samples in recordings.items():
            train = [(spot, s) for (d, r), ss in recordings.items() if d == dev and r != rec for spot, _, s in ss]
            for spot, mode, s in samples:
                g = guess(train, s, magnet, orientation)
                cell = cells[(spot, mode)]
                cell[1] += 1
                if g and g[0] == spot:
                    cell[0] += 1
        loro[name] = dict(cells)

    fb = defaultdict(lambda: [0, 0])
    for dev, p in feedback:
        cell = fb[(p.get("source", "?"), p["actual"])]
        cell[1] += 1
        if p.get("correct"):
            cell[0] += 1

    scans = defaultdict(lambda: {"n": 0, "fresh": 0, "refused": 0, "ages": []})
    for (_, _, s) in [x for ss in recordings.values() for x in ss]:
        _scan_stats(scans["recording"], s)
    for p in observed.values():
        _scan_stats(scans[p.get("source", "?")], p)

    return {"recordings": len(recordings), "loro": loro, "feedback": dict(fb), "scans": dict(scans)}


def _scan_stats(acc, s):
    acc["n"] += 1
    acc["fresh"] += 1 if s.get("scanFresh", True) else 0
    acc["refused"] += 1 if s.get("startScanReturned") is False else 0
    if s.get("scanAgeMs") is not None:
        acc["ages"].append(s["scanAgeMs"])


def pct(c):
    return f"{100 * c[0] / c[1]:5.1f}% ({c[0]}/{c[1]})" if c[1] else "    -"


def print_report(res):
    print(f"Recordings: {res['recordings']}\n")
    print("Leave-one-recording-out accuracy (spot / mode):")
    keys = sorted({k for cells in res["loro"].values() for k in cells})
    print(f"  {'spot':<12}{'mode':<9}" + "".join(f"{n:>22}" for n in FEATURE_SETS))
    for key in keys:
        print(f"  {key[0]:<12}{key[1]:<9}" + "".join(f"{pct(res['loro'][n].get(key, [0, 0])):>22}" for n in FEATURE_SETS))
    for n in FEATURE_SETS:
        tot = [sum(c[0] for c in res["loro"][n].values()), sum(c[1] for c in res["loro"][n].values())]
        print(f"  overall {n}: {pct(tot)}")
    print("\nApp guesses confirmed by feedback (source / actual spot):")
    for (source, actual), c in sorted(res["feedback"].items()):
        print(f"  {source:<14}{actual:<12}{pct(c)}")
    print("\nWi-Fi scans:")
    for source, a in sorted(res["scans"].items()):
        age = f"median age {statistics.median(a['ages']) / 1000:.1f}s" if a["ages"] else "no ages"
        print(f"  {source:<12} {a['n']} samples, fresh {pct([a['fresh'], a['n']])}, scan refused {a['refused']}, {age}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", help="JSON file of event rows instead of querying Supabase")
    ap.add_argument("--device", help="only this device_id")
    args = ap.parse_args()
    rows = json.load(open(args.input)) if args.input else load_live(args.device)
    print_report(analyse(rows))


if __name__ == "__main__":
    main()
