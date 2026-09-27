# fork: dot matrix icons from pixelarticons (github.com/halfmage/pixelarticons, MIT License,
# Copyright (c) 2020 Gerrit Halfmann). Each pixel of the 24x24 icon becomes one round dot.
#
# usage: python3 scripts/pixel_icons.py <pixelarticons svg folder> app/src/main/res/drawable [preview.png]
# (the svg folder is in the npm package: npm pack pixelarticons, package/svg)
# Replaces the hand drawn icons of scripts/dot_icons.py with the same names.
import os
import re
import sys

# our icon name -> pixelarticons name
MAP = {
    'shift': 'arrow-big-up',
    'shift_locked': 'arrow-big-up-dash',
    'backspace': 'delete',
    'enter': 'corner-down-left',
    'globe': 'globe',
    'smile': 'smile',
    'search': 'search',
    'clipboard': 'clipboard',
    'paste': 'clipboard-note',
    'copy': 'copy',
    'cut': 'scissors',
    'select_all': 'square-dashed-cursor',
    'select_word': 'text-cursor',
    'undo': 'undo',
    'redo': 'redo',
    'left': 'chevron-left',
    'right': 'chevron-right',
    'check': 'check',
    'trash': 'trash',
    'pin': 'bookmark',
    'close': 'close',
    'keyboard': 'keyboard',
    'sparkles': 'sparkles',
    'translate': 'languages',
    'more': 'more-horizontal',
    'question': 'circle-question',
    'settings': 'settings-cog',
    'emoji_recents': 'clock',
    'emoji_people': 'user',
    'emoji_nature': 'leaf',
    'emoji_food': 'coffee',
    'emoji_travel': 'car',
    'emoji_activities': 'trophy',
    'emoji_objects': 'lightbulb',
    'emoji_symbols': 'heart',
    'emoji_flags': 'flag',
    'emoji_emoticons': 'laugh',
}

# drawn here on the same 24x24 grid ('o' = dot), for what the pack doesn't have
OWN = {
    'gif': """
........................
........................
........................
........................
........................
........................
..oooo...oooo...oooooo..
.oo..oo...oo....oo......
.oo.......oo....oo......
.oo.......oo....oo......
.oo.ooo...oo....ooooo...
.oo..oo...oo....oo......
.oo..oo...oo....oo......
.oo..oo...oo....oo......
..ooooo..oooo...oo......
""",
    'space': """
........................
........................
........................
........................
........................
........................
........................
........................
........................
........................
........................
........................
..oo................oo..
..oo................oo..
..oo................oo..
..oooooooooooooooooooo..
..oooooooooooooooooooo..
""",
}

TOKEN = re.compile(r'[MmHhVvLlZz]|-?\d*\.?\d+')


def subpaths(d):
    """polygons of an svg path made of M/H/V/L/Z commands (pixelarticons use nothing else)"""
    tokens = TOKEN.findall(d)
    polys, cur = [], []
    x = y = 0.0
    start = (0.0, 0.0)
    cmd = None
    i = 0

    def num():
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    while i < len(tokens):
        t = tokens[i]
        if re.fullmatch(r'[A-Za-z]', t):
            cmd = t
            i += 1
            if cmd in 'Zz':
                if cur:
                    polys.append(cur)
                cur = []
                x, y = start
                continue
        if cmd in 'Mm':
            nx, ny = num(), num()
            if cmd == 'm':
                nx, ny = x + nx, y + ny
            if cur:
                polys.append(cur)
            x, y = nx, ny
            start = (x, y)
            cur = [(x, y)]
            cmd = 'L' if cmd == 'M' else 'l'  # following pairs are lines
        elif cmd in 'Ll':
            nx, ny = num(), num()
            if cmd == 'l':
                nx, ny = x + nx, y + ny
            x, y = nx, ny
            cur.append((x, y))
        elif cmd in 'Hh':
            v = num()
            x = v if cmd == 'H' else x + v
            cur.append((x, y))
        elif cmd in 'Vv':
            v = num()
            y = v if cmd == 'V' else y + v
            cur.append((x, y))
        else:
            raise ValueError(f'unsupported command {cmd}')
    if cur:
        polys.append(cur)
    return polys


