#!/usr/bin/env python3
"""Find a hand-picked list of channels (curated_wanted.json) in the iptv-org data and test their streams.

    python3 tools/build-channels/curate.py --search     # only show name matches, no stream tests
    python3 tools/build-channels/curate.py              # match + test, write data/curated.json

Only channels/streams that exist in iptv-org are kept; blocklisted, NSFW and closed channels are dropped.
Stream status is measured from THIS machine, so also look at the Geo-blocked label iptv-org puts on streams.
"""
import argparse
import json
import re
import unicodedata
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlparse

import common as b

HERE = Path(__file__).resolve().parent
OUT = b.ROOT / "data" / "curated.json"
NOISE = {"hd", "sd", "fhd", "tv", "channel", "the"}
MAX_CANDIDATES_TESTED = 6
MAX_STREAMS_TRIED = 4
MAX_ALTERNATIVES = 4


# ---- name matching (pure) ---------------------------------------------------

def norm(s):
    """Lowercase, accents and punctuation removed; letters of any script are kept (so "MBC忠北" != "mbc")."""
    s = unicodedata.normalize("NFKD", s.replace("&", " and "))
    s = "".join(ch for ch in s if not unicodedata.combining(ch)).lower()
    return re.sub(r"[\W_]+", " ", s).strip()


def keys(name):
    tokens = norm(name).split()
    core = [t for t in tokens if t not in NOISE]
    return "".join(tokens), "".join(core), set(core)


def variants(query):
    """The query itself plus the same without a (parenthesised part)."""
    plain = re.sub(r"\([^)]*\)", " ", query)
    return [query] if plain == query else [query, plain]


def score(queries, names):
    """100 same name; 90 same apart from HD/TV/spaces; <90 partial: query words inside the name (75..)
    or a name of 2+ words inside the query (60..)."""
    best = 0
    for q in queries:
        for v in variants(q):
            qn, ql, qt = keys(v)
            for name in names:
                n, l, t = keys(name)
                if qn and qn == n:
                    best = max(best, 100)
                elif ql and ql == l:
                    best = max(best, 90)
                elif qt and qt <= t:
                    best = max(best, 75 - min(len(t - qt), 25))
                elif len(t) >= 2 and t <= qt:
                    best = max(best, 60 - min(len(qt - t), 20))
    return best


def prefer_rank(item, channel):
    """Position of the first 'prefer' entry (country code or exact name) this channel satisfies."""
    for i, p in enumerate(item.get("prefer", [])):
        if p == channel["country"] or norm(p) == norm(channel["name"]) or norm(p) in [norm(a) for a in channel.get("alt_names") or []]:
            return i
    return len(item.get("prefer", []))


def channel_candidates(item, channels, by_channel):
    """[(score, channel)] best first. Ties: preferred > has streams > shorter name."""
    queries = [item["q"], *item.get("aliases", [])]
    out = []
    for c in channels:
        if item.get("country") and c["country"] != item["country"]:
            continue
        if c["country"] in item.get("exclude_country", []):
            continue
        s = score(queries, [c["name"], *(c.get("alt_names") or [])])
        if s:
            out.append((s, c))
    # Strong matches (>= 90) first; inside a tier: preferred > has streams > better score > shorter name.
    return sorted(out, key=lambda sc: (sc[0] < 90, prefer_rank(item, sc[1]), not by_channel.get(sc[1]["id"]), -sc[0], len(norm(sc[1]["name"])), sc[1]["name"]))


def stream_candidates(item, channels, by_channel):
    """[(score, channel, feed, title, [streams])] matched on stream titles (e.g. 'Al Jazeera English' inside 'Al Jazeera')."""
    queries = [item["q"], *item.get("aliases", [])]
    groups = {}
    for c in channels:
        if (item.get("country") and c["country"] != item["country"]) or c["country"] in item.get("exclude_country", []):
            continue
        for s in by_channel.get(c["id"], []):
            title = s.get("title") or ""
            sc = score(queries, [title])
            if sc >= 90:
                groups.setdefault((c["id"], s.get("feed"), title), [c, sc, []])[2].append(s)
    out = [(sc, c, feed, title, ss) for (cid, feed, title), (c, sc, ss) in groups.items()]
    return sorted(out, key=lambda x: (-x[0], prefer_rank(item, x[1]), len(norm(x[3]))))


# ---- stream test ------------------------------------------------------------

def probe(stream, timeout=5):
    """(status, detail): status is hidup | geo-block | mati."""
    headers = {"User-Agent": stream.get("user_agent") or "Mozilla/5.0"}
    if stream.get("referrer"):
        headers["Referer"] = stream["referrer"]
    try:
        with urllib.request.urlopen(urllib.request.Request(stream["url"], headers=headers), timeout=timeout) as r:
            text = r.read(b.PROBE_BYTES).decode("utf-8", "ignore")
        return ("hidup", "") if b.is_hls(text) else ("mati", "bukan playlist HLS")
    except urllib.error.HTTPError as e:
        return ("geo-block", f"HTTP {e.code}") if e.code in (403, 451) else ("mati", f"HTTP {e.code}")
    except Exception as e:  # DNS, TLS, timeout, reset...
        return "mati", type(e).__name__


