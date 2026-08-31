#!/usr/bin/env python3
"""Build sinhala_freq.db from nlpcuom Sinhala word-frequency lists.

Sources (https://github.com/nlpcuom/Word-Frequency-List-for-Sinhala):
  verified — verified_word_list_200K.si (~280k words, default previously)
  2m       — word_frequency_list_2M.zip (~2.1M words)

Usage:
  python scripts/build_sinhala_freq_db.py
  python scripts/build_sinhala_freq_db.py --source 2m
  python scripts/build_sinhala_freq_db.py --source verified --limit 100000
"""

from __future__ import annotations

import argparse
import gzip
import shutil
import sqlite3
import sys
import urllib.request
import zipfile
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "app" / "src" / "main" / "assets" / "sinhala_freq.db.gz"

SOURCES = {
    "verified": {
        "url": (
            "https://raw.githubusercontent.com/nlpcuom/"
            "Word-Frequency-List-for-Sinhala/main/verified_word_list_200K.si"
        ),
        "cache": ROOT / "scripts" / ".verified_word_list_200K.si",
        "default_limit": 280603,
    },
    "2m": {
        "url": (
            "https://raw.githubusercontent.com/nlpcuom/"
            "Word-Frequency-List-for-Sinhala/main/word_frequency_list_2M.zip"
        ),
        "cache": ROOT / "scripts" / ".word_frequency_list_2M.zip",
        "default_limit": 2_138_021,
    },
}


def is_sinhala_word(word: str) -> bool:
    if not word or len(word) > 48:
        return False
    sinhala = 0
    for ch in word:
        code = ord(ch)
        if 0x0D80 <= code <= 0x0DFF:
            sinhala += 1
        elif ch.isascii() and (ch.isalpha() or ch.isdigit()):
            return False
    return sinhala > 0 and sinhala >= len(word) // 2


def download(source_url: str, dest: Path) -> None:
    print(f"Downloading {source_url} ...")
    with urllib.request.urlopen(source_url, timeout=300) as resp:
        dest.write_bytes(resp.read())


def iter_verified_lines(path: Path) -> Iterable[str]:
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            yield line


def iter_2m_lines(zip_path: Path) -> Iterable[str]:
    with zipfile.ZipFile(zip_path) as archive:
        inner = archive.namelist()[0]
        with archive.open(inner) as handle:
            for raw in handle:
                yield raw.decode("utf-8")


def ensure_source(source_key: str) -> Iterable[str]:
    meta = SOURCES[source_key]
    cache: Path = meta["cache"]
    if not cache.exists():
        download(meta["url"], cache)
    if source_key == "2m":
        return iter_2m_lines(cache)
    return iter_verified_lines(cache)


def parse_line(line: str) -> tuple[str, int] | None:
    line = line.strip()
    if not line:
        return None
    parts = line.split()
    if len(parts) < 2:
        return None
    word = parts[0]
    try:
        freq = int(parts[-1])
    except ValueError:
        return None
    if not is_sinhala_word(word):
        return None
    return word, freq


def build_db(lines: Iterable[str], out_path: Path, limit: int) -> int:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if out_path.exists():
        out_path.unlink()

    conn = sqlite3.connect(out_path)
    conn.execute("PRAGMA journal_mode=OFF")
    conn.execute("PRAGMA synchronous=OFF")
    conn.execute("PRAGMA temp_store=MEMORY")
    conn.execute(
        "CREATE TABLE words (word TEXT NOT NULL PRIMARY KEY, freq INTEGER NOT NULL)"
    )

    inserted = 0
    batch: list[tuple[str, int]] = []
    for line in lines:
        if inserted >= limit:
            break
        parsed = parse_line(line)
        if parsed is None:
            continue
        batch.append(parsed)
        inserted += 1
        if len(batch) >= 5000:
            conn.executemany("INSERT OR REPLACE INTO words(word, freq) VALUES (?, ?)", batch)
            batch.clear()
            if inserted % 100000 == 0:
                print(f"  ... {inserted} words")

    if batch:
        conn.executemany("INSERT OR REPLACE INTO words(word, freq) VALUES (?, ?)", batch)

    print("Creating index...")
    conn.execute("CREATE INDEX idx_words_word ON words(word)")
    conn.commit()
    conn.execute("ANALYZE")
    conn.close()
    return inserted


def gzip_db(db_path: Path, gz_path: Path) -> None:
    print(f"Compressing to {gz_path.name} ...")
    with db_path.open("rb") as src, gzip.open(gz_path, "wb", compresslevel=9) as dest:
        shutil.copyfileobj(src, dest)
    db_path.unlink()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", choices=sorted(SOURCES), default="2m")
    parser.add_argument("--input", type=Path, help="Local .si or .zip input file")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--limit", type=int, default=-1)
    parser.add_argument("--keep-raw-db", action="store_true")
    args = parser.parse_args()

    meta = SOURCES[args.source]
    limit = meta["default_limit"] if args.limit < 0 else args.limit

    raw_db = args.out.with_suffix("") if args.out.suffix == ".gz" else args.out
    if raw_db.suffix != ".db":
        raw_db = args.out.parent / "sinhala_freq.db"

    if args.input:
        if args.input.suffix.lower() == ".zip":
            lines = iter_2m_lines(args.input)
        else:
            lines = iter_verified_lines(args.input)
    else:
        lines = ensure_source(args.source)

    print(f"Building from {args.source} (limit={limit}) ...")
    count = build_db(lines, raw_db, limit)
    raw_mb = raw_db.stat().st_size / (1024 * 1024)
    print(f"Built {count} words -> {raw_db} ({raw_mb:.1f} MB)")

    gz_path = args.out if args.out.suffix == ".gz" else raw_db.with_suffix(raw_db.suffix + ".gz")
    gzip_db(raw_db, gz_path)
    gz_mb = gz_path.stat().st_size / (1024 * 1024)
    print(f"Wrote {gz_path} ({gz_mb:.1f} MB)")
    if not args.keep_raw_db and raw_db.exists():
        raw_db.unlink()
    if gz_mb > 95:
        print("WARNING: compressed DB exceeds ~95 MB — may be too large for GitHub.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
