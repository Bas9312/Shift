#!/usr/bin/env python3
"""Build the server-side familiar asset tree and the catalog seed.

Reads:
  tools/familiars_sources.tsv      - new artwork mapping (see build_familiar_map.py)
  app/src/main/res/drawable/*.webp - legacy artwork already shipped in the APK

Writes:
  SERVER/public_html/static/familiars/<id>/<variant>.webp
  tools/familiars_seed.sql

New artwork is re-encoded from PNG to webp. Legacy artwork is already webp at the target
width and is copied verbatim - re-encoding lossy over lossy only loses quality.
"""

import hashlib
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_NEW = ROOT / "newFamiliars"
SRC_LEGACY = ROOT / "app/src/main/res/drawable"
OUT = ROOT / "SERVER/public_html/static/familiars"
TSV = Path(__file__).resolve().parent / "familiars_sources.tsv"
SEED = Path(__file__).resolve().parent / "familiars_seed.sql"

TARGET_WIDTH = 1024
QUALITY = "82"
VARIANTS = ["day1", "day2", "day3", "night"]

# Legacy familiars, in the order they appeared in models/Familiar.kt. IDs are already
# stored in users.familiar and must not change.
LEGACY = [
    ("familiar_mirror",            "Осколок зеркала"),
    ("familiar_vaynera_spirit",    "Дух улицы Вайнера"),
    ("familiar_weird_compass",     'Компас "туда где странно"'),
    ("familiar_gentlemans_tear",   'Кубок "Слеза джентльмена"'),
    ("familiar_dobyvala",          "Добывала"),
    ("familiar_abyss_eater",       "Зев бездны"),
    ("familiar_earth_cat",         "Земляная кошка"),
    ("familiar_malachite_lizard",  "Малахитовая ящерица"),
    ("familiar_fox",               "Чудо-лиса"),
]

NEW_NAMES = {
    "familiar_ping_penguin":         "Пингвин Пинг",
    "familiar_five_more_minutes":    "Ещё пять минут",
    "familiar_public_wifi":          "Кусочек общественного Wi-Fi",
    "familiar_arkady":               "Бомж-пророк Аркадий",
    "familiar_bad_advice":           "Дух плохого совета",
    "familiar_told_you_raven":       "Ворон «Я же говорил»",
    "familiar_bureaucracy_imp":      "Мелкий бес бюрократии",
    "familiar_common_sense_toad":    "Жаба здравого смысла",
    "familiar_literal_pigeon":       "Голубь-почтальон",
    "familiar_unread_notifications": "Барабашка непрочитанных уведомлений",
    "familiar_lost_things":          "Дух потерянных вещей",
}

# Custom one-off familiars already assigned to players. No artwork, hidden from the picker,
# kept so the foreign key on users.familiar can be added without losing data.
CUSTOM = [
    ("familiar_glazastik",        "Глазастик",                   "creature"),
    ("familiar_dancefloor_queen", "Королева Танцпола",           "creature"),
    ("familiar_player_kristina",  "Матеюнс Кристина Валерьевна", "player"),
]


def legacy_source(familiar_id: str, variant: str) -> Path:
    """Resolve a legacy drawable. Naming is inconsistent: familiar_fox2 but familiar_mirror_2."""
    if variant == "day1":
        candidates = [f"{familiar_id}.webp"]
    elif variant == "night":
        candidates = [f"{familiar_id}_night.webp"]
    else:
        n = variant[-1]
        candidates = [f"{familiar_id}{n}.webp", f"{familiar_id}_{n}.webp"]
    for name in candidates:
        path = SRC_LEGACY / name
        if path.exists():
            return path
    raise FileNotFoundError(f"{familiar_id}/{variant}: tried {candidates}")


def convert(src: Path, dst: Path) -> None:
    subprocess.run(
        # alpha-quality=100 keeps the cutout mask lossless (lossy alpha fringes the edges);
        # method=6 is the slow encoder, worth ~5% here for a one-off batch.
        ["convert", str(src), "-resize", f"{TARGET_WIDTH}x{TARGET_WIDTH}>",
         "-quality", QUALITY, "-define", "webp:alpha-quality=100",
         "-define", "webp:method=6", str(dst)],
        check=True,
    )


def version_of(paths: list[Path]) -> str:
    digest = hashlib.sha1()
    for path in paths:
        digest.update(path.read_bytes())
    return digest.hexdigest()[:8]


def sql_quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> int:
    if not TSV.exists():
        print(f"missing {TSV}; run build_familiar_map.py first", file=sys.stderr)
        return 1

    new_map: dict[str, dict[str, str]] = {}
    order: list[str] = []
    for line in TSV.read_text(encoding="utf-8").splitlines():
        familiar_id, variant, name = line.split("\t")
        if familiar_id not in new_map:
            new_map[familiar_id] = {}
            order.append(familiar_id)
        new_map[familiar_id][variant] = name

    rows = []
    sort_order = 0

    for familiar_id, display_name in LEGACY:
        sort_order += 10
        target = OUT / familiar_id
        target.mkdir(parents=True, exist_ok=True)
        written = []
        for variant in VARIANTS:
            dst = target / f"{variant}.webp"
            shutil.copyfile(legacy_source(familiar_id, variant), dst)
            written.append(dst)
        rows.append((familiar_id, display_name, "creature", ",".join(VARIANTS),
                     version_of(written), 1, sort_order))
        print(f"  legacy {familiar_id}: 4 files copied")

    for familiar_id in order:
        sort_order += 10
        target = OUT / familiar_id
        target.mkdir(parents=True, exist_ok=True)
        written = []
        for variant in VARIANTS:
            dst = target / f"{variant}.webp"
            convert(SRC_NEW / new_map[familiar_id][variant], dst)
            written.append(dst)
        rows.append((familiar_id, NEW_NAMES[familiar_id], "creature", ",".join(VARIANTS),
                     version_of(written), 1, sort_order))
        total = sum(p.stat().st_size for p in written) // 1024
        print(f"  new    {familiar_id}: 4 files, {total} KB")

    sort_order = 900
    for familiar_id, display_name, kind in CUSTOM:
        sort_order += 1
        rows.append((familiar_id, display_name, kind, "", "1", 0, sort_order))

    lines = [
        "-- Generated by tools/prepare_familiars.py. Do not edit by hand.",
        "-- description is intentionally left NULL: the ability text lives in the owner's",
        "-- design document and is not duplicated here.",
        "INSERT INTO familiars (id, name, kind, variants, image_version, is_listed, sort_order) VALUES",
    ]
    values = [
        f"  ({sql_quote(i)}, {sql_quote(n)}, {sql_quote(k)}, {sql_quote(v)}, "
        f"{sql_quote(ver)}, {listed}, {so})"
        for i, n, k, v, ver, listed, so in rows
    ]
    lines.append(",\n".join(values) + ";")
    SEED.write_text("\n".join(lines) + "\n", encoding="utf-8")

    size = sum(f.stat().st_size for f in OUT.rglob("*.webp"))
    print(f"\n{len(rows)} catalog rows -> {SEED.name}")
    print(f"{len(list(OUT.rglob('*.webp')))} images, {size / 1024 / 1024:.1f} MB total in {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
