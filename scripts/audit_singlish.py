#!/usr/bin/env python3
"""Audit Singlish roman keys against the frequency corpus.

Two-phase workflow (as requested):
  1. Scan the corpus first — find high-frequency Sinhala words missing roman keys
  2. Check the most-used list first when running converter tests

Usage:
  python scripts/audit_singlish.py scan --top 10000
  python scripts/audit_singlish.py generate
  python scripts/audit_singlish.py report

Then run converter tests (CI or local Gradle):
  gradle test --tests SinglishCorpusAuditTest
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "app" / "src" / "main" / "assets" / "sinhala_freq.db"
DICT_PATH = ROOT / "app" / "src" / "main" / "assets" / "sinhala_dict.txt"
PRIORITY_PATH = ROOT / "scripts" / "most_used_singlish.txt"
CORPUS_TEST_PATH = ROOT / "app" / "src" / "test" / "resources" / "singlish_audit_corpus.txt"
PRIORITY_TEST_PATH = ROOT / "app" / "src" / "test" / "resources" / "singlish_audit_priority.txt"


def load_dict() -> dict[str, tuple[str, int]]:
    """roman_lower -> (sinhala, manual_freq)"""
    out: dict[str, tuple[str, int]] = {}
    if not DICT_PATH.exists():
        return out
    for line in DICT_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if len(parts) < 2:
            continue
        roman, sinhala = parts[0].strip(), parts[1].strip()
        freq = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 1
        if roman and sinhala:
            out[roman.lower()] = (sinhala, freq)
    return out


def load_priority() -> list[tuple[str, str, str]]:
    """(roman, sinhala, note) — user/Desh verified, checked first in tests."""
    rows: list[tuple[str, str, str]] = []
    if not PRIORITY_PATH.exists():
        return rows
    for line in PRIORITY_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if len(parts) < 2:
            continue
        roman, sinhala = parts[0].strip(), parts[1].strip()
        note = parts[2].strip() if len(parts) > 2 else ""
        if roman and sinhala:
            rows.append((roman, sinhala, note))
    return rows


def load_corpus(top: int) -> list[tuple[str, int]]:
    if not DB_PATH.exists():
        print(f"Missing {DB_PATH} — build with scripts/build_sinhala_freq_db.py", file=sys.stderr)
        return []
    conn = sqlite3.connect(DB_PATH)
    rows = conn.execute(
        "SELECT word, freq FROM words ORDER BY freq DESC LIMIT ?",
        (top,),
    ).fetchall()
    conn.close()
    return [(w, f) for w, f in rows]


def sinhala_to_romans(sinhala: str, roman_by_sinhala: dict[str, list[str]]) -> list[str]:
    return roman_by_sinhala.get(sinhala, [])


def cmd_scan(top: int) -> int:
    corpus = load_corpus(top)
    dict_entries = load_dict()

    roman_by_sinhala: dict[str, list[str]] = {}
    for roman, (sinhala, _) in dict_entries.items():
        roman_by_sinhala.setdefault(sinhala, []).append(roman)

    covered: list[tuple[str, int, list[str]]] = []
    missing: list[tuple[str, int]] = []

    for word, freq in corpus:
        romans = sinhala_to_romans(word, roman_by_sinhala)
        if romans:
            covered.append((word, freq, romans))
        else:
            missing.append((word, freq))

    report_path = ROOT / "scripts" / "audit_scan_report.txt"
    lines = [
        f"=== Corpus scan (top {len(corpus):,} words) ===",
        f"  With roman key in sinhala_dict.txt : {len(covered):,}",
        f"  Missing roman key (need mapping) : {len(missing):,}",
        "",
        "Top 100 MISSING — add roman to most_used_singlish.txt or sinhala_dict.txt:",
    ]
    for word, freq in missing[:100]:
        lines.append(f"  freq={freq:>8}  len={len(word)}  roman|{word}")

    lines.extend(["", "Top 50 covered (have roman — converter-tested):"])
    for word, freq, romans in covered[:50]:
        lines.append(f"  freq={freq:>8}  {romans[0]} -> {word}")

    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"Corpus scan complete: {len(covered):,} covered, {len(missing):,} missing roman keys")
    print(f"Full report: {report_path.relative_to(ROOT)}")
    return 0


def cmd_generate(top: int) -> int:
    corpus = load_corpus(top)
    dict_entries = load_dict()
    priority = load_priority()

    corpus_freq = {w: f for w, f in corpus}

    roman_by_sinhala: dict[str, list[str]] = {}
    for roman, (sinhala, _) in dict_entries.items():
        roman_by_sinhala.setdefault(sinhala, []).append(roman)

    # Priority test file — most-used / user-entered list FIRST
    priority_pairs: list[tuple[str, str, int, str]] = []
    seen: set[tuple[str, str]] = set()

    for roman, sinhala, note in priority:
        key = (roman.lower(), sinhala)
        if key in seen:
            continue
        seen.add(key)
        freq = corpus_freq.get(sinhala, 0)
        priority_pairs.append((roman, sinhala, freq, note))

    # Corpus-backed pairs from dict, sorted by corpus frequency
    corpus_pairs: list[tuple[str, str, int]] = []
    for roman, (sinhala, _) in dict_entries.items():
        freq = corpus_freq.get(sinhala, 0)
        if freq <= 0:
            continue
        key = (roman.lower(), sinhala)
        if key in seen:
            continue
        seen.add(key)
        corpus_pairs.append((roman, sinhala, freq))

    corpus_pairs.sort(key=lambda x: (-x[2], x[0]))

    PRIORITY_TEST_PATH.parent.mkdir(parents=True, exist_ok=True)

    def write_test_file(path: Path, rows: list, include_note: bool = False) -> None:
        lines = ["# roman|sinhala|corpus_freq|note", "# Generated by scripts/audit_singlish.py — do not hand-edit corpus file"]
        for row in rows:
            if include_note:
                roman, sinhala, freq, note = row
                lines.append(f"{roman}|{sinhala}|{freq}|{note}")
            else:
                roman, sinhala, freq = row
                lines.append(f"{roman}|{sinhala}|{freq}")
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    write_test_file(PRIORITY_TEST_PATH, priority_pairs, include_note=True)
    write_test_file(CORPUS_TEST_PATH, corpus_pairs)

    print(f"Wrote {len(priority_pairs)} priority pairs -> {PRIORITY_TEST_PATH.relative_to(ROOT)}")
    print(f"Wrote {len(corpus_pairs)} corpus pairs   -> {CORPUS_TEST_PATH.relative_to(ROOT)}")
    print("Run: gradle test --tests SinglishCorpusAuditTest")
    return 0


def cmd_report() -> int:
    priority = load_priority()
    dict_entries = load_dict()
    corpus = load_corpus(5000)
    corpus_freq = {w: f for w, f in corpus}

    print("=== Priority list (checked FIRST in tests) ===")
    print(f"  {PRIORITY_PATH.relative_to(ROOT)}: {len(priority)} entries")
    for roman, sinhala, note in priority[:15]:
        f = corpus_freq.get(sinhala, 0)
        extra = f" (corpus freq {f})" if f else ""
        n = f" — {note}" if note else ""
        print(f"  {roman} -> {sinhala}{extra}{n}")

    print()
    print("=== sinhala_dict.txt summary ===")
    print(f"  {len(dict_entries)} roman keys")

    in_top5k = sum(1 for _, (s, _) in dict_entries.items() if s in corpus_freq)
    print(f"  {in_top5k} keys point to top-5000 corpus words")

    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit Singlish mappings against corpus")
    sub = parser.add_subparsers(dest="command", required=True)

    p_scan = sub.add_parser("scan", help="Phase 1: scan corpus for missing roman keys")
    p_scan.add_argument("--top", type=int, default=10000)

    p_gen = sub.add_parser("generate", help="Generate JUnit test resource files")
    p_gen.add_argument("--top", type=int, default=10000)

    sub.add_parser("report", help="Show priority + dict summary")

    args = parser.parse_args()
    if args.command == "scan":
        return cmd_scan(args.top)
    if args.command == "generate":
        return cmd_generate(args.top)
    if args.command == "report":
        return cmd_report()
    return 1


if __name__ == "__main__":
    sys.exit(main())
