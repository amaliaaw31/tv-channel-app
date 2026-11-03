#!/usr/bin/env python3
"""Turn data/curated.json into the app's channel list using tiers.json.

    python3 tools/build-channels/finalize.py

Tier A + B  -> data/channels.json  (what the app bundles and loads)
everything else (Tier C) + not found -> data/excluded.json, each with a reason
Rules and the meaning of the tiers: docs/sources.md.
"""
import json
import re
import sys
from pathlib import Path

import common as b

HERE = Path(__file__).resolve().parent
CURATED = b.ROOT / "data" / "curated.json"
CHANNELS = b.ROOT / "data" / "channels.json"
EXCLUDED = b.ROOT / "data" / "excluded.json"


def tier_of(entry, tiers):
    if entry["id"] in tiers["A"] or set(entry["groups"]) & set(tiers.get("A_groups", [])):
        return "A"
    return "B" if entry["id"] in tiers["B"] else "C"


def exclusion_reason(entry, tiers):
    """Why a Tier C channel stays out of the app (a manual reason in tiers.json wins over the measured one)."""
    manual = tiers.get("exclude_reasons", {}).get(entry["id"])
    if manual:
        return manual
    status = entry["status"]
    if status == "tidak ada stream":
        return "tanpa stream"
    if status == "mati":
        return f"mati ({entry['detail']})"
    if status == "geo-block":
        return f"geo-block ({entry['detail']})"
    if re.fullmatch(r"[\d.]+", entry["domain"]):
        return "IP mentah"
    if any(entry["domain"] == d or entry["domain"].endswith("." + d) for d in tiers.get("aggregator_domains", [])):
        return "agregator"
    return "tidak dipilih (tier C)"


def app_entry(entry, tier, tiers):
    """Entry in the app's channels.json schema (+ groups/tier/note)."""
    out = {k: entry[k] for k in ("id", "name", "country", "category", "streamUrl")}
    out.update({k: v for k, v in tiers.get("overrides", {}).get(entry["id"], {}).items() if k in out})
    if entry.get("headers"):
        out["headers"] = entry["headers"]
    if entry["country"] in tiers.get("group_countries", []) and entry["groups"]:
        out["groups"] = sorted(entry["groups"])
    if entry.get("languages"):  # ISO 639-3 from iptv-org feeds; the app skips translating Indonesian ("ind") channels
        out["languages"] = entry["languages"]
    out["tier"] = tier
    if tier == "B":
        out["note"] = tiers["B_note"]
    return out


def finalize(curated, tiers):
    """(app entries, excluded entries). Raises if tiers.json names an id that curated.json does not have."""
    found = {e["id"]: e for e in curated["found"]}
    unknown = [i for i in tiers["A"] + tiers["B"] if i not in found]
    if unknown:
        raise SystemExit(f"tiers.json has ids that are not in curated.json: {unknown}")
    app, excluded = [], []
    for e in curated["found"]:
        tier = tier_of(e, tiers)
        if tier == "C":
            excluded.append({"id": e["id"], "name": e["name"], "country": e["country"], "reason": exclusion_reason(e, tiers),
                             "domain": e["domain"], "groups": e["groups"]})
        else:
            app.append(app_entry(e, tier, tiers))
    for m in curated["notFound"]:
        why = "; ".join(f"{o['name']} ({o['why']})" for o in m["onlyRejected"])
        excluded.append({"id": None, "name": m["requested"], "country": m.get("country"),
                         "reason": "tidak ditemukan" + (f": hanya ada {why}" if why else ""), "domain": "", "groups": m["groups"]})
    app.sort(key=lambda e: (e["country"], e["name"].lower(), e["id"]))
    excluded.sort(key=lambda e: (e["reason"].split(" ")[0], e["name"].lower()))
    return app, excluded


def main():
    curated = json.loads(CURATED.read_text())
    tiers = json.loads((HERE / "tiers.json").read_text())
    app, excluded = finalize(curated, tiers)
    b.write_entries(CHANNELS, app)
    EXCLUDED.write_text("[\n" + ",\n".join("  " + json.dumps(e, ensure_ascii=False) for e in excluded) + "\n]\n")
    count = lambda t: sum(1 for e in app if e["tier"] == t)
    print(f"wrote {CHANNELS}: {len(app)} channels (A={count('A')}, B={count('B')}); {EXCLUDED}: {len(excluded)}", file=sys.stderr)


if __name__ == "__main__":
    main()
