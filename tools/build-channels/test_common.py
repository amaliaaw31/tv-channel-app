"""Offline tests for common.py. Run: python3 -m unittest discover -s tools/build-channels"""
import unittest

import common as c


class Streams(unittest.TestCase):
    def test_order_prefers_resolution_then_https(self):
        s = [
            {"url": "http://a/720", "quality": "720p"},
            {"url": "https://a/1080", "quality": "1080p"},
            {"url": "http://a/1080", "quality": "1080p"},
            {"url": "rtmp://a/x", "quality": "2160p"},   # not http(s): unusable
            {"url": "https://a/none", "quality": None},
        ]
        self.assertEqual([x["url"] for x in c.order_streams(s)],
                         ["https://a/1080", "http://a/1080", "http://a/720", "https://a/none"])

    def test_hls_detection(self):
        self.assertTrue(c.is_hls("﻿#EXTM3U\n#EXT-X-VERSION:3\n"))
        self.assertFalse(c.is_hls("#EXTM3U\n#EXTINF:-1,Plain list\nhttp://x\n"))
        self.assertFalse(c.is_hls("<html>403</html>"))


if __name__ == "__main__":
    unittest.main()