def raster(svg):
    """24x24 grid, a pixel is on when its center is inside the shape (nonzero winding, the svg default)"""
    polys = []
    for d in re.findall(r'<path[^>]* d="([^"]+)"', svg):
        polys += subpaths(d)
    grid = [[False] * 24 for _ in range(24)]
    for gy in range(24):
        for gx in range(24):
            px, py = gx + 0.5, gy + 0.5
            winding = 0
            for poly in polys:
                n = len(poly)
                for k in range(n):
                    (x1, y1), (x2, y2) = poly[k], poly[(k + 1) % n]
                    if (y1 > py) != (y2 > py) and px < x1 + (py - y1) * (x2 - x1) / (y2 - y1):
                        winding += 1 if y2 > y1 else -1
            grid[gy][gx] = winding != 0
    return grid


def filled(grid):
    """the shape with its inside filled (shift while shifted): everything the outside can't reach"""
    outside = [[False] * 24 for _ in range(24)]
    stack = [(x, y) for x in range(24) for y in (0, 23)] + [(x, y) for y in range(24) for x in (0, 23)]
    while stack:
        x, y = stack.pop()
        if not (0 <= x < 24 and 0 <= y < 24) or outside[y][x] or grid[y][x]:
            continue
        outside[y][x] = True
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return [[not outside[y][x] for x in range(24)] for y in range(24)]


def own(art):
    rows = art.strip('\n').split('\n')
    top = (24 - len(rows)) // 2
    grid = [[False] * 24 for _ in range(24)]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            grid[top + y][x] = c == 'o'
    return grid


def xml(grid, source, r=0.42):
    parts = []
    for y in range(24):
        for x in range(24):
            if grid[y][x]:
                cx, cy = x + 0.5, y + 0.5
                parts.append(f"M{cx - r:.2f},{cy:.1f}a{r},{r} 0 1,0 {2 * r:.2f},0a{r},{r} 0 1,0 -{2 * r:.2f},0")
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- fork: dot matrix icon generated by scripts/pixel_icons.py from {source} -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp"\n'
            '    android:viewportWidth="24" android:viewportHeight="24">\n'
            f'    <path android:fillColor="#FFFFFFFF" android:pathData="{"".join(parts)}" />\n'
            '</vector>\n')


def main():
    src, out = sys.argv[1], sys.argv[2]
    icons = {}
    for name, pix in MAP.items():
        svg = open(os.path.join(src, pix + '.svg')).read()
        icons[name] = (raster(svg), f'pixelarticons {pix} (MIT)')
    icons['shift_filled'] = (filled(icons['shift'][0]), 'pixelarticons arrow-big-up, filled (MIT)')
    for name, art in OWN.items():
        icons[name] = (own(art), 'own drawing')
    for name, (grid, source) in icons.items():
        open(os.path.join(out, f'ic_dot_{name}.xml'), 'w').write(xml(grid, source))
    print(len(icons), 'icons')
    if len(sys.argv) > 3:  # preview sheet
        from PIL import Image, ImageDraw
        names = sorted(icons)
        cell, cols = 24 * 5, 8
        img = Image.new('RGB', (cols * cell, ((len(names) + cols - 1) // cols) * (cell + 16)), 'black')
        d = ImageDraw.Draw(img)
        for i, name in enumerate(names):
            ox, oy = (i % cols) * cell, (i // cols) * (cell + 16)
            for y in range(24):
                for x in range(24):
                    if icons[name][0][y][x]:
                        cx, cy = ox + x * 4.5 + 6, oy + y * 4.5 + 6
                        d.ellipse([cx - 1.9, cy - 1.9, cx + 1.9, cy + 1.9], fill='white')
            d.text((ox + 4, oy + cell - 6), name, fill='gray')
        img.save(sys.argv[3])


if __name__ == '__main__':
    main()
