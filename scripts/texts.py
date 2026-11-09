#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""
fork: the app's Korean texts as one spreadsheet (CSV, opens in Excel / Google Sheets / Numbers), and back.

  python3 scripts/texts.py export texts.csv   # all texts: file, key, current Korean, English, an empty "new" column
  python3 scripts/texts.py apply texts.csv    # writes every filled "new" cell into the Korean string files
  python3 scripts/texts.py sent texts.txt     # applies what the app's "Edit texts" screen sent (More > Edit texts)

Only the "바꿀 한국어" (new Korean) column is read back; empty cells change nothing. Line breaks are written as \\n,
placeholders like %s / %1$s must stay.
"""
import csv
import html
import re
import sys
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
FILES = [  # (label in the sheet, file name)
    ("DTboard", "fork_strings.xml"),
    ("HeliBoard", "strings.xml"),
    ("음성 안내", "strings-talkback-descriptions.xml"),
]
HEAD = ["구분", "키", "지금 한국어", "영어", "바꿀 한국어"]
STRING = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.S)


def read(path: Path):
    if not path.exists():
        return {}
    return {m.group(1): (m.group(2), m.group(3)) for m in STRING.finditer(path.read_text(encoding="utf-8"))}


def show(raw: str) -> str:
    """the text as it shows in the app (Android's escapes undone, entities decoded)"""
    t = raw.strip()
    if len(t) >= 2 and t[0] == '"' and t[-1] == '"':
        t = t[1:-1]
    t = t.replace("\\'", "'").replace('\\"', '"').replace("\\@", "@").replace("\\?", "?")
    return html.unescape(t)


def store(text: str) -> str:
    """the text written back in Android's string format"""
    t = text.replace("\r\n", "\n").replace("\n", "\\n")
    t = re.sub(r"&(?!(amp|lt|gt|quot|apos|#\d+);)", "&amp;", t)
    t = t.replace("\\'", "'").replace("'", "\\'").replace('\\"', '"').replace('"', '\\"')
    if t.startswith("@") or t.startswith("?"):
        t = "\\" + t
    return t


def export(out: Path):
    with out.open("w", encoding="utf-8-sig", newline="") as f:  # BOM: Excel opens it as UTF-8
        w = csv.writer(f)
        w.writerow(HEAD)
        for label, name in FILES:
            ko = read(RES / "values-ko" / name)
            en = read(RES / "values" / name)
            for key, (attrs, raw) in en.items():
                if 'translatable="false"' in attrs:
                    continue
                w.writerow([label, key, show(ko[key][1]) if key in ko else "", show(raw), ""])
    print(f"wrote {out}")


def apply(src: Path):
    changes = {}
    with src.open(encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            new = (row.get("바꿀 한국어") or "").strip()
            if new:
                changes.setdefault(row["구분"], {})[row["키"]] = new
    write(changes)


def sent(src: Path):
    """lines of label, key, old text, new text (tabs and line breaks escaped), as the app's text editor sends them"""
    def unesc(t: str) -> str:
        return re.sub(r"\\(.)", lambda m: {"t": "\t", "n": "\n"}.get(m.group(1), m.group(1)), t)
    changes = {}
    for line in src.read_text(encoding="utf-8-sig").splitlines():
        parts = line.split("\t")
        if len(parts) == 4 and parts[0] in dict(FILES):
            changes.setdefault(parts[0], {})[parts[1]] = unesc(parts[3])
    write(changes)


def write(changes):
    files = dict(FILES)
    total = 0
    for label, keys in changes.items():
        path = RES / "values-ko" / files[label]
        text = path.read_text(encoding="utf-8")
        for key, new in keys.items():
            value = store(new)
            pattern = re.compile(r'(<string name="' + re.escape(key) + r'"[^>]*>)(.*?)(</string>)', re.S)
            if pattern.search(text):
                text = pattern.sub(lambda m: m.group(1) + value + m.group(3), text, count=1)
            else:  # not translated yet: added
                i = text.rindex("</resources>")
                text = text[:i] + f'    <string name="{key}">{value}</string>\n' + text[i:]
            total += 1
        path.write_text(text, encoding="utf-8")
    print(f"changed {total} texts")


if __name__ == "__main__":
    commands = {"export": export, "apply": apply, "sent": sent}
    if len(sys.argv) != 3 or sys.argv[1] not in commands:
        sys.exit(__doc__)
    commands[sys.argv[1]](Path(sys.argv[2]))
