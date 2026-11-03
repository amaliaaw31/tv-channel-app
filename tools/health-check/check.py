#!/usr/bin/env python3
"""Daily health check of every stream in data/channels.json, measured from this machine (the VPS).

    python3 tools/health-check/check.py          # print the report, keep the result in ~/.tvchannel-health/last.json
    python3 tools/health-check/check.py --dir X  # keep state somewhere else

A channel is "ok" when its playlist is HLS and the first media segment starts to download, "geo" for HTTP 403/451,
"dead" for anything else (404/410, other HTTP errors, timeouts, DNS, not HLS). Exit code 1 when a channel that was ok
last time is not ok now (so cron mail or a log grep can notice). A VPS in Singapore is not the TV: a "geo" or "dead"
here can still play at home, and the reverse; confirm in the TV (AuditActivity) before dropping a channel
(docs/sources.md).
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urljoin

ROOT = Path(__file__).resolve().parent.parent.parent
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"


def classify(status, error, is_hls):
    """Pure: "ok" | "geo" | "dead" from the last HTTP status (None if none), the error name (or None) and HLS-ness."""
    if error is None and is_hls and status == 200:
        return "ok"
    return "geo" if status in (403, 451) else "dead"


def get(url, headers, limit=None, timeout=12):
    req = urllib.request.Request(url, headers={"User-Agent": UA, **headers})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read(limit) if limit else r.read()


def check(entry):
    headers, url = entry.get("headers", {}), entry["streamUrl"]
    status = error = None
    hls = False
    try:
        status, body = get(url, headers)
        text = body.decode("utf-8", "replace")
        hls = text.lstrip("﻿ \t\r\n").startswith("#EXTM3U") and "#EXT-X-" in text
        for _ in range(2):  # master -> media playlist -> a segment
            lines = [l.strip() for l in text.splitlines() if l.strip() and not l.startswith("#")]
            if not (hls and lines):
                break
            url = urljoin(url, lines[0])
            status, body = get(url, headers, limit=16384)
            text = body.decode("utf-8", "replace") if b"#EXTM3U" in body[:16] else ""
            if not text:
                break
    except urllib.error.HTTPError as e:
        status, error = e.code, "HTTPError"
    except Exception as e:
        error = type(e).__name__
    return {"id": entry["id"], "name": entry["name"], "state": classify(status, error, hls), "status": status, "error": error}


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--dir", type=Path, default=Path.home() / ".tvchannel-health")
    args = ap.parse_args()
    channels = json.loads((ROOT / "data" / "channels.json").read_text())
    with ThreadPoolExecutor(8) as pool:
        rows = list(pool.map(check, channels))
    args.dir.mkdir(parents=True, exist_ok=True)
    last = args.dir / "last.json"
    before = {r["id"]: r["state"] for r in json.loads(last.read_text())["rows"]} if last.exists() else {}
    last.write_text(json.dumps({"at": time.strftime("%Y-%m-%d %H:%M:%S"), "rows": rows}, ensure_ascii=False, indent=1))
    worse = [r for r in rows if r["state"] != "ok" and before.get(r["id"]) == "ok"]
    better = [r for r in rows if r["state"] == "ok" and before.get(r["id"]) not in (None, "ok")]
    counts = {s: sum(r["state"] == s for r in rows) for s in ("ok", "geo", "dead")}
    print(f"{time.strftime('%Y-%m-%d %H:%M')} {len(rows)} channel: {counts}")
    for title, group in (("BARU BERMASALAH", worse), ("PULIH", better)):
        for r in group:
            print(f"  {title}: {r['name']} ({r['id']}) -> {r['state']} status={r['status']} {r['error'] or ''}")
    for r in rows:
        if r["state"] != "ok" and r not in worse:
            print(f"  masih {r['state']}: {r['name']} status={r['status']} {r['error'] or ''}")
    return 1 if worse else 0


if __name__ == "__main__":
    sys.exit(main())
