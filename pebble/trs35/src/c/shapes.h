#pragma once
#include <pebble.h>

// Hand-drawn geometric motifs: half discs, triangle rows, rings, hatching,
// barcode ticks and the weather glyphs. All are plain primitives, no bitmaps.

// Half disc with its flat edge through `center`; `rot_deg` 0 = dome points up.
void shapes_half_disc(GContext *ctx, GPoint center, int r, int rot_deg, GColor color);

// A row of filled triangles (pointing right), `size` px tall, filling `box`;
// `phase` scrolls the row in px (wraps every size + gap).
void shapes_triangle_row(GContext *ctx, GRect box, int size, int gap, int phase, GColor color);

// Concentric rings around `center`, from r_min outward every `step` px.
void shapes_rings(GContext *ctx, GPoint center, int r_min, int r_max, int step, GColor color);

// 45 degree hatch inside `box`, lines every `step` px.
void shapes_hatch(GContext *ctx, GRect box, int step, GColor color);

// Barcode-like vertical ticks from a seed, filling `box`.
void shapes_barcode(GContext *ctx, GRect box, uint32_t seed, GColor color);

// Registration mark: small circle with a cross through it.
void shapes_crosshair(GContext *ctx, GPoint c, int r, GColor color);

// Weather glyph for a WMO code inside a square of side `s` at `origin`.
// `bg` is used to cut the crescent moon.
void shapes_weather(GContext *ctx, GPoint origin, int s, int code, bool is_day,
                    GColor fg, GColor bg);

// Bluetooth rune drawn with lines, h px tall.
void shapes_bt(GContext *ctx, GPoint origin, int h, GColor color);
