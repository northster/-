#pragma once
#include <pebble.h>

// Color roles. Blocks are filled with a1/a2/a3/panel and their text uses
// the matching *_ink so every theme stays readable.
typedef struct {
  GColor bg;        // screen background
  GColor fg;        // main text on bg (time)
  GColor muted;     // small code labels on bg
  GColor a1;        // primary accent block (acid lime in the default theme)
  GColor a1_ink;
  GColor a2;        // secondary accent (cyan)
  GColor a2_ink;
  GColor a3;        // tertiary accent (electric blue)
  GColor a3_ink;
  GColor panel;     // neutral block (white / black)
  GColor panel_ink;
  GColor alert;     // bluetooth lost, low battery
  GColor alert_ink;
} Theme;

const Theme *theme_get(void);
