#pragma once
#include <pebble.h>

// Hand-drawn marks and the weather glyphs: plain rectangles and lines, no
// bitmaps, drawn with antialiasing off so every edge stays on the pixel grid.

// Corner mark at (x, y); (dx, dy) = (+-1, +-1) says which way the frame runs
// (+1, +1 for the top-left corner, -1, -1 for the bottom-right one).
void shapes_corner(GContext *ctx, int x, int y, int dx, int dy, GColor color);

// Scale with a tick every 1/12 (taller every 1/4) and a marker at `pos`
// (0..1000) along it. The baseline is the bottom row of `box`.
void shapes_ruler(GContext *ctx, GRect box, int pos, GColor ticks, GColor marker);

// Checkerboard of `cell` px squares; odd `phase` inverts it.
void shapes_checker(GContext *ctx, GRect box, int cell, int phase, GColor color);

// Right triangle filling the lower right half of an n x n box at (x, y).
void shapes_wedge(GContext *ctx, int x, int y, int n, GColor color);

// Plus / diagonal cross, arm length `r`.
void shapes_plus(GContext *ctx, GPoint c, int r, GColor color);
void shapes_cross(GContext *ctx, GPoint c, int r, GColor color);

// The mock-up's partly-cloudy (day) icon, 18 x 13, at `origin`.
void shapes_partly_cloudy(GContext *ctx, GPoint origin, GColor fg);

// Weather glyph for a WMO code inside a square of side `s` at `origin`.
// `bg` is used to cut the crescent moon.
void shapes_weather(GContext *ctx, GPoint origin, int s, int code, bool is_day,
                    GColor fg, GColor bg);
