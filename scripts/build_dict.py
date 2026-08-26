#!/usr/bin/env python3
"""Build sinhala_dict.txt with manual overrides for common Singlish words."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "sinhala_dict.txt"

ENTRIES: dict[str, tuple[str, int]] = {
    "mama": ("මම", 100),
    "amma": ("අම්ම", 95),
    "ammaa": ("අම්මා", 95),
    "thaththa": ("තාත්තා", 100),
    "taaththaa": ("තාත්තා", 95),
    "mae": ("මැ", 85),
    "kae": ("කැ", 85),
    "ae": ("ඇ", 80),
    "aee": ("ඈ", 80),
    "thaththi": ("තාත්ති", 90),
    "nangi": ("නංගි", 95),
    "malli": ("මල්ලි", 95),
    "ayubowan": ("ආයුබොවන්", 100),
    "aayuboowan": ("ආයුබෝවන්", 90),
    "kohomada": ("කොහොමද", 100),
    "kohomath": ("කොහොමද", 95),
    "kohomd": ("කොහොමද", 80),
    "kohmd": ("කොහොමද", 75),
    "monawada": ("මොනවද", 90),
    "mokada": ("මොකද", 90),
    "enna": ("එන්න", 100),
    "ennam": ("එන්නම්", 95),
    "en": ("එන්", 80),
    "yanna": ("යන්න", 100),
    "denna": ("දෙන්න", 95),
    "ganna": ("ගන්න", 95),
    "balanna": ("බලන්න", 95),
    "kiyanna": ("කියන්න", 95),
    "enawa": ("එනවා", 90),
    "yanawa": ("යනවා", 90),
    "thiyenawa": ("තියෙනවා", 90),
    "karanawa": ("කරනවා", 90),
    "balanawa": ("බලනවා", 85),
    "gannawa": ("ගන්නවා", 85),
    "gihin": ("ගිහින්", 85),
    "oyata": ("ඔයාට", 95),
    "mata": ("මට", 100),
    "oyaa": ("ඔයා", 95),
    "mage": ("මගේ", 95),
    "oyage": ("ඔයාගේ", 90),
    "api": ("අපි", 95),
    "hari": ("හරි", 100),
    "ow": ("ඔව්", 95),
    "na": ("නැ", 90),
    "ey": ("එයි", 85),
    "eyi": ("එයි", 80),
    "dan": ("දැන්", 90),
    "saha": ("සහ", 90),
    "sama": ("සම", 80),
    "hondai": ("හොඳයි", 100),
    "hondayi": ("හොඳයි", 100),
    "lassanai": ("ලස්සනයි", 95),
    "sundara": ("සුන්දර", 90),
    "bohoma": ("බොහොම", 90),
    "pin": ("පින්", 85),
    "watura": ("වතුර", 95),
    "stuti": ("\u0dc3\u0dca\u0dad\u0dd2\u0dad\u0dd2", 90),
    "istuti": ("\u0d87\u0dc3\u0dca\u0dad\u0dd2\u0dad\u0dd2", 90),
    "lankawa": ("ලංකාව", 100),
    "lanka": ("ලංකා", 95),
    "gedhara": ("ගෙදර", 95),
    "paasala": ("පාසල", 90),
    "pasala": ("පාසල", 85),
    "colombo": ("කොළඹ", 90),
    "kandy": ("මහනුවර", 80),
    "shri": ("ශ්‍රී", 90),
    "bankuwa": ("බැංකුව", 90),
    "banga": ("බැංකු", 85),
    "bng": ("බැංකුව", 70),
    "bnk": ("බැංකු", 60),
    "kata": ("කතා", 85),
}

def main() -> None:
    lines = [
        f"{key}|{value}|{freq}"
        for key, (value, freq) in sorted(ENTRIES.items(), key=lambda x: -x[1][1])
    ]
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(lines)} entries to {OUT}")


if __name__ == "__main__":
    main()
