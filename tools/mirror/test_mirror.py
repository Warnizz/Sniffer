import gzip
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import mirror  # noqa: E402

HEADER14 = "radio,mcc,net,area,cell,unit,lon,lat,range,samples,changeable,created,updated,averageSignal"
ROW_LTE = "LTE,238,1,1001,2000001,123,12.568,55.676,800,12,1,1600000000,1700000000,0"
ROW_GSM = "GSM,238,2,2002,31000,,12.570,55.677,1500,3,1,1600000000,1700000000,0"


class SlimTest(unittest.TestCase):
    def test_slim_withHeader_keepsColumns(self):
        out = list(mirror.slim([HEADER14, ROW_LTE]))
        self.assertEqual(out[0], ",".join(mirror.COLUMNS))
        self.assertEqual(len(out), 2)

    def test_slim_withoutHeader_works(self):
        out = list(mirror.slim([ROW_LTE]))
        self.assertEqual(out[0], ",".join(mirror.COLUMNS))
        self.assertEqual(out[1], "LTE,238,1,1001,2000001,123,12.568,55.676,800,12")

    def test_slim_dropsExtraColumns(self):
        self.assertEqual(len(list(mirror.slim([ROW_LTE]))[1].split(",")), 10)

    def test_slim_skipsBadRows(self):
        bad = [
            "LTE,238,1",
            "LTE,abc,1,1,1,,12.0,55.0,1,1,1,0,0,0",
            "LTE,238,1,1,1,,12.0,95.0,1,1,1,0,0,0",
            "LTE,238,1,1,1,,0,0,1,1,1,0,0,0",
            "",
        ]
        self.assertEqual(list(mirror.slim(bad + [ROW_GSM]))[1:], ["GSM,238,2,2002,31000,,12.570,55.677,1500,3"])

    def test_slim_keepsEmptyUnit(self):
        self.assertEqual(list(mirror.slim([ROW_GSM]))[1].split(",")[5], "")


class IndexTest(unittest.TestCase):
    def test_build_index_shape(self):
        idx = mirror.build_index({"238": (1234, 2)}, "2026-11-03")
        self.assertEqual(idx, {"generated": "2026-11-03",
                               "countries": {"238": {"file": "238.csv.gz", "bytes": 1234, "rows": 2}}})


class MainTest(unittest.TestCase):
    def test_main_writesGzAndIndex(self):
        with tempfile.TemporaryDirectory() as src, tempfile.TemporaryDirectory() as out:
            with gzip.open(os.path.join(src, "238.csv.gz"), "wt") as f:
                f.write("\n".join([HEADER14, ROW_LTE, ROW_GSM, "junk"]) + "\n")
            self.assertEqual(mirror.main([src, out, "--generated", "2026-11-03"]), 0)
            self.assertTrue(os.path.exists(os.path.join(out, "LICENSE-DATA.txt")))
            with gzip.open(os.path.join(out, "238.csv.gz"), "rt") as f:
                lines = f.read().splitlines()
            self.assertEqual(len(lines), 3)
            idx = json.load(open(os.path.join(out, "index.json")))
            self.assertEqual(idx["countries"]["238"]["rows"], 2)
            self.assertEqual(idx["countries"]["238"]["bytes"], os.path.getsize(os.path.join(out, "238.csv.gz")))

    def test_main_skipsCorruptFile(self):
        with tempfile.TemporaryDirectory() as src, tempfile.TemporaryDirectory() as out:
            with gzip.open(os.path.join(src, "238.csv.gz"), "wt") as f:
                f.write(ROW_LTE + "\n")
            with open(os.path.join(src, "240.csv.gz"), "w") as f:
                f.write("<html>error</html>")
            self.assertEqual(mirror.main([src, out, "--generated", "2026-11-03"]), 0)
            idx = json.load(open(os.path.join(out, "index.json")))
            self.assertEqual(list(idx["countries"]), ["238"])
            self.assertFalse(os.path.exists(os.path.join(out, "240.csv.gz")))

    def test_main_carriesForwardPreviousEntries(self):
        with tempfile.TemporaryDirectory() as src, tempfile.TemporaryDirectory() as out, tempfile.TemporaryDirectory() as prev:
            with gzip.open(os.path.join(src, "238.csv.gz"), "wt") as f:
                f.write(ROW_LTE + "\n")
            prev_index = os.path.join(prev, "index.json")
            json.dump({"generated": "2026-10-03", "countries": {
                "238": {"file": "238.csv.gz", "bytes": 1, "rows": 1},
                "240": {"file": "240.csv.gz", "bytes": 99, "rows": 7}}}, open(prev_index, "w"))
            self.assertEqual(mirror.main([src, out, "--generated", "2026-11-03", "--previous-index", prev_index]), 0)
            idx = json.load(open(os.path.join(out, "index.json")))
            self.assertEqual(idx["countries"]["240"], {"file": "240.csv.gz", "bytes": 99, "rows": 7})
            self.assertEqual(idx["countries"]["238"]["rows"], 1)

    def test_main_noInputs_fails(self):
        with tempfile.TemporaryDirectory() as src, tempfile.TemporaryDirectory() as out:
            self.assertNotEqual(mirror.main([src, out]), 0)


if __name__ == "__main__":
    unittest.main()
