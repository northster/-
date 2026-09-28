# fork: dot matrix icons from pixelarticons (github.com/halfmage/pixelarticons, MIT License,
# Copyright (c) 2020 Gerrit Halfmann). Each pixel of the 24x24 icon becomes one round dot.
#
# usage: python3 scripts/pixel_icons.py <pixelarticons svg folder> app/src/main/res/drawable [preview.png]
# (the svg folder is in the npm package: npm pack pixelarticons, package/svg)
# Replaces the hand drawn icons of scripts/dot_icons.py with the same names.
#
# Every icon is a 12x12 grid of big round dots, the pitch and dot size of the Doto font at key label size, and all of
# them fill the same box: the longer side of each drawing is SIZE dots (a few thin ones smaller, see SMALL), centered.
# That way a row of them reads as a row of equal square buttons. Pixelarticons are resampled to that size by how much
# of each dot's area the icon covers, at the sub-dot offset where the fewest dots are half covered (crisp lines).
import os
import re
import sys

# our icon name -> pixelarticons name
MAP = {
    'enter': 'corner-down-left',
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
    'zap': 'zap',
    'more': 'more-horizontal',
    'question': 'circle-question',
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

SIZE = 10
# thin glyphs look too heavy at full size
SMALL = {'left': 8, 'right': 8, 'check': 8, 'close': 8, 'more': 8}

# drawn straight on the 12x12 grid ('o' = dot), at most SIZE x SIZE: simple key icons read better drawn for the grid
OWN = {
    'shift': """
....oo....
...o..o...
..o....o..
.o......o.
oooo..oooo
...o..o...
...o..o...
...o..o...
...o..o...
...oooo...
""",
    'shift_filled': """
....oo....
...oooo...
..oooooo..
.oooooooo.
oooooooooo
...oooo...
...oooo...
...oooo...
...oooo...
...oooo...
""",
    'shift_locked': """
....oo....
...oooo...
..oooooo..
.oooooooo.
oooooooooo
...oooo...
...oooo...
...oooo...
..........
...oooo...
""",
    'backspace': """
..oooooooo
.o.......o
o..o..o..o
o...oo...o
o...oo...o
o..o..o..o
.o.......o
..oooooooo
""",
    'globe': """
...oooo...
.oo.oo.oo.
.o.o..o.o.
o..o..o..o
oooooooooo
o..o..o..o
.o.o..o.o.
.oo.oo.oo.
...oooo...
""",
    'space': """
o........o
o........o
oooooooooo
""",
    'gif': """
.oo..o.ooo
o..o.o.o..
o....o.o..
o.oo.o.oo.
o..o.o.o..
o..o.o.o..
.oo..o.o..
""",
    # translate: 가 and A
    'translate': """
ooo.o.....
..o.o.....
..o.oo....
.o..o.....
o...o.....
.......o..
......o.o.
.....o...o
.....ooooo
.....o...o
""",
    # AI: a big and a small star
    'sparkles': """
........o.
.......ooo
....o...o.
....o.....
...ooo....
ooooooooo.
...ooo....
....o.....
....o.....
""",
    # settings: three dots in a column
    'settings': """
oo
oo
..
..
oo
oo
..
..
oo
oo
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
    """12x12 grid from an 'o' drawing, centered"""
    rows = art.strip('\n').split('\n')
    top = (12 - len(rows)) // 2
    left = (12 - max(len(r) for r in rows)) // 2
    grid = [[False] * 12 for _ in range(12)]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            grid[top + y][left + x] = c == 'o'
    return grid


def center(grid):
    """the dots moved so there is as much room left as right and above as below (icons line up in a row)"""
    cells = [(x, y) for y in range(12) for x in range(12) if grid[y][x]]
    if not cells:
        return grid
    left, right = min(x for x, _ in cells), max(x for x, _ in cells)
    top, bottom = min(y for _, y in cells), max(y for _, y in cells)
    dx = (11 - right - left) // 2
    dy = (11 - bottom - top) // 2
    out = [[False] * 12 for _ in range(12)]
    for x, y in cells:
        out[y + dy][x + dx] = True
    return out


def fit(grid, size):
    """24x24 pixels -> 12x12 dots, the drawing's longer side scaled to [size] dots and centered"""
    cells = [(x, y) for y in range(24) for x in range(24) if grid[y][x]]
    left, right = min(x for x, _ in cells), max(x for x, _ in cells) + 1
    top, bottom = min(y for _, y in cells), max(y for _, y in cells) + 1
    scale = max(right - left, bottom - top) / size  # pixels per dot
    cx, cy = (left + right) / 2, (top + bottom) / 2

    def covered(px, py):
        x, y = int(px // 1), int(py // 1)
        return 0 <= x < 24 and 0 <= y < 24 and grid[y][x]

    best, best_blur = None, None
    for oy in (-0.25, 0, 0.25):
        for ox in (-0.25, 0, 0.25):
            dots = [[0.0] * 12 for _ in range(12)]
            for gy in range(12):
                for gx in range(12):
                    # the dot's area in pixels, 4x4 samples
                    hits = 0
                    for sy in range(4):
                        for sx in range(4):
                            px = cx + (gx - 6 + ox + (sx + 0.5) / 4) * scale
                            py = cy + (gy - 6 + oy + (sy + 0.5) / 4) * scale
                            hits += covered(px, py)
                    dots[gy][gx] = hits / 16
            blur = sum(min(c, 1 - c) for row in dots for c in row)
            if best_blur is None or blur < best_blur:
                best, best_blur = dots, blur
    return [[c >= 0.45 for c in row] for row in best]


def xml(grid, source, r=0.86):
    parts = []
    for y in range(12):
        for x in range(12):
            if grid[y][x]:
                cx, cy = 2 * x + 1, 2 * y + 1
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
    # pinned clips: the bookmark filled in
    icons['pin_filled'] = (filled(icons['pin'][0]), 'pixelarticons bookmark, filled (MIT)')
    icons = {name: (fit(grid, SMALL.get(name, SIZE)), source) for name, (grid, source) in icons.items()}
    for name, art in OWN.items():
        icons[name] = (own(art), 'own drawing')
    icons = {name: (center(grid), source) for name, (grid, source) in icons.items()}
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
            for y in range(12):
                for x in range(12):
                    if icons[name][0][y][x]:
                        cx, cy = ox + x * 9 + 8, oy + y * 9 + 8
                        d.ellipse([cx - 3.9, cy - 3.9, cx + 3.9, cy + 3.9], fill='white')
            d.text((ox + 4, oy + cell - 6), name, fill='gray')
        img.save(sys.argv[3])


if __name__ == '__main__':
    main()
