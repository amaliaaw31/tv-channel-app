#!/usr/bin/env python3
"""Build data/channels.json from the iptv-org API.

Fetches channels/streams/blocklist/categories/logos, drops blocked, NSFW and closed
channels, keeps the categories listed in config.json, probes each channel's streams
(first valid HLS playlist wins) and writes the app's channels.json schema.

    python3 tools/build-channels/build_channels.py              # write data/channels.json
    python3 tools/build-channels/build_channels.py --publish    # ...then git commit + push
    python3 tools/build-channels/build_channels.py --limit 50   # quick trial run (not for publishing)

Stdlib only. Rules and sources: docs/sources.md.
"""
import argparse
import json
import re
import subprocess
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

API = "https://iptv-org.github.io/api/"
HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
OUT = ROOT / "data" / "channels.json"
EXCLUDE = HERE / "exclude.json"
ISO2 = re.compile(r"^[A-Z]{2}$")
COUNTRY_FIXES = {"UK": "GB"}  # iptv-org writes UK; ISO 3166-1 alpha-2 is GB
PROBE_BYTES = 16384


# ---- pure logic (covered by test_build_channels.py) -------------------------

def select_channels(channels, blocked_ids, wanted):
    """Channels that pass every filter, as {id, name, country, category} in input order.

    A channel in several wanted categories gets the first one in `wanted` (config order = priority).
    """
    out = []
    for c in channels:
        if c.get("closed") or c.get("is_nsfw") or c["id"] in blocked_ids:
            continue
        categories = c.get("categories") or []
        category = next((w for w in wanted if w in categories), None)
        country = COUNTRY_FIXES.get(c.get("country"), c.get("country"))
        if category is None or not isinstance(country, str) or not ISO2.match(country):
            continue
        out.append({"id": c["id"], "name": (c.get("name") or c["id"]).strip(), "country": country, "category": category})
    return out


def quality_rank(quality):
    m = re.match(r"(\d+)", quality or "")
    return int(m.group(1)) if m else 0


def order_streams(streams):
    """Best first: highest resolution, then https over http."""
    usable = [s for s in streams if s.get("url", "").startswith(("http://", "https://"))]
    return sorted(usable, key=lambda s: (-quality_rank(s.get("quality")), not s["url"].startswith("https://"), s["url"]))


def is_hls(text):
    t = text.lstrip("﻿ \t\r\n")
    # "#EXT-X-" separates real HLS from plain m3u lists, which start with #EXTM3U too.
    return t.startswith("#EXTM3U") and "#EXT-X-" in t


def to_entry(candidate, stream, logo):
    entry = {k: candidate[k] for k in ("id", "name", "country", "category")}
    if logo:
        entry["logo"] = logo
    entry["streamUrl"] = stream["url"]
    headers = {}
    if stream.get("user_agent"):
        headers["User-Agent"] = stream["user_agent"]
    if stream.get("referrer"):
        headers["Referer"] = stream["referrer"]
    if headers:
        entry["headers"] = headers
    return entry


def pick_logos(logos):
    """channel id -> logo URL; prefers logos that are in use and not tied to a specific feed."""
    best = {}
    for l in logos:
        score = (bool(l.get("in_use")), l.get("feed") is None)
        if l["channel"] not in best or score > best[l["channel"]][0]:
            best[l["channel"]] = (score, l["url"])
    return {k: v[1] for k, v in best.items()}


# ---- network ----------------------------------------------------------------

def fetch_json(name):
    with urllib.request.urlopen(API + name + ".json", timeout=120) as r:
        return json.load(r)


