#include "layout.h"
#include "state.h"

// 144 x 168 (basalt). Modules butt against each other like a collage;
// the background shows only through deliberate gaps.
static const Layout s_layouts[LAYOUT_COUNT] = {
  [LAYOUT_STACK] = {
    .mod = {
      [MOD_HEADER] = {{0, 0}, {144, 16}},
      [MOD_TIME]   = {{0, 17}, {144, 50}},
      [MOD_DATE]   = {{0, 68}, {144, 20}},
      [MOD_WX]     = {{0, 90}, {72, 78}},
      [MOD_STEPS]  = {{72, 90}, {72, 78}},
    },
    .time_stacked = false,
  },
  [LAYOUT_INVERT] = {
    .mod = {
      [MOD_HEADER] = {{0, 0}, {144, 16}},
      [MOD_WX]     = {{0, 16}, {72, 76}},
      [MOD_STEPS]  = {{72, 16}, {72, 76}},
      [MOD_DATE]   = {{0, 94}, {144, 20}},
      [MOD_TIME]   = {{0, 116}, {144, 50}},
    },
    .time_stacked = false,
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
};

const Layout *layout_get(int id) {
  if (id < 0 || id >= LAYOUT_COUNT) id = 0;
  return &s_layouts[id];
}
