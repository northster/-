#pragma once
#include <pebble.h>

// The face is a set of rectangular modules; a layout preset places them.
typedef enum {
  MOD_HEADER,  // watch name, link, battery
  MOD_TIME,    // time inside corner marks, minute ruler, position / AM-PM
  MOD_DATE,    // weekday, date, checker
  MOD_WX,      // barcode + weather block
  MOD_STEPS,   // steps panel
  MOD_COUNT
} ModuleId;

typedef enum {
  LAYOUT_STACK = 0,   // time on top, weather left, steps right
  LAYOUT_SWAP = 1,    // as STACK with the two cells swapped
  LAYOUT_INVERT = 2,  // cells on top, time below
} LayoutId;

typedef struct {
  GRect mod[MOD_COUNT];
} Layout;

const Layout *layout_get(int id);