def test_streams(streams):
    """(stream, status, detail) of the first live stream in quality order, else the first one tried; None if no streams."""
    tried = []
    for s in b.order_streams(streams)[:MAX_STREAMS_TRIED]:
        status, detail = probe(s)
        tried.append((s, status, detail))
        if status == "hidup":
            return tried[-1]
    return tried[0] if tried else None


# ---- main -------------------------------------------------------------------

def reject_reason(c, blocked):
    if c["id"] in blocked:
        return f"blocklist ({blocked[c['id']]})"
    if c.get("closed"):
        return "channel ditutup"
    if c.get("is_nsfw"):
        return "NSFW"
    return None


def units(item, usable, rejected, by_channel, blocked):
    """Expand a wanted item into candidate 'units': each is (kind, score, channel, feed, title, streams) with a
    ready list of streams to test. Returns (units, alternatives, rejected_info)."""
    by_id = {c["id"]: c for c in usable}
    if "feeds_of" in item:  # one unit per feed of a channel, e.g. regional TVRI
        c = by_id.get(item["feeds_of"])
        feeds = {}
        for s in (by_channel.get(item["feeds_of"], []) if c else []):
            if s.get("feed") not in item.get("exclude_feeds", []):
                feeds.setdefault((s.get("feed"), s.get("title")), []).append(s)
        return [[(100, c, feed, title, ss)] for (feed, title), ss in sorted(feeds.items(), key=lambda kv: kv[0][1] or "")], [], []
    if "owner_regex" in item:  # every channel of a group of owners
        rx = re.compile(item["owner_regex"], re.I)
        found = [c for c in usable if c["country"] == item.get("country") and rx.search(" ".join([c.get("network") or "", *(c.get("owners") or [])]))]
        return [[(100, c, None, None, by_channel.get(c["id"], []))] for c in found], [], []

    cands = channel_candidates(item, usable, by_channel)
    strong = [x for x in cands if x[0] >= 90]
    via_stream = [] if strong else stream_candidates(item, usable, by_channel)
    alternatives = [c for s, c in cands if s >= 60]
    if strong:
        pool = [(s, c, item.get("feed"), None, by_channel.get(c["id"], [])) for s, c in strong]
    elif via_stream:
        pool = via_stream
    else:
        pool = [(s, c, item.get("feed"), None, by_channel.get(c["id"], [])) for s, c in cands]
    if item.get("feed"):
        pool = [(s, c, f, t, [x for x in ss if x.get("feed") == f]) for s, c, f, t, ss in pool]
    rej = [] if pool else [(c["name"], reject_reason(c, blocked)) for s, c in channel_candidates(item, rejected, by_channel)[:2]]
    if not pool:
        return [], alternatives, rej
    return [pool[:MAX_CANDIDATES_TESTED]], alternatives, rej


def resolve_unit(group, item, by_channel):
    """Test one candidate group, return the serialisable entry for the first candidate with a live stream."""
    chosen = None
    for s, c, feed, title, streams in group:
        t = test_streams(streams)
        # Keep the first candidate unless a later one is live while the kept one is not (or has no streams at all).
        if chosen is None or (t and t[1] == "hidup" and (chosen[1] is None or chosen[1][1] != "hidup")):
            chosen = ((s, c, feed, title, streams), t)
        if t and t[1] == "hidup":
            break
    (s, c, feed, title, streams), tested = chosen
    stream, status, detail = tested if tested else ({"url": "", "quality": None}, "tidak ada stream", "")
    cid = c["id"] + (f"#{feed}" if feed else "")
    entry = {"id": cid, "name": title or c["name"], "country": b.COUNTRY_FIXES.get(c["country"], c["country"]),
             "category": item.get("category") or (c.get("categories") or ["general"])[0], "streamUrl": stream["url"]}
    headers = {k: v for k, v in (("User-Agent", stream.get("user_agent")), ("Referer", stream.get("referrer"))) if v}
    if headers:
        entry["headers"] = headers
    entry.update({
        # "~..." marks entries found automatically (feeds / owners) rather than by a requested name.
        "groups": list(item["groups"]), "requested": [item["q"] if "q" in item else "~" + ("feed " + item["feeds_of"] if "feeds_of" in item else "pemilik")],
        "matchedChannel": c["name"], "matchScore": s, "status": status, "detail": detail,
        "domain": urlparse(stream["url"]).hostname or "", "quality": stream.get("quality"),
        "geoLabel": "Geo-blocked" in (stream.get("labels") or []) or any("Geo-blocked" in (x.get("labels") or []) for x in streams if x.get("url") == stream["url"]),
    })
    return entry


