# fork: dot matrix icons, 12x12 grid, each 'o' is one round dot (24x24 viewport, 2 units per cell)
# Most of these are now replaced by scripts/pixel_icons.py (run it after this one).
ICONS = {
'shift': """
.....oo.....
....o..o....
...o....o...
..o......o..
.ooo....ooo.
...o....o...
...o....o...
...o....o...
...oooooo...
""",
'shift_filled': """
.....oo.....
....oooo....
...oooooo...
..oooooooo..
.oooooooooo.
...oooooo...
...oooooo...
...oooooo...
...oooooo...
""",
'shift_locked': """
.....oo.....
....oooo....
...oooooo...
..oooooooo..
.oooooooooo.
...oooooo...
...oooooo...
...oooooo...
............
...oooooo...
""",
'backspace': """
...ooooooooo
..o........o
.o..o...o..o
o....o.o...o
o.....o....o
o....o.o...o
.o..o...o..o
..o........o
...ooooooooo
""",
'enter': """
.........o..
.........o..
.........o..
...o.....o..
..o......o..
.oooooooo...
..o.........
...o........
""",
'space': """
.o........o.
.o........o.
.oooooooooo.
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
'smile': """
...oooo...
.oo....oo.
.o......o.
o..o..o..o
o........o
o.o....o.o
o..oooo..o
.o......o.
.oo....oo.
...oooo...
""",
'search': """
..oooo....
.o....o...
o......o..
o......o..
o......o..
o......o..
.o....o...
..oooo.o..
........o.
.........o
""",
'clipboard': """
...oooo...
.oo....oo.
.o.oooo.o.
.o......o.
.o......o.
.o......o.
.o......o.
.o......o.
.o......o.
.oooooooo.
""",
'paste': """
...oooo...
.oo....oo.
.o.oooo.o.
.o......o.
.o.oooo.o.
.o......o.
.o.oooo.o.
.o......o.
.o.oo...o.
.oooooooo.
""",
'copy': """
oooooo....
o....o....
o....o....
o..ooooooo
o..o.....o
oooo.....o
...o.....o
...o.....o
...o.....o
...ooooooo
""",
'cut': """
o.......o
.o.....o.
..o...o..
...o.o...
....o....
...o.o...
.oo...oo.
o..o.o..o
o..o.o..o
.oo...oo.
""",
'select_all': """
o.o.o.o.o
.........
o.......o
.........
o.......o
.........
o.......o
.........
o.o.o.o.o
""",
'select_word': """
.ooo.ooo.
....o....
....o....
....o....
....o....
....o....
....o....
....o....
.ooo.ooo.
""",
'undo': """
...o......
..o.......
.oooooooo.
..o......o
...o.....o
.........o
.........o
..ooooooo.
""",
'redo': """
......o...
.......o..
.oooooooo.
o......o..
o.....o...
o.........
o.........
.ooooooo..
""",
'left': """
...o......
..o.......
.o........
oooooooooo
.o........
..o.......
...o......
""",
'right': """
......o...
.......o..
........o.
oooooooooo
........o.
.......o..
......o...
""",
'check': """
.........o
........o.
.......o..
o.....o...
.o...o....
..o.o.....
...o......
""",
'trash': """
...oooo...
oooooooooo
.o......o.
.o.o..o.o.
.o.o..o.o.
.o.o..o.o.
.o.o..o.o.
.o......o.
..oooooo..
""",
'pin': """
..oooooo..
...o..o...
...o..o...
...o..o...
..o....o..
.oooooooo.
....oo....
....oo....
....oo....
....o.....
""",
'close': """
o......o
.o....o.
..o..o..
...oo...
...oo...
..o..o..
.o....o.
o......o
""",
'keyboard': """
ooooooooooo
o.........o
o.o.o.o.o.o
o.........o
o.o.o.o.o.o
o.........o
o..ooooo..o
o.........o
ooooooooooo
""",
'sparkles': """
....o.....
....o.....
...ooo....
.ooooooo..
...ooo....
....o...o.
....o..ooo
........o.
""",
'translate': """
.oo.........
o..o........
oooo........
o..o........
o..o........
............
......ooo.o.
........o.o.
........o.oo
........o.o.
.......o..o.
......o...o.
""",
'more': """
oo...oo...oo
oo...oo...oo
""",
'emoji_recents': """
...oooo...
.oo....oo.
.o...o..o.
o....o...o
o....o...o
o....ooo.o
o........o
.o......o.
.oo....oo.
...oooo...
""",
'emoji_people': """
....oo....
...oooo...
...oooo...
....oo....
..oooooo..
.o.oooo.o.
o..oooo..o
...o..o...
...o..o...
...o..o...
""",
'emoji_nature': """
......oooo
....oo...o
...o.....o
..o.....o.
..o....o..
.o....o...
.o...o....
.o..o.....
..oo......
.o........
""",
'emoji_food': """
..o..o....
...o..o...
..o..o....
..........
oooooooo..
o......ooo
o......o.o
o......ooo
.o....o...
..oooo....
""",
'emoji_travel': """
..oooooo..
.o......o.
o........o
oooooooooo
o.oo..oo.o
o.oo..oo.o
oooooooooo
.oo....oo.
""",
'emoji_activities': """
...oooo...
.oo.o..oo.
.o..o...o.
o...o....o
oooooooooo
o....o...o
.o...o..o.
.oo..o.oo.
...oooo...
""",
'emoji_objects': """
...oooo...
..o....o..
.o......o.
.o......o.
.o......o.
..o....o..
...o..o...
...oooo...
...oooo...
....oo....
""",
'emoji_symbols': """
..o..o..
..o..o..
oooooooo
..o..o..
..o..o..
oooooooo
..o..o..
..o..o..
""",
'emoji_flags': """
ooooooooo
o.......o
o.......o
o.......o
ooooooooo
o........
o........
o........
o........
""",
'emoji_emoticons': """
......o...
..o....o..
........o.
........o.
........o.
..o.....o.
.......o..
......o...
""",
'question': """
...oooo...
..o....o..
.......o..
......o...
.....o....
.....o....
..........
.....o....
""",
'settings': """
.....oo.....
..o..oo..o..
...oooooo...
..oo....oo..
oooo....oooo
oooo....oooo
..oo....oo..
...oooooo...
..o..oo..o..
.....oo.....
""",
'gif': """
.ooo..o.oooo
o.....o.o...
o.oo..o.ooo.
o..o..o.o...
.ooo..o.o...
""",
}

def xml(name, art, r=0.8):
    rows = [l for l in art.strip('\n').split('\n')]
    h, w = len(rows), max(len(l) for l in rows)
    ox, oy = (12 - w) / 2, (12 - h) / 2  # center on the 12x12 grid
    parts = []
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == 'o':
                cx, cy = (ox + x) * 2 + 1, (oy + y) * 2 + 1
                parts.append(f"M{cx - r:.1f},{cy:.1f}a{r},{r} 0 1,0 {2*r:.1f},0a{r},{r} 0 1,0 -{2*r:.1f},0")
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- fork: dot matrix icon, generated by scripts/dot_icons.py -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp"\n'
            '    android:viewportWidth="24" android:viewportHeight="24">\n'
            f'    <path android:fillColor="#FFFFFFFF" android:pathData="{"".join(parts)}" />\n'
            '</vector>\n')

if __name__ == '__main__':
    import os, sys
    out = sys.argv[1]
    for name, art in ICONS.items():
        rows = art.strip('\n').split('\n')
        assert all(len(r) <= 12 for r in rows) and len(rows) <= 12, name
        open(os.path.join(out, f'ic_dot_{name}.xml'), 'w').write(xml(name, art))
    print(len(ICONS), 'icons')
