"""Offline tests for the name matching in curate.py. Run: python3 -m unittest discover -s tools/build-channels"""
import unittest

import curate as c


def ch(id, name, country="ID", alt=None):
    return {"id": id, "name": name, "country": country, "alt_names": alt or []}


class Matching(unittest.TestCase):
    def test_tiers(self):
        self.assertEqual(c.score(["RCTI"], ["RCTI"]), 100)
        self.assertEqual(c.score(["RCTI HD"], ["RCTI"]), 90)                  # HD is noise
        self.assertEqual(c.score(["Kompas TV"], ["KompasTV"]), 100)           # spaces and case
        self.assertEqual(c.score(["GTV (Indonesia) HD"], ["GTV"]), 90)        # parenthesised part dropped
        self.assertTrue(60 < c.score(["Euronews"], ["Euronews English"]) < 90)  # query inside the name
        self.assertTrue(0 < c.score(["Jawa Pos TV Jakarta"], ["Jawa Pos TV"]) < 75)  # name inside the query
        self.assertEqual(c.score(["CNA"], ["CNA International"]) // 10, 7)
        self.assertEqual(c.score(["Sky Sports"], ["Sky"]), 0)                 # a single word is never "inside" a query

    def test_non_latin_names_do_not_collapse(self):
        self.assertEqual(c.score(["MBC"], ["MBC忠北"]), 0)                     # was a false 100 when non-ASCII was dropped
        self.assertEqual(c.score(["Cafe"], ["Café"]), 100)                    # accents are still ignored

    def test_ordering_prefers_country_then_streams(self):
        channels = [ch("a", "Discovery Channel", "NZ"), ch("b", "Discovery Channel", "US"), ch("c", "Discovery", "IL")]
        streams = {"a": [], "b": [{}], "c": [{}]}
        item = {"q": "Discovery", "prefer": ["US"]}
        self.assertEqual([x["id"] for s, x in c.channel_candidates(item, channels, streams)], ["b", "c", "a"])

    def test_scope_and_exclusion(self):
        channels = [ch("a", "NASA TV", "MK"), ch("b", "NASA TV", "US")]
        ids = lambda item: [x["id"] for s, x in c.channel_candidates(item, channels, {})]
        self.assertEqual(sorted(ids({"q": "NASA TV"})), ["a", "b"])
        self.assertEqual(ids({"q": "NASA TV", "exclude_country": ["MK"]}), ["b"])
        self.assertEqual(ids({"q": "NASA TV", "country": "MK"}), ["a"])

    def test_stream_title_match_finds_feeds_inside_a_channel(self):
        channels = [ch("AlJazeera.qa", "Al Jazeera", "QA")]
        streams = {"AlJazeera.qa": [{"title": "Al Jazeera", "feed": "Arabic"}, {"title": "Al Jazeera English", "feed": "English"}]}
        got = c.stream_candidates({"q": "Al Jazeera English"}, channels, streams)
        self.assertEqual([(x[2], x[3]) for x in got], [("English", "Al Jazeera English")])


if __name__ == "__main__":
    unittest.main()
