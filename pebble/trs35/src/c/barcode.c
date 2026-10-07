#include "barcode.h"

// Code 93 symbol patterns 0..46 and the start/stop "*" (47) as 9-bit masks,
// bar = 1, MSB first.
static const uint16_t s_patterns[48] = {
  0x114, 0x148, 0x144, 0x142, 0x128, 0x124, 0x122, 0x150,
  0x112, 0x10A, 0x1A8, 0x1A4, 0x1A2, 0x194, 0x192, 0x18A,
  0x168, 0x164, 0x162, 0x134, 0x11A, 0x158, 0x14C, 0x146,
  0x12C, 0x116, 0x1B4, 0x1B2, 0x1AC, 0x1A6, 0x196, 0x19A,
  0x16C, 0x166, 0x136, 0x13A, 0x12E, 0x1D4, 0x1D2, 0x1CA,
  0x16E, 0x176, 0x1AE, 0x126, 0x1DA, 0x1D6, 0x132, 0x15E,
};
#define START_STOP 47

static int draw_bits(GContext *ctx, int x, int y, int h, uint16_t bits, int n) {
  for (int i = n - 1; i >= 0; i--, x++) {
    if (bits & (1 << i)) graphics_fill_rect(ctx, GRect(x, y, 1, h), 0, GCornerNone);
  }
  return x;
}

// mod-47 check over values[0..n), weights 1..max counted from the right
static int check(const int *v, int n, int max) {
  int sum = 0;
  for (int i = 0; i < n; i++) sum += v[i] * ((n - 1 - i) % max + 1);
  return sum % 47;
}

int barcode_draw(GContext *ctx, GPoint o, int h, const char *digits, bool term, GColor color) {
  int v[BARCODE_MAX_DIGITS + 2];
  int n = strlen(digits);
  if (n == 0 || n > BARCODE_MAX_DIGITS) return 0;
  for (int i = 0; i < n; i++) {
    if (digits[i] < '0' || digits[i] > '9') return 0;
    v[i] = digits[i] - '0';
  }
  v[n] = check(v, n, 20);
  v[n + 1] = check(v, n + 1, 15);
  graphics_context_set_fill_color(ctx, color);
  int x = draw_bits(ctx, o.x, o.y, h, s_patterns[START_STOP], 9);
  for (int i = 0; i < n + 2; i++) x = draw_bits(ctx, x, o.y, h, s_patterns[v[i]], 9);
  x = draw_bits(ctx, x, o.y, h, s_patterns[START_STOP], 9);
  if (term) x = draw_bits(ctx, x, o.y, h, 1, 1);
  return x - o.x;
}
