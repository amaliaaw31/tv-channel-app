"""Offline tests for the pure logic. Run: python3 -m unittest discover -s tools/build-channels"""
import unittest

import build_channels as b


def ch(id, categories, **kw):
    return {"id": id, "name": id, "country": "ID", "categories": categories, "closed": None, "is_nsfw": False, **kw}


class SelectChannels(unittest.TestCase):
    def test_filters_and_priority(self):
        channels = [
            ch("ok", ["news", "sports"]),            # in two wanted categories: first in config order wins
            ch("closed", ["sports"], closed="2020-01-01"),
            ch("nsfw", ["sports"], is_nsfw=True),
            ch("blocked", ["sports"]),
            ch("other", ["music"]),
            ch("uk", ["sports"], country="UK"),
            ch("bad", ["sports"], country="INT"),
        ]
        got = {c["id"]: c for c in b.select_channels(channels, {"blocked"}, ["sports", "news"])}
        self.assertEqual(set(got), {"ok", "uk"})
        self.assertEqual(got["ok"]["category"], "sports")
        self.assertEqual(got["uk"]["country"], "GB")


class Streams(unittest.TestCase):
    def test_order_prefers_resolution_then_https(self):
        s = [
            {"url": "http://a/720", "quality": "720p"},
            {"url": "https://a/1080", "quality": "1080p"},
            {"url": "http://a/1080", "quality": "1080p"},
            {"url": "rtmp://a/x", "quality": "2160p"},   # not http(s): unusable
            {"url": "https://a/none", "quality": None},
        ]
        self.assertEqual([x["url"] for x in b.order_streams(s)],
                         ["https://a/1080", "http://a/1080", "http://a/720", "https://a/none"])

    def test_hls_detection(self):
        self.assertTrue(b.is_hls("﻿#EXTM3U\n#EXT-X-VERSION:3\n"))
        self.assertFalse(b.is_hls("#EXTM3U\n#EXTINF:-1,Plain list\nhttp://x\n"))
        self.assertFalse(b.is_hls("<html>403</html>"))


class Convert(unittest.TestCase):
    def test_entry_schema(self):
        cand = {"id": "x", "name": "X", "country": "ID", "category": "news"}
        plain = b.to_entry(cand, {"url": "https://x/a.m3u8"}, None)
        self.assertEqual(list(plain), ["id", "name", "country", "category", "streamUrl"])
        full = b.to_entry(cand, {"url": "https://x/a.m3u8", "user_agent": "UA", "referrer": "https://r/"}, "https://l/x.png")
        self.assertEqual(full["headers"], {"User-Agent": "UA", "Referer": "https://r/"})
        self.assertEqual(full["logo"], "https://l/x.png")

    def test_logo_preference(self):
        logos = [
            {"channel": "x", "feed": "HD", "in_use": True, "url": "feed"},
            {"channel": "x", "feed": None, "in_use": True, "url": "main"},
            {"channel": "x", "feed": None, "in_use": False, "url": "old"},
        ]
        self.assertEqual(b.pick_logos(logos), {"x": "main"})


if __name__ == "__main__":
    unittest.main()
