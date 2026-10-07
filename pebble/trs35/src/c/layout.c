#include "layout.h"
#include "state.h"

// 144 x 168 (basalt). Modules butt against each other like a collage.
// Time boxes are sized so the gap above the digits matches the gap below
// the label line (see time_update in face.c).
static const Layout s_layouts[LAYOUT_COUNT] = {
  [LAYOUT_STACK] = {
    .mod = {
      [MOD_HEADER] = {{0, 0}, {144, 16}},
      [MOD_TIME]   = {{0, 17}, {144, 56}},
      [MOD_DATE]   = {{0, 74}, {144, 20}},
      [MOD_WX]     = {{0, 96}, {72, 72}},
      [MOD_STEPS]  = {{72, 96}, {72, 72}},
    },
  },
  [LAYOUT_INVERT] = {
    .mod = {
      [MOD_HEADER] = {{0, 0}, {144, 16}},
      [MOD_WX]     = {{0, 16}, {72, 72}},
      [MOD_STEPS]  = {{72, 16}, {72, 72}},
      [MOD_DATE]   = {{0, 91}, {144, 20}},
      [MOD_TIME]   = {{0, 112}, {144, 56}},
    },
  },
  [LAYOUT_COLUMN] = {
    .mod = {
      [MOD_HEADER] = {{0, 0}, {144, 16}},
      [MOD_WX]     = {{0, 16}, {50, 76}},
      [MOD_STEPS]  = {{0, 92}, {50, 76}},
      [MOD_TIME]   = {{50, 16}, {94, 110}},
      [MOD_DATE]   = {{54, 128}, {90, 40}},
    },
    .time_stacked = true,
  },
  [LAYOUT_BAND] = {
    .mod = {
      [MOD_HEADER] = {{0, 0}, {144, 16}},
      [MOD_WX]     = {{0, 16}, {144, 32}},
      [MOD_TIME]   = {{0, 48}, {144, 54}},
      [MOD_DATE]   = {{0, 102}, {144, 20}},
      [MOD_STEPS]  = {{0, 124}, {144, 44}},
    },
  },
  [LAYOUT_COLUMN_R] = {
    .mod = {
      [MOD_HEADER] = {{0, 0}, {144, 16}},
      [MOD_TIME]   = {{0, 16}, {94, 110}},
      [MOD_DATE]   = {{4, 128}, {90, 40}},
      [MOD_WX]     = {{94, 16}, {50, 76}},
      [MOD_STEPS]  = {{94, 92}, {50, 76}},
    },
    .time_stacked = true,
  },
};

const Layout *layout_get(int id) {
  if (id < 0 || id >= LAYOUT_COUNT) id = 0;
  return &s_layouts[id];
}
