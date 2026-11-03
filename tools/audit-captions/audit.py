#!/usr/bin/env python3
"""Which channels in data/channels.json carry captions? Measured from THIS machine (the VPS), no TV needed.

    python3 tools/audit-captions/audit.py             # manifests only: master + first media playlist
    python3 tools/audit-captions/audit.py --segments  # also look inside the newest segments for CEA-608/708 (EIA-608 "GA94" SEI)
    python3 tools/audit-captions/audit.py --write     # also write data/captions.json (read by build-channels/finalize.py)

Caption kind per channel (the "caption" column of channels.json):
    subtitle  the master playlist has #EXT-X-MEDIA TYPE=SUBTITLES (WebVTT renditions)
    cc        CLOSED-CAPTIONS is declared (CC1/CC2...), or --segments found real CEA text inside the video
    none      neither
A channel whose stream cannot be fetched from here (geo-block, dead) is left out, so its old value stays unknown.
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urljoin

ROOT = Path(__file__).resolve().parent.parent.parent
CHANNELS = ROOT / "data" / "channels.json"
OUT = ROOT / "data" / "captions.json"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
ATTR = re.compile(r'([A-Z0-9-]+)=("[^"]*"|[^,]*)')
SEGMENT_BYTES = 3_000_000


def fetch(url, headers, limit=None, timeout=15):
    req = urllib.request.Request(url, headers={"User-Agent": UA, **headers})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read(limit) if limit else r.read()


def attrs(line):
    return {k: v.strip('"') for k, v in ATTR.findall(line.split(":", 1)[1])}


def parse_master(text):
    """(subtitle languages, cc [(instream-id, language)], first variant uri or None, cc-explicitly-none)."""
    subs, ccs, variant, none = [], [], None, False
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    for i, l in enumerate(lines):
        if l.startswith("#EXT-X-MEDIA:"):
            a = attrs(l)
            if a.get("TYPE") == "SUBTITLES":
                subs.append(a.get("LANGUAGE") or a.get("NAME", "?"))
            elif a.get("TYPE") == "CLOSED-CAPTIONS":
                ccs.append((a.get("INSTREAM-ID", "?"), a.get("LANGUAGE") or a.get("NAME", "?")))
        elif l.startswith("#EXT-X-STREAM-INF:"):
            if attrs(l).get("CLOSED-CAPTIONS") == "NONE":
                none = True
            if variant is None and i + 1 < len(lines) and not lines[i + 1].startswith("#"):
                variant = lines[i + 1]
    return subs, ccs, variant, none


def segment_urls(text, base):
    return [urljoin(base, l.strip()) for l in text.splitlines() if l.strip() and not l.startswith("#")]


def strip_ts(data):
    """Payload bytes of an MPEG-TS stream (headers and adaptation fields removed); b"" when it is not TS."""
    if len(data) < 188 * 3 or data[0] != 0x47 or data[188] != 0x47:
        return b""
    out = bytearray()
    for p in range(0, len(data) - 187, 188):
        pkt = data[p:p + 188]
        if pkt[0] != 0x47:
            continue
        i = 4
        if pkt[3] & 0x20:  # adaptation field present
            i += 1 + pkt[4]
        if pkt[3] & 0x10 and i < 188:
            out += pkt[i:]
    return bytes(out)


def cea_scan(data):
    """Look for ATSC A/53 caption data (SEI user data "GA94", type 3) in H.264/H.265 video.
    Returns {"sei": number of cc_data blocks, "pairs608": valid field 1/2 pairs, "text608": pairs with printable text,
    "pairs708": DTVCC pairs, "ch1": text pairs of caption field 1 (CC1/CC2), "other": field 2 (CC3/CC4),
    "sample": first decoded characters} (all zero when there is none)."""
    res = {"sei": 0, "pairs608": 0, "text608": 0, "pairs708": 0, "ch1": 0, "other": 0, "sample": ""}
    for blob in (data, strip_ts(data)):
        if not blob:
            continue
        for m in re.finditer(b"GA94\x03", blob):
            p = m.end()
            if p + 2 > len(blob):
                continue
            count = blob[p] & 0x1F
            p += 2  # flags/count byte + em_data byte
            res["sei"] += 1
            for k in range(count):
                if p + 3 > len(blob):
                    break
                tag, d1, d2 = blob[p], blob[p + 1], blob[p + 2]
                p += 3
                valid, kind = tag & 0x04, tag & 0x03
                if not valid:
                    continue
                if kind in (0, 1):
                    res["pairs608"] += 1
                    a, b = d1 & 0x7F, d2 & 0x7F
                    chars = "".join(chr(c) for c in (a, b) if 0x20 <= c < 0x7F)
                    if chars:
                        res["text608"] += 1
                        res["ch1" if kind == 0 else "other"] += 1
                        if len(res["sample"]) < 60:
                            res["sample"] += chars
                elif d1 or d2:
                    res["pairs708"] += 1
        if res["sei"]:
            break  # the raw blob and the TS payload see the same data
    return res


def audit(entry, segments, samples=2):
    out = {"id": entry["id"], "name": entry["name"], "languages": entry.get("languages", []), "status": "ok",
           "subtitle": [], "cc": [], "kind": "none"}
    headers = entry.get("headers", {})
    try:
        master = fetch(entry["streamUrl"], headers).decode("utf-8", "replace")
        if "#EXT-X-STREAM-INF" in master:
            subs, ccs, variant, none = parse_master(master)
            out.update(subtitle=sorted(set(subs)), cc=[f"{i}:{l}" for i, l in ccs], declaredNone=none)
            media_url = urljoin(entry["streamUrl"], variant) if variant else None
        else:  # the url is a media playlist already
            media_url = entry["streamUrl"]
            out["noMaster"] = True
        if media_url:
            media = fetch(media_url, headers).decode("utf-8", "replace")
            segs = segment_urls(media, media_url)
            out["segments"] = len(segs)
            if segments and segs:
                scans = []
                for s in segs[-samples:]:
                    try:
                        scans.append(cea_scan(fetch(s, headers, SEGMENT_BYTES)))
                    except Exception as e:  # one bad segment must not hide the channel
                        scans.append({"error": type(e).__name__})
                out["inVideo"] = {k: sum(s.get(k, 0) for s in scans) for k in ("sei", "pairs608", "text608", "pairs708", "ch1", "other")}
                out["inVideo"]["sample"] = next((s["sample"] for s in scans if s.get("sample")), "")
    except urllib.error.HTTPError as e:
        out["status"] = f"HTTP {e.code}"
    except Exception as e:
        out["status"] = type(e).__name__
    if out["subtitle"]:
        out["kind"] = "subtitle"
    elif out["cc"] or (out.get("inVideo", {}).get("text608", 0) + out.get("inVideo", {}).get("pairs708", 0)) > 0:
        out["kind"] = "cc"
    return out


def table(rows):
    head = ("channel", "bahasa channel", "jenis", "subtitle (WebVTT)", "CC dideklarasikan", "di video 608/708", "status")
    lines = [head]
    for r in rows:
        iv = r.get("inVideo")
        inside = "-" if iv is None else (f"{iv['ch1']} f1 / {iv['other']} f2 / {iv['pairs708']} 708" if iv["sei"] else "tidak ada")
        lines.append((r["name"], ",".join(r["languages"]) or "?", r["kind"], ",".join(r["subtitle"]) or "-", ",".join(r["cc"]) or "-", inside, r["status"]))
    w = [max(len(str(l[i])) for l in lines) for i in range(len(head))]
    return "\n".join("  ".join(str(c).ljust(w[i]) for i, c in enumerate(l)) for l in lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--segments", action="store_true", help="also scan the newest segments for CEA-608/708")
    ap.add_argument("--write", action="store_true", help=f"write {OUT.relative_to(ROOT)}")
    ap.add_argument("--samples", type=int, default=2, help="newest segments scanned per channel with --segments (default 2; live captions come and go)")
    ap.add_argument("--id", action="append", help="only this channel id (repeatable)")
    args = ap.parse_args()
    channels = json.loads(CHANNELS.read_text())
    if args.id:
        channels = [c for c in channels if c["id"] in args.id]
    with ThreadPoolExecutor(8) as pool:
        rows = list(pool.map(lambda c: audit(c, args.segments, args.samples), channels))
    rows.sort(key=lambda r: ({"subtitle": 0, "cc": 1, "none": 2}[r["kind"]], r["name"].lower()))
    print(table(rows))
    from collections import Counter
    print("\n" + ", ".join(f"{k}={v}" for k, v in sorted(Counter(r["kind"] for r in rows if r["status"] == "ok").items())),
          "| tidak terbaca:", sum(r["status"] != "ok" for r in rows), file=sys.stderr)
    if args.write:
        old = {d["id"]: d for d in json.loads(OUT.read_text())} if OUT.exists() else {}
        data = []
        for r in rows:
            if r["status"] != "ok":
                if r["id"] in old:
                    data.append(old[r["id"]])  # not reachable from here this time: keep what was measured before
                continue
            kind = r["kind"]
            # A caption encoder (cc_data in the video) with no text in this sample is a channel whose captions come and go:
            # keep an earlier "cc" instead of flapping.
            if kind == "none" and r.get("inVideo", {}).get("sei") and old.get(r["id"], {}).get("caption") == "cc":
                kind = "cc"
            data.append({"id": r["id"], "caption": kind, "languages": r["subtitle"] or [c.split(":", 1)[1] for c in r["cc"]]})
        OUT.write_text("[\n" + ",\n".join("  " + json.dumps(d, ensure_ascii=False) for d in sorted(data, key=lambda d: d["id"])) + "\n]\n")
        print(f"wrote {OUT} ({len(data)} channels)", file=sys.stderr)


if __name__ == "__main__":
    main()
