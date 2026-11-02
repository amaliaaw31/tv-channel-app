#!/usr/bin/env python3
"""Remove channels from data/channels.json using the results of the on-device audit.

The audit (debug build, AuditActivity) plays every channel on the TV and records how it ended.
This script drops the channels that ended in the given error titles and remembers them in
exclude.json, so build_channels.py will not bring them back. See docs/sources.md.

    python3 tools/build-channels/prune_audit.py audit.jsonl              # default titles
    python3 tools/build-channels/prune_audit.py audit.jsonl --dry-run    # only report
"""
import argparse
import datetime
import json
from collections import Counter
from pathlib import Path

import build_channels as b

DEFAULT_TITLES = ["Format tidak didukung", "Dibatasi wilayah"]


def parse_audit(lines):
    """Last audit record per channel id (a channel audited twice keeps its newest result)."""
    latest = {}
    for line in lines:
        if line.strip():
            r = json.loads(line)
            latest[r["id"]] = r
    return latest


def decide(lines, titles):
    """channel id -> error title, for channels whose latest audit is an error with one of `titles`."""
    return {i: r["title"] for i, r in parse_audit(lines).items() if r.get("result") == "error" and r.get("title") in titles}


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("audit", type=Path, help="audit.jsonl pulled from the TV")
    ap.add_argument("--titles", default=",".join(DEFAULT_TITLES), help="comma-separated error titles to remove")
    ap.add_argument("--data", type=Path, default=b.OUT)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    lines = args.audit.read_text().splitlines()
    audit = parse_audit(lines)
    remove = decide(lines, set(args.titles.split(",")))
    entries = json.loads(args.data.read_text())
    kept = [e for e in entries if e["id"] not in remove]
    not_audited = sum(1 for e in entries if e["id"] not in audit)

    print(f"audited {len(audit)} channels: {dict(Counter(r['result'] for r in audit.values()))}")
    print(f"errors by title: {dict(Counter(r['title'] for r in audit.values() if r.get('result') == 'error'))}")
    print(f"in data but not audited: {not_audited}")
    print(f"removing {len(entries) - len(kept)} of {len(entries)}: {dict(Counter(remove[e['id']] for e in entries if e['id'] in remove))}")
    if args.dry_run:
        return

    today = datetime.date.today().isoformat()
    try:
        excluded = {e["id"]: e for e in json.loads(b.EXCLUDE.read_text())}
    except (OSError, ValueError):
        excluded = {}
    for e in entries:
        if e["id"] in remove:
            excluded[e["id"]] = {"id": e["id"], "reason": remove[e["id"]], "audited": today}
    b.EXCLUDE.write_text("[\n" + ",\n".join("  " + json.dumps(excluded[i], ensure_ascii=False) for i in sorted(excluded)) + "\n]\n")
    b.write_entries(args.data, kept)
    print(f"wrote {args.data} ({len(kept)} channels) and {b.EXCLUDE} ({len(excluded)} excluded)")


if __name__ == "__main__":
    main()
