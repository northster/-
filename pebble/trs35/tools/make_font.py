#!/usr/bin/env python3
"""Build TRS Grid, the face's display font, from stroke skeletons.

A monospace, 45-degree-chamfered face in the spirit of receipt / grid type:
every glyph is a set of polylines on a small grid, thickened with mitred
joins (so corners come out as clean 45 degree chamfers), unioned and
written as a TrueType font. Only the characters the face draws are built.

  pip install fonttools shapely
  python3 tools/make_font.py            # -> resources/fonts/TRSGrid.ttf
  python3 tools/make_font.py --narrow   # -> resources/fonts/TRSGrid-Narrow.ttf

The output is original work under the repository's license.
"""
import os

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from shapely.geometry import LineString, Polygon, MultiPolygon
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

import argparse

ap = argparse.ArgumentParser()
ap.add_argument('out', nargs='?')
ap.add_argument('--narrow', action='store_true', help='condensed cut (for the step count)')
ARGS = ap.parse_args()

UPM = 1000
H = 700          # digit / cap height
if ARGS.narrow:
    W, S, K, GAP = 340, 100, 46, 90
else:
    W = 520      # glyph box width
    S = 134      # stroke (about 0.19 of the height, like the mock-up)
    K = 70       # skeleton chamfer; outer corners end up ~K+S/4, counters ~K-S/4
    GAP = 112    # space between glyph boxes
ADV = W + GAP

X0, X1 = S / 2, W - S / 2
Y0, Y1 = S / 2, H - S / 2
YM = H / 2
XC = W / 2


def oct_path(x0, y0, x1, y1, k=K):
    """Closed octagon skeleton (rectangle with 45 degree corners)."""
    return [(x0 + k, y0), (x1 - k, y0), (x1, y0 + k), (x1, y1 - k),
            (x1 - k, y1), (x0 + k, y1), (x0, y1 - k), (x0, y0 + k), (x0 + k, y0)]


def stroke(path, closed=False):
    line = LineString(path)
    if closed:
        return Polygon(line.buffer(S / 2, join_style=2, mitre_limit=3).exterior)\
            .difference(Polygon(path).buffer(-S / 2, join_style=2, mitre_limit=3))
    return line.buffer(S / 2, cap_style=3, join_style=2, mitre_limit=3)


def ring(x0, y0, x1, y1, k=K):
    outer = Polygon(oct_path(x0, y0, x1, y1, k)).buffer(S / 2, join_style=2, mitre_limit=3)
    inner = Polygon(oct_path(x0, y0, x1, y1, k)).buffer(-S / 2, join_style=2, mitre_limit=3)
    return outer.difference(inner)


def dot(cx, cy, d=S * 1.1):
    r = d / 2
    k = d * 0.3
    return Polygon(oct_path(cx - r, cy - r, cx + r, cy + r, k))


def box():
    return Polygon([(0, 0), (W, 0), (W, H), (0, H)])


def clip(geom):
    """Square caps may poke out of the glyph box; trim them."""
    return geom.intersection(box())


def paths(*ps):
    return clip(unary_union([stroke(p) for p in ps]))


G = {}

# ---- digits ----------------------------------------------------------------
G['0'] = ring(X0, Y0, X1, Y1)
XS = X1 - S * 0.35
G['1'] = clip(unary_union([stroke([(XS, Y0), (XS, Y1)]),
                           stroke([(XS, Y1), (XS - W * 0.27, Y1 - W * 0.27)])]))
G['2'] = paths([(X0, Y1 - K), (X0 + K, Y1), (X1 - K, Y1), (X1, Y1 - K), (X1, YM + K), (X1 - K, YM),
                (X0 + K, YM), (X0, YM - K), (X0, Y0), (X1, Y0)])
G['3'] = paths([(X0, Y1), (X1 - K, Y1), (X1, Y1 - K), (X1, Y0 + K), (X1 - K, Y0), (X0, Y0)],
               [(X0 + S, YM), (X1, YM)])
