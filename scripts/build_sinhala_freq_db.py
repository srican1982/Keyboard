#!/usr/bin/env python3
"""Build sinhala_freq.db from nlpcuom verified word-frequency list.

Source: https://github.com/nlpcuom/Word-Frequency-List-for-Sinhala
File: verified_word_list_200K.si (word<TAB>frequency)

Usage:
  python scripts/build_sinhala_freq_db.py [--limit 50000]
  python scripts/build_sinhala_freq_db.py --input path/to/verified_word_list_200K.si
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
import urllib.request
from pathlib import Path

DEFAULT_URL = (
    "https://raw.githubusercontent.com/nlpcuom/"
    "Word-Frequency-List-for-Sinhala/main/verified_word_list_200K.si"
)
ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "app" / "src" / "main" / "assets" / "sinhala_freq.db"


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


def load_lines(source: Path) -> list[str]:
    return source.read_text(encoding="utf-8").splitlines()


def download(source_url: str, dest: Path) -> None:
    print(f"Downloading {source_url} ...")
    with urllib.request.urlopen(source_url, timeout=120) as resp:
        dest.write_bytes(resp.read())


def build_db(lines: list[str], out_path: Path, limit: int) -> int:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if out_path.exists():
        out_path.unlink()

    conn = sqlite3.connect(out_path)
    conn.execute("PRAGMA journal_mode=OFF")
    conn.execute("PRAGMA synchronous=OFF")
    conn.execute(
        "CREATE TABLE words (word TEXT NOT NULL PRIMARY KEY, freq INTEGER NOT NULL)"
    )
    conn.execute("CREATE INDEX idx_words_word ON words(word)")

    inserted = 0
    batch: list[tuple[str, int]] = []
    for line in lines:
        if inserted >= limit:
            break
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        word = parts[0]
        try:
            freq = int(parts[1])
        except ValueError:
            continue
        if not is_sinhala_word(word):
            continue
        batch.append((word, freq))
        inserted += 1
        if len(batch) >= 2000:
            conn.executemany("INSERT OR REPLACE INTO words(word, freq) VALUES (?, ?)", batch)
            batch.clear()

    if batch:
        conn.executemany("INSERT OR REPLACE INTO words(word, freq) VALUES (?, ?)", batch)

    conn.commit()
    conn.execute("ANALYZE")
    conn.close()
    return inserted


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, help="Local verified_word_list_200K.si")
    parser.add_argument("--url", default=DEFAULT_URL)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--limit", type=int, default=280603)
    args = parser.parse_args()

    temp_input = ROOT / "scripts" / ".verified_word_list_200K.si"
    if args.input:
        source = args.input
    else:
        if not temp_input.exists():
            download(args.url, temp_input)
        source = temp_input

    lines = load_lines(source)
    count = build_db(lines, args.out, args.limit)
    size_kb = args.out.stat().st_size // 1024
    print(f"Wrote {count} words to {args.out} ({size_kb} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
