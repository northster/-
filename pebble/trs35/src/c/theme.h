#pragma once
#include <pebble.h>

// Color roles, kept deliberately few: a field, two inks, two blocks and a
// single spot color.
typedef struct {
  GColor bg;         // the field
  GColor fg;         // main ink: time, date, marks
  GColor muted;      // secondary ink: ruler, small labels, empty battery cells
  GColor block;      // weather block
  GColor block_ink;
  GColor panel;      // steps panel
  GColor panel_ink;
  GColor accent;     // the one spot color (checker, shake overlay)
} Theme;

const Theme *theme_get(void);
