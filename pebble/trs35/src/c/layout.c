#include "layout.h"
#include "state.h"

// 144 x 168 (basalt). Every module keeps its size in every preset; only the
// positions change, so the drawing code has one shape to get right.
//   header 15, time frame 85, gap 1, date 18, gap 1, cells 48
static const Layout s_layouts[LAYOUT_COUNT] = {
  [LAYOUT_STACK] = {{
    [MOD_HEADER] = {{0, 0}, {144, 15}},
    [MOD_TIME]   = {{0, 15}, {144, 85}},
    [MOD_DATE]   = {{0, 101}, {144, 18}},
    [MOD_WX]     = {{0, 120}, {72, 48}},
    [MOD_STEPS]  = {{72, 120}, {72, 48}},
  }},
  [LAYOUT_SWAP] = {{
    [MOD_HEADER] = {{0, 0}, {144, 15}},
    [MOD_TIME]   = {{0, 15}, {144, 85}},
    [MOD_DATE]   = {{0, 101}, {144, 18}},
    [MOD_WX]     = {{72, 120}, {72, 48}},
    [MOD_STEPS]  = {{0, 120}, {72, 48}},
  }},
  [LAYOUT_INVERT] = {{
    [MOD_HEADER] = {{0, 0}, {144, 15}},
    [MOD_WX]     = {{0, 15}, {72, 48}},
    [MOD_STEPS]  = {{72, 15}, {72, 48}},
    [MOD_DATE]   = {{0, 64}, {144, 18}},
    [MOD_TIME]   = {{0, 83}, {144, 85}},
  }},
};

const Layout *layout_get(int id) {
  if (id < 0 || id >= LAYOUT_COUNT) id = 0;
  return &s_layouts[id];
}
