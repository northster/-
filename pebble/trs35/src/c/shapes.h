#pragma once
#include <pebble.h>

// Hand-drawn geometric motifs: half discs, quarter-disc tiles, triangle
// rows, plus marks and the weather glyphs. All are plain primitives, no bitmaps.

// Half disc with its flat edge through `center`; `rot_deg` 0 = dome points up.
void shapes_half_disc(GContext *ctx, GPoint center, int r, int rot_deg, GColor color);

// A row of filled triangles (pointing right), `size` px tall, filling `box`;
// `phase` scrolls the row in px (wraps every size + gap).
void shapes_triangle_row(GContext *ctx, GRect box, int size, int gap, int phase, GColor color);




// Field of quarter discs (the grey tile pattern of the references): one
// quarter disc per `tile` px cell, its corner picked from the cell position
// plus `phase` (0..3) so the field can turn during the shake clip.
void shapes_quarter_tiles(GContext *ctx, GRect box, int tile, int phase, GColor color);

// Plus sign, arm length `r`.
void shapes_plus(GContext *ctx, GPoint c, int r, GColor color);


// Weather glyph for a WMO code inside a square of side `s` at `origin`.
// `bg` is used to cut the crescent moon.
void shapes_weather(GContext *ctx, GPoint origin, int s, int code, bool is_day,
                    GColor fg, GColor bg);

// Bluetooth rune drawn with lines, h px tall.
void shapes_bt(GContext *ctx, GPoint origin, int h, GColor color);