def status_text(e):
    if e["status"] == "hidup":
        return "hidup" + (" ⚠geo-label" if e["geoLabel"] else "")
    if e["status"] == "geo-block":
        return f"geo-block {e['detail'].replace('HTTP ', '')}" + (" ⚠geo-label" if e["geoLabel"] else "")
    if e["status"] == "tidak ada stream":
        return "tanpa stream"
    return f"mati ({e['detail']})" + (" ⚠geo-label" if e["geoLabel"] else "")


def report(path):
    """Markdown tables from curated.json: Indonesia and international, each split live / found-but-failed / not found."""
    data = json.loads(path.read_text())
    for title, is_id in (("INDONESIA", True), ("INTERNASIONAL", False)):
        found = [e for e in data["found"] if (e["country"] == "ID") == is_id]
        missing = [m for m in data["notFound"] if (m.get("country") == "ID") == is_id]
        print(f"\n## {title}  (ketemu {len(found)}, tidak ditemukan {len(missing)})")
        for label, rows in (("KETEMU + HIDUP", [e for e in found if e["status"] == "hidup"]),
                            ("KETEMU TAPI GAGAL", [e for e in found if e["status"] != "hidup"])):
            print(f"\n### {label} ({len(rows)})\n| Channel | Grup | Negara | Domain | Status | Kualitas |\n|---|---|---|---|---|---|")
            for e in sorted(rows, key=lambda e: (e["groups"][0], e["name"].lower())):
                same = e["requested"][0].startswith("~") or e["name"].lower() in [r.lower() for r in e["requested"]]
                shown = e["name"] if same else f"{e['requested'][0]} → {e['name']}"
                domain = (e["domain"] + " (IP)") if re.fullmatch(r"[\d.]+", e["domain"]) else (e["domain"] or "-")
                print(f"| {shown} | {'+'.join(e['groups'])} | {e['country']} | {domain} | {status_text(e)} | {e['quality'] or '?'} |")
        print(f"\n### TIDAK DITEMUKAN ({len(missing)})")
        for m in missing:
            why = "; ".join(f"{o['name']} ({o['why']})" for o in m["onlyRejected"])
            print(f"- {m['requested']} [{'+'.join(m['groups'])}]" + (f": hanya ada {why}" if why else ""))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--search", action="store_true", help="only list name matches")
    ap.add_argument("--report", action="store_true", help="print tables from the existing curated.json")
    ap.add_argument("--out", type=Path, default=OUT)
    args = ap.parse_args()
    if args.report:
        return report(args.out)

    wanted = json.loads((HERE / "curated_wanted.json").read_text())["items"]
    channels, streams, blocklist = (b.fetch_json(n) for n in ("channels", "streams", "blocklist"))
    blocked = {x["channel"]: x.get("reason", "blocklist") for x in blocklist}
    by_channel = {}
    for s in streams:
        if s.get("channel"):
            by_channel.setdefault(s["channel"], []).append(s)
    usable = [c for c in channels if not reject_reason(c, blocked)]
    rejected = [c for c in channels if reject_reason(c, blocked)]

    plan = []  # (item, groups-of-candidates, alternatives, rejected)
    for item in wanted:
        us, alts, rej = units(item, usable, rejected, by_channel, blocked)
        plan.append((item, us, alts, rej))

    if args.search:
        for item, us, alts, rej in plan:
            if "q" not in item:
                print(f"[{item.get('feeds_of') or item.get('owner_regex')[:20]}] {len(us)} unit")
                continue
            line = "; ".join(f"{u[0]}:{u[1]['name']}[{u[1]['country']}]{'#' + u[2] if u[2] else ''} s={len(u[4])}" for g in us for u in g) or "TIDAK ADA"
            print(f"{item['q']:<24} {line}" + (f"   (hanya: {rej})" if rej else ""))
        return

    jobs = [(item, g, alts) for item, us, alts, rej in plan for g in us]
    with ThreadPoolExecutor(16) as pool:
        entries = list(pool.map(lambda j: resolve_unit(j[1], j[0], by_channel), jobs))
    for (item, g, alts), e in zip(jobs, entries):
        e["alternatives"] = [{"id": a["id"], "name": a["name"], "country": b.COUNTRY_FIXES.get(a["country"], a["country"])}
                             for a in alts if a["id"] != e["id"].split("#")[0]][:MAX_ALTERNATIVES]
    missing = [{"requested": item["q"], "groups": item["groups"], "country": item.get("country"),
                "onlyRejected": [{"name": n, "why": w} for n, w in rej]}
               for item, us, alts, rej in plan if "q" in item and not us]

    merged = {}  # the same channel can be wanted twice (e.g. RCTI HD and RCTI): merge groups
    for e in entries:
        if e["id"] in merged:
            m = merged[e["id"]]
            m["groups"] = sorted(set(m["groups"]) | set(e["groups"]))
            m["requested"] += e["requested"]
        else:
            merged[e["id"]] = e
    out = {"found": list(merged.values()), "notFound": missing}
    args.out.write_text(json.dumps(out, ensure_ascii=False, indent=1))
    print(f"wrote {args.out}: found={len(merged)} notFound={len(missing)}")


if __name__ == "__main__":
    main()
