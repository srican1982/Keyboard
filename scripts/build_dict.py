#!/usr/bin/env python3
words = [
    ("enna", "\u0d91\u0db1\u0dca\u0db1"),
    ("ennam", "\u0d91\u0db1\u0dca\u0db1\u0db8\u0dca"),
    ("en", "\u0d91\u0db1\u0dca"),
    ("mama", "\u0db8\u0db8"),
    ("amma", "\u0d85\u0db8\u0dca\u0db8"),
    ("thaththa", "\u0dad\u0dcf\u0dad\u0dca\u0dad\u0dcf"),
    ("nangi", "\u0dba\u0d82\u0da2\u0dd2"),
    ("malli", "\u0db8\u0dbd\u0dca\u0dbd\u0dd2"),
    ("ayubowan", "\u0d86\u0dba\u0dd4\u0db6\u0ddc\u0dc0\u0db1\u0dca"),
    ("kohomada", "\u0d9a\u0ddc\u0dc4\u0ddc\u0db8\u0daf"),
    ("kohomath", "\u0d9a\u0ddc\u0dc4\u0ddc\u0db8\u0daf"),
    ("stuti", "\u0dc3\u0dca\u0dad\u0dd2\u0dad\u0dd2"),
    ("istuti", "\u0d87\u0dc3\u0dca\u0dad\u0dd2\u0dad\u0dd2"),
    ("oyata", "\u0d94\u0dba\u0dcf\u0da7"),
    ("mata", "\u0db8\u0da7"),
    ("hari", "\u0dc4\u0dba\u0dd2"),
    ("ow", "\u0d94\u0dc0\u0dca"),
    ("na", "\u0dab"),
    ("yanna", "\u0dba\u0db1\u0dca\u0db1"),
    ("denna", "\u0daf\u0dd9\u0db1\u0dca\u0db1"),
    ("ganna", "\u0d9c\u0db1\u0dca\u0db1"),
    ("balanna", "\u0db6\u0db1\u0dca\u0db1"),
    ("kiyanna", "\u0d9a\u0dd2\u0dba\u0db1\u0dca\u0db1"),
    ("hondai", "\u0dc4\u0ddc\u0db1\u0dca\u0daf\u0dd2"),
    ("watura", "\u0dc0\u0dad\u0dd4\u0db1\u0dca"),
]

out = r"f:\newacu\sinhala-keyboard\app\src\main\assets\sinhala_dict.txt"
with open(out, "w", encoding="utf-8") as f:
    for k, v in words:
        f.write(f"{k}|{v}\n")
print(len(words))