G['4'] = paths([(X0, Y1), (X0, YM), (X1, YM)], [(X1 - S * 0.4, Y1), (X1 - S * 0.4, Y0)])
G['5'] = paths([(X1, Y1), (X0, Y1), (X0, YM), (X1 - K, YM), (X1, YM - K), (X1, Y0 + K),
                (X1 - K, Y0), (X0, Y0)])
G['6'] = paths([(X1, Y1), (X0 + K, Y1), (X0, Y1 - K), (X0, Y0 + K), (X0 + K, Y0), (X1 - K, Y0),
                (X1, Y0 + K), (X1, YM - K), (X1 - K, YM), (X0, YM)])
G['7'] = paths([(X0, Y1), (X1, Y1), (X1, Y1 - K), (X0 + W * 0.18, Y0)])
d = S * 0.22
G['8'] = unary_union([ring(X0 + d, YM, X1 - d, Y1), ring(X0, Y0, X1, YM)])
G['9'] = paths([(X0, Y0), (X1 - K, Y0), (X1, Y0 + K), (X1, Y1 - K), (X1 - K, Y1), (X0 + K, Y1),
                (X0, Y1 - K), (X0, YM + K), (X0 + K, YM), (X1, YM)])

# ---- punctuation (narrow) --------------------------------------------------
# dots sit at the left of a narrow advance so the space on both sides of a
# colon or period is the same GAP as between digits
DOTW = S * 1.1
NARROW = {':': DOTW + GAP, '.': DOTW + GAP, ' ': W * 0.5}
# proportional exceptions: the 1 only takes the room it needs (as in the mock-up)
TIGHT = '1'

G[':'] = unary_union([dot(DOTW / 2, DOTW / 2, DOTW), dot(DOTW / 2, H * 0.47, DOTW)])
G['.'] = dot(DOTW / 2, DOTW / 2, DOTW)
G['-'] = paths([(X0 + S * 0.3, YM), (X1 - S * 0.3, YM)])
G['/'] = paths([(X0, Y0), (X1, Y1)])
G['+'] = paths([(X0, YM), (X1, YM)], [(XC, YM - (X1 - X0) / 2), (XC, YM + (X1 - X0) / 2)])
G['%'] = unary_union([ring(X0 - S * 0.2, H * 0.62, X0 + S * 1.0, Y1, K * 0.4),
                      ring(X1 - S * 1.0, Y0, X1 + S * 0.2, H * 0.38, K * 0.4),
                      paths([(X0, Y0), (X1, Y1)])])
# degree: a small chamfered ring at the top left, on a narrow advance
DEG = S * 2.1
G['°'] = ring(S / 2, H - DEG + S / 2, DEG - S / 2, Y1, K * 0.5)
NARROW['°'] = DEG + GAP * 0.6

# ---- letters used by the face (weekdays, AM/PM, units) ---------------------
G['O'] = G['0']
G['S'] = paths([(X1, Y1), (X0 + K, Y1), (X0, Y1 - K), (X0, YM + K), (X0 + K, YM), (X1 - K, YM),
                (X1, YM - K), (X1, Y0 + K), (X1 - K, Y0), (X0, Y0)])
G['U'] = paths([(X0, Y1), (X0, Y0 + K), (X0 + K, Y0), (X1 - K, Y0), (X1, Y0 + K), (X1, Y1)])
G['N'] = paths([(X0, Y0), (X0, Y1), (X1, Y0), (X1, Y1)])
G['M'] = paths([(X0, Y0), (X0, Y1), (XC, YM), (X1, Y1), (X1, Y0)])
G['W'] = paths([(X0, Y1), (X0, Y0), (XC, YM), (X1, Y0), (X1, Y1)])
G['T'] = paths([(X0, Y1), (X1, Y1)], [(XC, Y1), (XC, Y0)])
G['E'] = paths([(X1, Y1), (X0, Y1), (X0, Y0), (X1, Y0)], [(X0, YM), (X1 - S * 0.4, YM)])
G['F'] = paths([(X1, Y1), (X0, Y1), (X0, Y0)], [(X0, YM), (X1 - S * 0.4, YM)])
G['D'] = paths([(X0, Y0), (X0, Y1), (X1 - K * 1.5, Y1), (X1, Y1 - K * 1.5), (X1, Y0 + K * 1.5),
                (X1 - K * 1.5, Y0), (X0, Y0)])
