"""Run: python3 -m unittest discover -s tools/health-check"""
import unittest

import check


class Classify(unittest.TestCase):
    def test_states(self):
        self.assertEqual(check.classify(200, None, True), "ok")
        self.assertEqual(check.classify(403, "HTTPError", False), "geo")
        self.assertEqual(check.classify(451, "HTTPError", False), "geo")
        self.assertEqual(check.classify(404, "HTTPError", False), "dead")
        self.assertEqual(check.classify(None, "URLError", False), "dead")     # DNS / timeout
        self.assertEqual(check.classify(200, None, False), "dead")            # an HTML page, not HLS


if __name__ == "__main__":
    unittest.main()