def check_stream(stream, timeout):
    """True if the URL answers 200 with an HLS playlist within the time budget."""
    headers = {"User-Agent": stream.get("user_agent") or "Mozilla/5.0"}
    if stream.get("referrer"):
        headers["Referer"] = stream["referrer"]
    deadline = time.monotonic() + timeout
    try:
        with urllib.request.urlopen(urllib.request.Request(stream["url"], headers=headers), timeout=timeout) as r:
            if r.status != 200:
                return False
            buf = b""
            while len(buf) < PROBE_BYTES and time.monotonic() < deadline:
                chunk = r.read(4096)
                if not chunk:
                    break
                buf += chunk
        return is_hls(buf.decode("utf-8", "ignore"))
    except Exception:  # any failure (DNS, TLS, timeout, HTTP error) means "not usable"
        return False


def probe(candidate, streams, cfg):
    for s in order_streams(streams)[: cfg["max_streams_tried_per_channel"]]:
        if check_stream(s, cfg["timeout_seconds"]):
            return s
    return None


# ---- main -------------------------------------------------------------------

def write_entries(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    # One channel per line keeps git diffs reviewable.
    body = ",\n".join("  " + json.dumps(e, ensure_ascii=False) for e in entries)
    path.write_text("[\n" + body + "\n]\n")


def load_excluded():
    """Ids in exclude.json (written by prune_audit.py): channels known not to work on the target TV."""
    try:
        return {e["id"] for e in json.loads(EXCLUDE.read_text())}
    except (OSError, ValueError):
        return set()


def previous_count(path):
    try:
        return len(json.loads(path.read_text()))
    except (OSError, ValueError):
        return 0


def git(*args):
    subprocess.run(["git", "-C", str(ROOT), *args], check=True)


def publish(count):
    git("add", "data/channels.json")
    if subprocess.run(["git", "-C", str(ROOT), "diff", "--cached", "--quiet", "--", "data/channels.json"]).returncode == 0:
        print("publish: no changes")
        return
    git("commit", "-m", f"Update channels data ({count} channels)", "--", "data/channels.json")
    git("push")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--publish", action="store_true", help="git commit + push data/channels.json")
    ap.add_argument("--limit", type=int, help="only probe the first N candidate channels")
    ap.add_argument("--out", type=Path, default=OUT)
    ap.add_argument("--force", action="store_true", help="write even if the result is suspiciously small")
    args = ap.parse_args()
    cfg = json.loads((HERE / "config.json").read_text())

    print("fetching API data...")
    channels, streams, blocklist = fetch_json("channels"), fetch_json("streams"), fetch_json("blocklist")
    known = {c["id"] for c in fetch_json("categories")}
    unknown = [c for c in cfg["categories"] if c not in known]
    if unknown:
        sys.exit(f"config.json: unknown categories {unknown}; valid: {sorted(known)}")
    logos = pick_logos(fetch_json("logos"))

    excluded = load_excluded()
    candidates = select_channels(channels, {b["channel"] for b in blocklist} | excluded, cfg["categories"])
    by_channel = {}
    for s in streams:
        if s.get("channel"):
            by_channel.setdefault(s["channel"], []).append(s)
    candidates = [c for c in candidates if c["id"] in by_channel]
    if args.limit:
        candidates = candidates[: args.limit]
    print(f"candidates: {len(candidates)} channels with streams (blocklist={len(blocklist)}, excluded={len(excluded)})")

    with ThreadPoolExecutor(cfg["workers"]) as pool:
        results = list(pool.map(lambda c: probe(c, by_channel[c["id"]], cfg), candidates))
    entries = [to_entry(c, s, logos.get(c["id"])) for c, s in zip(candidates, results) if s]
    entries.sort(key=lambda e: (e["country"], e["name"].lower(), e["id"]))
    print(f"valid HLS: {len(entries)} of {len(candidates)}")

    floor = max(cfg["min_channels"], int(previous_count(args.out) * cfg["min_keep_ratio"]))
    if len(entries) < floor and not args.force:
        sys.exit(f"refusing to write: {len(entries)} channels is below the safety floor {floor} "
                 "(network problem on this machine?). Use --force to override.")

    write_entries(args.out, entries)
    print(f"wrote {args.out} ({args.out.stat().st_size // 1024} KB)")
    if args.publish:
        publish(len(entries))


if __name__ == "__main__":
    main()
