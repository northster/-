#pragma once
#include <pebble.h>
#include "layout.h"

// Builds the module layers inside `window` and draws them.
void face_create(Window *window);
void face_destroy(void);

// Re-applies the current layout preset (after a settings change).
void face_apply_layout(void);

// Redraw one module / everything.
void face_mark_dirty(ModuleId id);
void face_mark_all_dirty(void);

// Shake animation: t runs 0..1000 over the clip, frame counts up from 0.
// face_set_anim(-1, 0) returns everything to rest.
void face_set_anim(int t, int frame);

// Current time text (called from the tick handler).
void face_set_time(struct tm *now);
