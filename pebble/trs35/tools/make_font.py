#!/usr/bin/env python3
"""Build the TRS Grid pixel fonts, one TrueType file per size the face uses.

The face's numbers are pixel art: at each size the glyphs are drawn on the
pixel grid (stroke, 45 degree chamfers and counters in whole pixels) and
written as TrueType outlines made of pixel squares, so Pebble's 1-bit font
converter reproduces them exactly, with no stray pixels. Glyphs that appear
in the design mock-up are taken from it pixel for pixel (mockup_glyphs.py);
the rest are generated here with the same rules.

  pip install fonttools shapely
  python3 tools/make_font.py            # -> resources/fonts/TRSGrid-*.ttf
  python3 tools/make_font.py --preview out.png

The output is original work under the repository's license.
"""
import argparse
import os

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from shapely.geometry import MultiPolygon, box as sbox
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

from mockup_glyphs import MOCKUP

# em: the pixel size the face loads the font at (package.json FONT_*_<em>).
# H: digit height, W: glyph width, S: stroke, CO / CI: outer / counter
# chamfer, GAP: space after each glyph. All in pixels.
CUTS = {
    # the mock-up draws 28px digits with 6px gaps; 26 / 5 keeps any time
    # (four wide digits, "03:47") inside the 132px between the margins
    'Time':  dict(em=53, H=37, W=26, S=7, CO=5, CI=1, GAP=5),
    'Date':  dict(em=21, H=15, W=11, S=3, CO=2, CI=0, GAP=2),
    'Temp':  dict(em=24, H=17, W=12, S=3, CO=2, CI=0, GAP=3),
    'Steps': dict(em=30, H=21, W=10, S=3, CO=2, CI=0, GAP=3),
}
CHARS = {
    'Time': '0123456789:',
    'Date': '0123456789. ADEFHIMNORSTUW',
    'Temp': '0123456789-',
    'Steps': '0123456789',
}
UNIT = 16  # font units per pixel


