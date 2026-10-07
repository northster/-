#pragma once
#include <pebble.h>

// The face is a set of rectangular modules; a layout preset places them.
typedef enum {
  MOD_HEADER,  // code label, bluetooth, battery
  MOD_TIME,
  MOD_DATE,
  MOD_WX,      // weather cell
  MOD_STEPS,   // steps cell
  MOD_COUNT
} ModuleId;

typedef enum {
  LAYOUT_STACK = 0,   // time on top, two cells below
  LAYOUT_INVERT = 1,  // two cells on top, time below
  LAYOUT_COLUMN = 2,  // cells in a left column, hours over minutes on the right
} LayoutId;

typedef struct {
  GRect mod[MOD_COUNT];
  bool time_stacked;   // hours above minutes
} Layout;

const Layout *layout_get(int id);
