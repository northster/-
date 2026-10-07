#pragma once
#include <pebble.h>

// Code 93 for a string of digits: start, digits, the C and K mod-47 check
// symbols, stop, then a one-module termination bar. Every symbol is 9
// modules, so n digits take 9 * (n + 4) + 1 modules (4 digits: 73).
#define BARCODE_MAX_DIGITS 8
#define BARCODE_MODULES(n_digits, term) (9 * ((n_digits) + 4) + ((term) ? 1 : 0))

// Draws `digits` with 1px modules from `origin`, `h` px tall. Without `term`
// the trailing bar is left off (one pixel narrower; scanners then refuse it).
// Returns the width drawn, 0 if the input is not valid.
int barcode_draw(GContext *ctx, GPoint origin, int h, const char *digits, bool term, GColor color);