class G:
    """A glyph as a set of (x, y) pixels, y = 0 at the top."""

    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, set()

    def oct(self, x0, y0, x1, y1, tl=0, tr=0, bl=0, br=0, on=True):
        """Filled rectangle (inclusive) with 45 degree corner chamfers."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                dl, dr, dt, db = x - x0, x1 - x, y - y0, y1 - y
                if dl + dt < tl or dr + dt < tr or dl + db < bl or dr + db < br:
                    continue
                (self.px.add if on else self.px.discard)((x, y))
        return self

    def rect(self, x0, y0, x1, y1, on=True):
        return self.oct(x0, y0, x1, y1, on=on)

    def cut(self, *a, **k):
        return self.oct(*a, on=False, **k)

    def band(self, pts, s):
        """Stroke s px wide (measured across rows) along a polyline."""
        for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
            for y in range(min(ya, yb), max(ya, yb) + 1):
                t = 0 if yb == ya else (y - ya) / (yb - ya)
                xc = xa + (xb - xa) * t
                x0 = int(round(xc - (s - 1) / 2))
                for x in range(x0, x0 + s):
                    if 0 <= x < self.w:
                        self.px.add((x, y))
        return self

    def rows(self):
        return [''.join('#' if (x, y) in self.px else '.' for x in range(self.w)) for y in range(self.h)]


def from_rows(rows):
    g = G(len(rows[0]), len(rows))
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c == '#':
                g.px.add((x, y))
    return g


def make(ch, p):
    H, W, S, CO, CI = p['H'], p['W'], p['S'], p['CO'], p['CI']
    R, B = W - 1, H - 1
    m0 = (H - S) // 2           # first row of the middle bar
    m1 = m0 + S - 1             # last row of the middle bar
    g = G(W, H)

    def ring(y0, y1, tl=CO, tr=CO, bl=CO, br=CO):
        g.oct(0, y0, R, y1, tl, tr, bl, br).cut(S, y0 + S, R - S, y1 - S, CI, CI, CI, CI)

    def slant_bar():
        # middle bar of 2: a parallelogram leaning one pixel per row
        for k in range(S):
            g.rect(S - 1 - k, m0 + k, R - k, m0 + k)

    if ch in '0O':
        ring(0, B)
    elif ch == '1':
        w = 2 * S
        g = G(w, H)
        sl = w - S  # stem left
        for y in range(H):
            for x in range(w):
                stem = x >= sl and x + y >= sl
                flag = x < sl and x + y >= sl and y - x <= sl + 1 and x + y <= 2 * sl + 1
                if stem or flag:
                    g.px.add((x, y))
    elif ch == '2':
        g.oct(0, 0, R, S - 1, CO, CO, 0, 0)
        g.rect(R - S + 1, S, R, m0 - 1)
        slant_bar()
        g.rect(0, m1 + 1, S - 1, B - S)
        g.rect(0, B - S + 1, R, B)
    elif ch == '3':
        g.oct(0, 0, R, S - 1, 0, CO, 0, 0)
        g.rect(R - S + 1, S, R, B - S)
        g.rect(W // 3, m0, R, m1)
        g.oct(0, B - S + 1, R, B, 0, 0, 0, CO)
    elif ch == '4':
        g.rect(0, 0, S - 1, m1)
        g.rect(0, m0, R - 1, m1)
        g.rect(R - S, 0, R - 1, B)
    elif ch == '5':
        g.rect(0, 0, R, S - 1)
        g.rect(0, S, S - 1, m0 - 1)
        g.oct(0, m0, R, m1, 0, CO, 0, 0)
        g.rect(R - S + 1, m1 + 1, R, B - S)
        g.oct(0, B - S + 1, R, B, 0, 0, 0, CO)
    elif ch == '6':
        g.oct(0, 0, R, S - 1, CO, 0, 0, 0)
        g.rect(0, S, S - 1, B)
        g.oct(0, m0, R, B, 0, CO, CO, CO).cut(S, m1 + 1, R - S, B - S, CI, CI, CI, CI)
    elif ch == '9':
        g.oct(0, 0, R, m1, CO, CO, 0, CO).cut(S, S, R - S, m0 - 1, CI, CI, CI, CI)
        g.rect(R - S + 1, 0, R, B)
        g.oct(0, B - S + 1, R, B, 0, 0, 0, CO)
    elif ch == '7':
        g.rect(0, 0, R, S - 1)
        g.band([(R - S // 2 - 1, S), (S // 2 + 2, B)], S)
    elif ch == '8':
        i = max(1, S // 3)
        g.oct(i, 0, R - i, m0 + 1, CO, CO, 0, 0).cut(S + i, S, R - S - i, m0 - 1, CI, CI, CI, CI)
        g.rect(i + 1, m0 + 2, R - i - 1, m0 + 2)
        g.oct(0, m0 + 3, R, B, i, i, CO, CO).cut(S, m1 + 1, R - S, B - S, CI, CI, CI, CI)
    elif ch == ':':
        d = S + 1
        g = G(d, H)
        c = max(1, d // 4)
        g.oct(0, H // 2 - d + 2, d - 1, H // 2 + 1, c, c, c, c)
        g.oct(0, B - d + 1, d - 1, B, c, c, c, c)
    elif ch == '.':
        g = G(S, H)
        g.rect(0, B - S + 1, S - 1, B)
    elif ch == '-':
        g.rect(0, m0, R - 2, m1)
    elif ch == ' ':
        g = G(W // 2, H)
    elif ch == 'A':
        g.oct(0, 0, R, S - 1, CO, CO, 0, 0)
        g.rect(0, S, S - 1, B).rect(R - S + 1, S, R, B).rect(0, m0, R, m1)
    elif ch == 'E':
        g.rect(0, 0, R, S - 1).rect(0, S, S - 1, B).rect(0, m0, R - 1, m1).rect(0, B - S + 1, R, B)
    elif ch == 'F':
        g.rect(0, 0, R, S - 1).rect(0, S, S - 1, B).rect(0, m0, R - 1, m1)
    elif ch == 'H':
        g.rect(0, 0, S - 1, B).rect(R - S + 1, 0, R, B).rect(0, m0, R, m1)
    elif ch == 'I':
        c = (W - S) // 2
        g.rect(1, 0, R - 1, S - 1).rect(c, S, c + S - 1, B - S).rect(1, B - S + 1, R - 1, B)
    elif ch == 'M':
        g.rect(0, 0, S - 1, B).rect(R - S + 1, 0, R, B)
        g.band([(S, S - 1), (W // 2, m1 - 1)], S - 1).band([(R - S, S - 1), (R - W // 2, m1 - 1)], S - 1)
    elif ch == 'N':
        g.rect(0, 0, S - 1, B).rect(R - S + 1, 0, R, B)
        g.band([(S, 1), (R - S, B - 1)], S)
    elif ch == 'R':
        g.oct(0, 0, R, m1, 0, CO, 0, CO).cut(S, S, R - S, m0 - 1, 0, CI, 0, CI)
        g.rect(0, m1 + 1, S - 1, B).rect(R - S + 1, m1 + 1, R, B)
    elif ch == 'S':
        g.oct(0, 0, R, S - 1, CO, 0, 0, 0)
        g.rect(0, S, S - 1, m0 - 1)
        g.oct(0, m0, R, m1, 0, CO, 0, 0)
        g.rect(R - S + 1, m1 + 1, R, B - S)
        g.oct(0, B - S + 1, R, B, 0, 0, 0, CO)
    elif ch == 'T':
        c = (W - S) // 2
        g.rect(0, 0, R, S - 1).rect(c, S, c + S - 1, B)
    elif ch == 'U':
        g.rect(0, 0, S - 1, B - S).rect(R - S + 1, 0, R, B - S)
        g.oct(0, B - S + 1, R, B, 0, 0, CO, CO)
    else:
        raise KeyError(ch)
    return g


def glyph_set(cut):
    p = CUTS[cut]
    mock = MOCKUP.get(cut.lower(), {})
    out = {}
    for ch in CHARS[cut]:
        if ch in mock:
            rows = mock[ch]
            # narrower than the mock-up: drop columns from the middle, which
            # keeps the strokes and chamfers and only tightens the counter
            extra = len(rows[0]) - p['W']
            if extra > 0 and len(rows[0]) >= p['W'] + 2 and ch not in '1:':
                mid = len(rows[0]) // 2 - extra // 2
                rows = [r[:mid] + r[mid + extra:] for r in rows]
            out[ch] = from_rows(rows)
        else:
            out[ch] = make(ch, p)
    if cut == 'Date':
        # the mock-up gives the 1 a pixel of air on both sides
        one = out['1']
        g = G(one.w + 1, one.h)
        g.px = {(x + 1, y) for x, y in one.px}
        out['1'] = g
    return out


def outline(g):
    """Pixel squares -> outline, y up, baseline under the last row."""
    squares = [sbox(x * UNIT, (g.h - 1 - y) * UNIT, (x + 1) * UNIT, (g.h - y) * UNIT) for x, y in g.px]
    return unary_union(squares) if squares else None


def draw(geom, pen):
    polys = list(geom.geoms) if isinstance(geom, MultiPolygon) else [geom]
    for p in polys:
        p = orient(p.simplify(0), sign=-1.0)  # TrueType: outer contours clockwise
        for ring_ in [p.exterior] + list(p.interiors):
            pts = [(int(round(x)), int(round(y))) for x, y in list(ring_.coords)[:-1]]
            pen.moveTo(pts[0])
            for pt in pts[1:]:
                pen.lineTo(pt)
            pen.closePath()


def name(c):
    return {':': 'colon', '.': 'period', '-': 'hyphen', ' ': 'space'}.get(c, 'uni%04X' % ord(c))


def build(cut, path):
    p = CUTS[cut]
    glyphs = glyph_set(cut)
    upm = p['em'] * UNIT
    chars = sorted(glyphs)
    fb = FontBuilder(upm, isTTF=True)
    fb.setupGlyphOrder(['.notdef'] + [name(c) for c in chars])
    fb.setupCharacterMap({ord(c): name(c) for c in chars})
    out, metrics = {'.notdef': TTGlyphPen(None).glyph()}, {'.notdef': (p['W'] * UNIT, 0)}
    for c in chars:
        g = glyphs[c]
        pen = TTGlyphPen(None)
        geom = outline(g)
        if geom is not None:
            draw(geom, pen)
        out[name(c)] = pen.glyph()
        extra = 1 if c == '1' and cut in ('Time', 'Date') else 0  # the mock-up's 1 breathes a little
        lsb = min(x for x, _ in g.px) * UNIT if g.px else 0  # must match the outline's xMin
        metrics[name(c)] = ((g.w + p['GAP'] + extra) * UNIT, lsb)
    fb.setupGlyf(out)
    fb.setupHorizontalMetrics(metrics)
    asc = int(upm * 0.8)
    fb.setupHorizontalHeader(ascent=asc, descent=asc - upm)
    fb.setupNameTable({'familyName': 'TRS Grid ' + cut, 'styleName': 'Regular'})
    fb.setupOS2(sTypoAscender=asc, sTypoDescender=asc - upm, usWinAscent=asc, usWinDescent=upm - asc)
    fb.setupPost()
    fb.save(path)
    return glyphs


def check(cut, path, glyphs):
    """Render with FreeType the way Pebble's converter does; must match."""
    try:
        import freetype
    except ImportError:
        return
    face = freetype.Face(path)
    face.set_pixel_sizes(0, CUTS[cut]['em'])
    flags = freetype.FT_LOAD_RENDER | freetype.FT_LOAD_MONOCHROME | freetype.FT_LOAD_TARGET_MONO
    for c, g in glyphs.items():
        face.load_char(c, flags)
        b = face.glyph.bitmap
        got = set()
        for r in range(b.rows):
            for col in range(b.width):
                if b.buffer[r * b.pitch + col // 8] & (0x80 >> (col % 8)):
                    got.add((col + face.glyph.bitmap_left, r + g.h - face.glyph.bitmap_top))
        if got != g.px:
            raise SystemExit('%s %r renders differently from its design' % (cut, c))


def preview(path, sets):
    from PIL import Image
    scale, pad = 3, 4
    lines = []
    for cut, glyphs in sets:
        gap = CUTS[cut]['GAP']
        w = sum(g.w + gap for g in glyphs.values()) + pad
        h = max(g.h for g in glyphs.values())
        img = Image.new('L', (w, h + pad), 255)
        x = pad // 2
        for c in sorted(glyphs):
            g = glyphs[c]
            for px, py in g.px:
                img.putpixel((x + px, pad // 2 + py), 0)
            x += g.w + gap
        lines.append(img)
    out = Image.new('L', (max(i.width for i in lines), sum(i.height for i in lines)), 255)
    y = 0
    for i in lines:
        out.paste(i, (0, y))
        y += i.height
    out.resize((out.width * scale, out.height * scale), Image.NEAREST).save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out-dir', default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                      '..', 'resources', 'fonts'))
    ap.add_argument('--preview')
    args = ap.parse_args()
    sets = []
    for cut in CUTS:
        path = os.path.join(args.out_dir, 'TRSGrid-%s.ttf' % cut)
        glyphs = build(cut, path)
        check(cut, path, glyphs)
        sets.append((cut, glyphs))
        print('wrote', os.path.normpath(path), len(glyphs), 'glyphs')
    if args.preview:
        preview(args.preview, sets)


if __name__ == '__main__':
    main()
