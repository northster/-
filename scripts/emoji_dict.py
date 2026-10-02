#!/usr/bin/env python3
# fork: builds app/src/main/assets/emoji_keywords.txt from the Unicode CLDR emoji annotations (Unicode License v3),
# for the toolbar's emoji ideas widget (offline). Usage:
#   curl -o ko.json https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-annotations-full/annotations/ko/annotations.json
#   curl -o en.json .../annotations/en/annotations.json
#   python3 scripts/emoji_dict.py ko.json en.json app/src/main/assets/emoji_keywords.txt
# Format: one keyword per line, "keyword<TAB>emoji emoji ...", the emojis best first (the keyword is the emoji's
# name / a whole keyword before a word of a keyword).
import json, re, sys
from collections import OrderedDict

def is_emoji(s):
    if not s:
        return False
    cp = ord(s[0])
    if 0x1F3FB <= cp <= 0x1F3FF:  # skin tone modifiers alone
        return False
    return cp >= 0x1F000 or 0x2600 <= cp <= 0x27BF or 0x2190 <= cp <= 0x2BFF or 0x2300 <= cp <= 0x23FF or cp in (0xA9, 0xAE)

def main(out, *files):
    index = {}  # keyword -> {emoji: score}
    order = []
    for f in files:
        ann = json.load(open(f))['annotations']['annotations']
        for emoji, v in ann.items():
            if not is_emoji(emoji):
                continue
            order.append(emoji)
            names = v.get('tts', [])
            for kw in v.get('default', []) + names:
                kw = kw.strip().lower()
                if not kw:
                    continue
                score = 3 if kw in [n.lower() for n in names] else 2
                index.setdefault(kw, {})
                index[kw][emoji] = max(index[kw].get(emoji, 0), score)
                # each word of a keyword too, weaker
                words = [w for w in re.split(r'[\s:,]+', kw) if len(w) >= 1]
                if len(words) > 1:
                    for w in words:
                        index.setdefault(w, {})
                        index[w][emoji] = max(index[w].get(emoji, 0), 1)
    rank = {e: i for i, e in enumerate(OrderedDict.fromkeys(order))}
    with open(out, 'w') as o:
        for kw in sorted(index):
            ems = sorted(index[kw].items(), key=lambda x: (-x[1], rank.get(x[0], 0)))
            o.write(kw + '\t' + ' '.join(e for e, _ in ems[:12]) + '\n')

if __name__ == '__main__':
    main(sys.argv[-1], *sys.argv[1:-1])