G['H'] = paths([(X0, Y0), (X0, Y1)], [(X1, Y0), (X1, Y1)], [(X0, YM), (X1, YM)])
G['R'] = paths([(X0, Y0), (X0, Y1), (X1 - K, Y1), (X1, Y1 - K), (X1, YM + K), (X1 - K, YM), (X0, YM)],
               [(XC, YM), (X1, Y0)])
G['I'] = paths([(X0 + S * 0.4, Y1), (X1 - S * 0.4, Y1)], [(XC, Y1), (XC, Y0)],
               [(X0 + S * 0.4, Y0), (X1 - S * 0.4, Y0)])
G['A'] = paths([(X0, Y0), (X0, Y1 - K), (X0 + K, Y1), (X1 - K, Y1), (X1, Y1 - K), (X1, Y0)],
               [(X0, YM), (X1, YM)])
G['P'] = paths([(X0, Y0), (X0, Y1), (X1 - K, Y1), (X1, Y1 - K), (X1, YM + K), (X1 - K, YM), (X0, YM)])
G['C'] = paths([(X1, Y1), (X0 + K, Y1), (X0, Y1 - K), (X0, Y0 + K), (X0 + K, Y0), (X1, Y0)])
G['K'] = paths([(X0, Y0), (X0, Y1)], [(X1, Y1), (X0 + S * 0.6, YM), (X1, Y0)])

NAMES = {':': 'colon', '.': 'period', '-': 'hyphen', '/': 'slash', '+': 'plus', '%': 'percent',
         '°': 'degree', ' ': 'space'}
for c in '0123456789':
    NAMES[c] = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine'][int(c)]


def name(c):
    return NAMES.get(c, c)


def draw(geom, pen):
    polys = list(geom.geoms) if isinstance(geom, MultiPolygon) else [geom]
    for p in polys:
        if p.is_empty:
            continue
        p = orient(p, sign=-1.0)  # TrueType: outer contours clockwise
        for ring_ in [p.exterior] + list(p.interiors):
            pts = [(round(x), round(y)) for x, y in list(ring_.coords)[:-1]]
            pen.moveTo(pts[0])
            for pt in pts[1:]:
                pen.lineTo(pt)
            pen.closePath()


def build(out):
    chars = sorted(set(G) | {' '})
    order = ['.notdef'] + [name(c) for c in chars]
    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap({ord(c): name(c) for c in chars})
    glyphs, metrics = {}, {}
    pen = TTGlyphPen(None)
    glyphs['.notdef'] = pen.glyph()
    metrics['.notdef'] = (ADV, 0)
    from shapely import affinity
    for c in chars:
        pen = TTGlyphPen(None)
        adv = int(NARROW.get(c, ADV))
        if c in G:
            g = G[c]
            if c in TIGHT:
                minx, _, maxx, _ = g.bounds
                g = affinity.translate(g, -minx)
                adv = int(maxx - minx + GAP)
            draw(g, pen)
        glyphs[name(c)] = pen.glyph()
        metrics[name(c)] = (adv, 0)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=800, descent=-200)
    fb.setupNameTable({'familyName': 'TRS Grid', 'styleName': 'Narrow' if ARGS.narrow else 'Regular'})
    fb.setupOS2(sTypoAscender=800, sTypoDescender=-200, usWinAscent=800, usWinDescent=200)
    fb.setupPost()
    fb.save(out)
    print('wrote', out, len(chars), 'glyphs')


if __name__ == '__main__':
    here = os.path.dirname(os.path.abspath(__file__))
    default = 'TRSGrid-Narrow.ttf' if ARGS.narrow else 'TRSGrid.ttf'
    build(ARGS.out or os.path.join(here, '..', 'resources', 'fonts', default))
