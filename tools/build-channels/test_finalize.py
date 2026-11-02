"""Offline tests for finalize.py. Run: python3 -m unittest discover -s tools/build-channels"""
import unittest

import finalize as f

TIERS = {
    "A": ["a1"], "A_groups": ["TVRI Daerah"], "B": ["b1", "sport"], "B_note": "perlu dicek di TV",
    "group_countries": ["ID"], "aggregator_domains": ["agg.example"],
    "overrides": {"sport": {"streamUrl": "https://official/sport.m3u8", "name": "TVRI Sport"}},
}


def e(id, country="ID", groups=("Berita",), status="hidup", domain="video.example", detail="", name=None):
    return {"id": id, "name": name or id, "country": country, "category": "news", "streamUrl": f"https://{domain}/{id}.m3u8",
            "groups": list(groups), "status": status, "detail": detail, "domain": domain}


class Finalize(unittest.TestCase):
    def setUp(self):
        self.curated = {"found": [
            e("a1"), e("daerah", groups=["TVRI Daerah"]), e("b1", country="US"), e("sport", status="geo-block", detail="HTTP 403", domain="1.2.3.4"),
            e("ip", domain="1.2.3.4"), e("agg", domain="cdn.agg.example"), e("dead", status="mati", detail="HTTP 404"),
            e("none", status="tidak ada stream", domain=""), e("other"),
        ], "notFound": [{"requested": "MNC News", "groups": ["MNC"], "country": "ID", "onlyRejected": [{"name": "MNC News", "why": "channel ditutup"}]}]}
        self.app, self.excluded = f.finalize(self.curated, TIERS)

    def test_tiers(self):
        tiers = {x["id"]: x["tier"] for x in self.app}
        self.assertEqual(tiers, {"a1": "A", "daerah": "A", "b1": "B", "sport": "B"})   # group rule, explicit A/B, nothing else

    def test_tier_b_gets_note_and_tier_a_does_not(self):
        by = {x["id"]: x for x in self.app}
        self.assertEqual(by["b1"]["note"], "perlu dicek di TV")
        self.assertNotIn("note", by["a1"])

    def test_groups_only_for_configured_countries(self):
        by = {x["id"]: x for x in self.app}
        self.assertEqual(by["a1"]["groups"], ["Berita"])
        self.assertNotIn("groups", by["b1"])                 # US channel: grouped by country in the app

    def test_override_replaces_stream_and_name(self):
        sport = next(x for x in self.app if x["id"] == "sport")
        self.assertEqual((sport["streamUrl"], sport["name"]), ("https://official/sport.m3u8", "TVRI Sport"))

    def test_exclusion_reasons(self):
        why = {x["name"]: x["reason"] for x in self.excluded}
        self.assertEqual(why["ip"], "IP mentah")
        self.assertEqual(why["agg"], "agregator")
        self.assertEqual(why["dead"], "mati (HTTP 404)")
        self.assertEqual(why["none"], "tanpa stream")
        self.assertEqual(why["other"], "tidak dipilih (tier C)")
        manual = f.exclusion_reason(e("x", status="geo-block", detail="HTTP 403"), {**TIERS, "exclude_reasons": {"x": "timeout di TV"}})
        self.assertEqual(manual, "timeout di TV")                              # manual reason beats the measured one
        self.assertEqual(why["MNC News"], "tidak ditemukan: hanya ada MNC News (channel ditutup)")

    def test_unknown_tier_id_is_an_error(self):
        with self.assertRaises(SystemExit):
            f.finalize(self.curated, {**TIERS, "A": ["typo"]})


if __name__ == "__main__":
    unittest.main()
