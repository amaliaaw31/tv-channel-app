"""Offline tests for prune_audit. Run: python3 -m unittest discover -s tools/build-channels"""
import json
import unittest

import prune_audit as p

TITLES = {"Format tidak didukung", "Dibatasi wilayah"}


def line(id, result, title=None):
    return json.dumps({"id": id, "result": result, **({"title": title} if title else {})})


class Decide(unittest.TestCase):
    def test_only_chosen_error_titles_are_removed(self):
        lines = [
            line("fmt", "error", "Format tidak didukung"),
            line("geo", "error", "Dibatasi wilayah"),
            line("gone", "error", "Channel tidak ditemukan"),   # error, but not one we remove
            line("slow", "timeout"),
            line("fine", "ok"),
            "",                                                  # blank lines are ignored
        ]
        self.assertEqual(p.decide(lines, TITLES), {"fmt": "Format tidak didukung", "geo": "Dibatasi wilayah"})

    def test_latest_audit_wins(self):
        lines = [line("x", "error", "Dibatasi wilayah"), line("x", "ok")]
        self.assertEqual(p.decide(lines, TITLES), {})


if __name__ == "__main__":
    unittest.main()
