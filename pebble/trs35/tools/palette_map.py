#!/usr/bin/env python3
"""Map colors to the nearest of the Pebble 64-color palette (basalt).

Distance is CIE76 delta-E in Lab space, which matches what the eye sees
better than plain RGB distance (neon greens especially).

  python3 tools/palette_map.py '#C8FF00' '#00D8FF'      # map hex colors
  python3 tools/palette_map.py --image ref.png -n 8      # dominant colors of an image

Needs Pillow only for --image.
"""
import argparse
import sys

LEVELS = (0x00, 0x55, 0xAA, 0xFF)

# Names from the Pebble SDK (GColor*), indexed by (r, g, b) level 0..3.
NAMES = {
    (0, 0, 0): "Black", (0, 0, 1): "OxfordBlue", (0, 0, 2): "DukeBlue", (0, 0, 3): "Blue",
    (0, 1, 0): "DarkGreen", (0, 1, 1): "MidnightGreen", (0, 1, 2): "CobaltBlue", (0, 1, 3): "BlueMoon",
    (0, 2, 0): "IslamicGreen", (0, 2, 1): "JaegerGreen", (0, 2, 2): "TiffanyBlue", (0, 2, 3): "VividCerulean",
    (0, 3, 0): "Green", (0, 3, 1): "Malachite", (0, 3, 2): "MediumSpringGreen", (0, 3, 3): "Cyan",
    (1, 0, 0): "BulgarianRose", (1, 0, 1): "ImperialPurple", (1, 0, 2): "Indigo", (1, 0, 3): "ElectricUltramarine",
    (1, 1, 0): "ArmyGreen", (1, 1, 1): "DarkGray", (1, 1, 2): "Liberty", (1, 1, 3): "VeryLightBlue",
    (1, 2, 0): "KellyGreen", (1, 2, 1): "MayGreen", (1, 2, 2): "CadetBlue", (1, 2, 3): "PictonBlue",
    (1, 3, 0): "BrightGreen", (1, 3, 1): "ScreaminGreen", (1, 3, 2): "MediumAquamarine", (1, 3, 3): "ElectricBlue",
    (2, 0, 0): "DarkCandyAppleRed", (2, 0, 1): "JazzberryJam", (2, 0, 2): "Purple", (2, 0, 3): "VividViolet",
    (2, 1, 0): "WindsorTan", (2, 1, 1): "RoseVale", (2, 1, 2): "Purpureus", (2, 1, 3): "LavenderIndigo",
    (2, 2, 0): "Limerick", (2, 2, 1): "Brass", (2, 2, 2): "LightGray", (2, 2, 3): "BabyBlueEyes",
    (2, 3, 0): "SpringBud", (2, 3, 1): "Inchworm", (2, 3, 2): "MintGreen", (2, 3, 3): "Celeste",
    (3, 0, 0): "Red", (3, 0, 1): "Folly", (3, 0, 2): "FashionMagenta", (3, 0, 3): "Magenta",
    (3, 1, 0): "Orange", (3, 1, 1): "SunsetOrange", (3, 1, 2): "BrilliantRose", (3, 1, 3): "ShockingPink",
    (3, 2, 0): "ChromeYellow", (3, 2, 1): "Rajah", (3, 2, 2): "Melon", (3, 2, 3): "RichBrilliantLavender",
    (3, 3, 0): "Yellow", (3, 3, 1): "Icterine", (3, 3, 2): "PastelYellow", (3, 3, 3): "White",
}


def srgb_to_lab(rgb):
    def lin(c):
        c /= 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(c) for c in rgb)
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = (0.2126 * r + 0.7152 * g + 0.0722 * b)
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883

    def f(t):
        return t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    fx, fy, fz = f(x), f(y), f(z)
    return 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)


PALETTE = []
for idx, name in NAMES.items():
    rgb = tuple(LEVELS[i] for i in idx)
    PALETTE.append((name, rgb, srgb_to_lab(rgb)))


def nearest(rgb, k=1):
    lab = srgb_to_lab(rgb)
    scored = sorted(PALETTE, key=lambda p: sum((a - b) ** 2 for a, b in zip(lab, p[2])))
    out = []
    for name, prgb, plab in scored[:k]:
        de = sum((a - b) ** 2 for a, b in zip(lab, plab)) ** 0.5
        out.append((name, prgb, de))
    return out


def parse_hex(s):
    s = s.strip().lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def hexs(rgb):
    return '#%02X%02X%02X' % rgb


def dominant(path, n):
    from PIL import Image
    im = Image.open(path).convert('RGB')
    im.thumbnail((256, 256))
    q = im.quantize(colors=n, method=Image.Quantize.MEDIANCUT)
    pal = q.getpalette()
    counts = sorted(q.getcolors(), reverse=True)
    total = sum(c for c, _ in counts)
    return [(tuple(pal[i * 3:i * 3 + 3]), c / total) for c, i in counts]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('colors', nargs='*', help='hex colors like #C8FF00')
    ap.add_argument('--image', help='extract dominant colors from an image')
    ap.add_argument('-n', type=int, default=8, help='number of colors for --image')
    args = ap.parse_args()

    rows = []
    if args.image:
        for rgb, share in dominant(args.image, args.n):
            rows.append((hexs(rgb), rgb, '%4.1f%%' % (share * 100)))
    for c in args.colors:
        rgb = parse_hex(c)
        rows.append((hexs(rgb), rgb, ''))
    if not rows:
        ap.print_help()
        return 1

    print('| source | share | nearest GColor | hex | dE | runner-up |')
    print('|---|---|---|---|---|---|')
    for h, rgb, share in rows:
        best, second = nearest(rgb, 2)
        print('| %s | %s | GColor%s | %s | %.1f | %s (%.1f) |' % (
            h, share, best[0], hexs(best[1]), best[2], second[0], second[2]))
    return 0


if __name__ == '__main__':
    sys.exit(main())
