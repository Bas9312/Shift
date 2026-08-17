#!/usr/bin/env python3
"""Build the source-image map for the familiar asset pipeline.

Maps the raw generated artwork in newFamiliars/ onto <familiar_id>/<variant> pairs and
writes tools/familiars_sources.tsv. Kept as a script rather than a one-off command so the
mapping is reviewable and re-runnable when new artwork arrives.

Source files are addressed by their position in the sorted listing, which is how the
artwork was delivered; the resulting TSV records the real filenames so the mapping can be
checked by eye afterwards.
"""

import sys
from pathlib import Path

SRC = Path(__file__).resolve().parent.parent / "newFamiliars"
OUT = Path(__file__).resolve().parent / "familiars_sources.tsv"

VARIANTS = ["day1", "day2", "day3", "night"]

# familiar id -> source indices (1-based, sorted listing) in day1, day2, day3, night order
NEW_FAMILIARS = [
    ("familiar_ping_penguin",         [1, 2, 3, 4]),
    ("familiar_five_more_minutes",    [5, 6, 7, 8]),
    ("familiar_public_wifi",          [9, 10, 11, 12]),
    ("familiar_arkady",               [13, 14, 15, 16]),
    ("familiar_bad_advice",           [17, 18, 19, 20]),
    # 21-24 are a second take on familiar_bad_advice - excluded by the owner.
    ("familiar_told_you_raven",       [25, 26, 27, 28]),
    # 31/32 were delivered at 1536x1024 instead of 1254x1254; replaced by 50/49.
    # Within this group the day poses are 31, 32, the funny pose is 29, the sleeper is 30.
    ("familiar_bureaucracy_imp",      [50, 49, 29, 30]),
    ("familiar_common_sense_toad",    [33, 34, 35, 36]),
    ("familiar_literal_pigeon",       [37, 38, 39, 40]),
    ("familiar_unread_notifications", [41, 42, 43, 44]),
    ("familiar_lost_things",          [45, 46, 47, 48]),
]

EXPECTED_SIZE = (1254, 1254)


def main() -> int:
    files = sorted(p.name for p in SRC.glob("*.png"))
    if not files:
        print(f"no PNG files in {SRC}", file=sys.stderr)
        return 1

    rows = []
    used = set()
    for familiar_id, indices in NEW_FAMILIARS:
        if len(indices) != len(VARIANTS):
            print(f"{familiar_id}: expected {len(VARIANTS)} sources", file=sys.stderr)
            return 1
        for variant, idx in zip(VARIANTS, indices):
            if not 1 <= idx <= len(files):
                print(f"{familiar_id}/{variant}: index {idx} out of range", file=sys.stderr)
                return 1
            if idx in used:
                print(f"{familiar_id}/{variant}: index {idx} used twice", file=sys.stderr)
                return 1
            used.add(idx)
            rows.append((familiar_id, variant, files[idx - 1]))

    OUT.write_text(
        "".join(f"{fid}\t{variant}\t{name}\n" for fid, variant, name in rows),
        encoding="utf-8",
    )
    print(f"{len(rows)} images -> {len(NEW_FAMILIARS)} familiars, written to {OUT.name}")

    unused = [files[i - 1] for i in range(1, len(files) + 1) if i not in used]
    if unused:
        print(f"unused ({len(unused)}):")
        for name in unused:
            print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
