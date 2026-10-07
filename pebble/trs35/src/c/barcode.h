#pragma once
#include <pebble.h>

// Code 128 (set C) for an even number of digits. A real, scannable symbol:
// start C, digit pairs, mod-103 check symbol, stop. Each symbol is 11
// modules wide, the stop 13, so n digits take 11 * (n / 2 + 2) + 13 modules.
#define BARCODE_MODULES(n_digits) (11 * ((n_digits) / 2 + 2) + 13)

// Draws `digits` (even length, '0'-'9') with 1px modules from `origin`,
// `h` px tall. Returns the width drawn, 0 if the input is not valid.
int barcode_draw(GContext *ctx, GPoint origin, int h, const char *digits, GColor color);
