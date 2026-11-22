#!/usr/bin/env python3
"""Slims OpenCelliD per-country exports for the Sniffer app and writes index.json.

Usage: mirror.py <in_dir> <out_dir> [--generated YYYY-MM-DD]

<in_dir> holds OpenCelliD exports named <mcc>.csv.gz (14 columns, header optional).
<out_dir> receives <mcc>.csv.gz with only the columns the app uses, index.json and
LICENSE-DATA.txt. Data is CC BY-SA 4.0 (c) OpenCelliD contributors. Stdlib only.
"""
import datetime
import gzip
import json
import os
import re
import shutil
import sys

COLUMNS = ["radio", "mcc", "net", "area", "cell", "unit", "lon", "lat", "range", "samples"]
_INT_FIELDS = (1, 2, 3, 4)


def _valid(fields):
    if len(fields) < 10:
        return False
    try:
        for i in _INT_FIELDS:
            int(fields[i])
        lon, lat = float(fields[6]), float(fields[7])
    except ValueError:
        return False
    if abs(lat) > 90 or abs(lon) > 180 or (lat == 0 and lon == 0):
        return False
    return True


def slim(lines):
    """Yields a header, then each valid row cut to COLUMNS."""
    yield ",".join(COLUMNS)
    for line in lines:
        line = line.strip()
        if not line or line.startswith("radio,"):
            continue
        fields = line.split(",")
        if _valid(fields):
            yield ",".join(fields[:10])


def build_index(entries, generated):
    return {
        "generated": generated,
        "countries": {mcc: {"file": f"{mcc}.csv.gz", "bytes": b, "rows": r} for mcc, (b, r) in sorted(entries.items())},
    }


def main(argv):
    args = list(argv)
    generated = datetime.date.today().isoformat()
    if "--generated" in args:
        i = args.index("--generated")
        generated = args[i + 1]
        del args[i:i + 2]
    if len(args) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    src, out = args
    os.makedirs(out, exist_ok=True)
    entries = {}
    for name in sorted(os.listdir(src)):
        m = re.fullmatch(r"(\d{3})\.csv\.gz", name)
        if not m:
            continue
        mcc = m.group(1)
        dst = os.path.join(out, name)
        rows = 0
        with gzip.open(os.path.join(src, name), "rt", errors="replace") as fin, gzip.open(dst, "wt") as fout:
            for i, line in enumerate(slim(fin)):
                fout.write(line + "\n")
                rows += 0 if i == 0 else 1
        entries[mcc] = (os.path.getsize(dst), rows)
    if not entries:
        print("no <mcc>.csv.gz inputs found", file=sys.stderr)
        return 1
    with open(os.path.join(out, "index.json"), "w") as f:
        json.dump(build_index(entries, generated), f, indent=1)
    shutil.copy(os.path.join(os.path.dirname(os.path.abspath(__file__)), "LICENSE-DATA.txt"), out)
    print(f"wrote {len(entries)} countries, {sum(r for _, r in entries.values())} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
