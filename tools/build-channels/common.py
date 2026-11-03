"""Helpers shared by curate.py and finalize.py."""
import json
import re
import urllib.request
from pathlib import Path

API = "https://iptv-org.github.io/api/"
ROOT = Path(__file__).resolve().parent.parent.parent
COUNTRY_FIXES = {"UK": "GB"}  # iptv-org writes UK; ISO 3166-1 alpha-2 is GB
PROBE_BYTES = 16384


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


def languages_for(entry_id, feeds):
    """ISO 639-3 languages of a curated entry id ("channel" or "channel#feed") from iptv-org feeds.json rows.
    The named feed wins, else the main feed, else every feed of the channel; [] when iptv-org has no data."""
    channel, _, feed = entry_id.partition("#")
    rows = [f for f in feeds if f.get("channel") == channel]
    chosen = [f for f in rows if feed and f.get("id") == feed] or [f for f in rows if f.get("is_main")] or rows
    return sorted({l.lower() for f in chosen for l in (f.get("languages") or []) if l})


def fetch_json(name):
    with urllib.request.urlopen(API + name + ".json", timeout=120) as r:
        return json.load(r)


def write_entries(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    # One channel per line keeps git diffs reviewable.
    body = ",\n".join("  " + json.dumps(e, ensure_ascii=False) for e in entries)
    path.write_text("[\n" + body + "\n]\n")
