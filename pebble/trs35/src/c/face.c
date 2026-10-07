#include "face.h"
#include "barcode.h"
#include "shapes.h"
#include "state.h"
#include "theme.h"

// Everything below is laid out on a 6px side margin: content runs from
// x = 6 to x = 137 (and 6 / 65 inside a 72px cell). Positions are local to
// each module; layout.c places the modules.
#define MARGIN 6
#define STEP_GOAL 10000

// Digit ink metrics measured from the fonts at build time (see wscript).
#ifndef FM_TIME_TOP
#define FM_TIME_TOP 15
#define FM_TIME_H 37
#define FM_DATE_TOP 6
#define FM_DATE_H 15
#define FM_TEMP_TOP 7
#define FM_TEMP_H 17
#define FM_STEPS_TOP 9
#define FM_STEPS_H 21
#endif
#ifndef FM_TIME_RSB
#define FM_TIME_RSB 5
#define FM_TEMP_RSB 3
#endif
// Silkscreen 8: caps are 5px tall, 3px below the text box top and 1px in
// from its left edge.
#define CODE_TOP 3
#define CODE_LEFT 1
#define CODE_H 5

static Layer *s_root;
static Layer *s_mod[MOD_COUNT];
static Layer *s_fx;  // shake-clip overlay, empty at rest

static GFont s_font_time, s_font_date, s_font_temp, s_font_steps, s_font_code;

static char s_time[8], s_ampm[4], s_wday[4], s_date[8], s_hhmm[6];
static int s_minute;

// animation state, see face_set_anim
static int s_anim_t = -1;   // -1 = at rest
static int s_anim_p = 0;    // 0..1000..0, sin envelope of t
static int s_anim_frame = 0;

/* ---- helpers ------------------------------------------------------------ */

static int lerp(int a, int b, int p) { return a + (b - a) * p / 1000; }

static void begin(GContext *ctx) { graphics_context_set_antialiased(ctx, false); }

static void fill(GContext *ctx, GRect r, GColor c) {
  graphics_context_set_fill_color(ctx, c);
  graphics_fill_rect(ctx, r, 0, GCornerNone);
}

static int text_w(const char *s, GFont font) {
  return graphics_text_layout_get_content_size(s, font, GRect(0, 0, 200, 60), GTextOverflowModeFill,
                                               GTextAlignmentLeft).w;
}

// Draws `s` with its ink top at y (font_top: ink offset of that font).
static void text_at(GContext *ctx, const char *s, GFont font, int font_top, int x, int y, int w,
                    GColor color) {
  graphics_context_set_text_color(ctx, color);
  graphics_draw_text(ctx, s, font, GRect(x, y - font_top, w, 80), GTextOverflowModeFill,
                     GTextAlignmentLeft, NULL);
}

static void code_at(GContext *ctx, const char *s, int x, int y, GColor c) {
  text_at(ctx, s, s_font_code, CODE_TOP, x - CODE_LEFT, y, 100, c);
}

// The two cells trade colors for the middle of the shake clip.
static bool colors_swapped(void) { return s_anim_t >= 0 && s_anim_p > 600; }

/* ---- header: PEBBLE TIME_                         80% ▮▮▮▮▯ ------------- */

static void header_update(Layer *layer, GContext *ctx) {
  begin(ctx);
  const Theme *th = theme_get();
  GRect b = layer_get_bounds(layer);
  fill(ctx, b, th->bg);
  const int y = 5;  // caps row 5..9

  // the watch's name with a terminal cursor (it blinks during the shake
  // clip); while the phone is lost the slot becomes an inverted NO LINK tag
  const char *name = g_bt_connected ? state_watch_name() : "NO LINK";
  int nw = text_w(name, s_font_code);
  GColor ink = th->fg;
  if (!g_bt_connected) {
    fill(ctx, GRect(MARGIN - 2, y - 2, nw + 3, CODE_H + 4), th->fg);
    ink = th->bg;
  }
  code_at(ctx, name, MARGIN, y, ink);
  if (s_anim_t < 0 || (s_anim_frame / 2) % 2 == 0) {
    fill(ctx, GRect(MARGIN + nw + 1, y + CODE_H - 1, 3, 1), th->fg);
  }

  // battery: five 4x7 cells ending on the right margin; empty ones are a
  // muted outline around the panel color
  int pct = g_battery.charge_percent;
  int cells_x = b.size.w - MARGIN - 5 * 5 + 1;
  int filled = (pct + 19) / 20;
  for (int i = 0; i < 5; i++) {
    GRect cell = GRect(cells_x + i * 5, y - 1, 4, 7);
    fill(ctx, cell, i < filled ? th->fg : th->muted);
    if (i >= filled) fill(ctx, grect_inset(cell, GEdgeInsets(1)), th->panel);
  }
  char buf[8];
  snprintf(buf, sizeof(buf), g_battery.is_charging ? "+%d%%" : "%d%%", pct);
  int px = cells_x - 4 - text_w(buf, s_font_code);
  bool low = pct <= 20 && !g_battery.is_charging;
  code_at(ctx, buf, px, y, low ? th->fg : th->muted);
}

/* ---- time: corner marks, digits, minute ruler, position / AM-PM --------- */

// local rows inside the 85px frame; the content is centered between the marks
#define T_DIGITS 13
#define T_RULER (T_DIGITS + FM_TIME_H + 4)   // 7px ruler box
#define T_LABELS (T_RULER + 7 + 6)

static void time_update(Layer *layer, GContext *ctx) {
  begin(ctx);
  const Theme *th = theme_get();
  GRect b = layer_get_bounds(layer);
  fill(ctx, b, th->bg);
  int r = b.size.w - 1 - MARGIN, btm = b.size.h - 1;

  shapes_corner(ctx, MARGIN, 1, 1, 1, th->fg);
  shapes_corner(ctx, r, 1, -1, 1, th->fg);
  shapes_corner(ctx, MARGIN, btm - 1, 1, -1, th->fg);
  shapes_corner(ctx, r, btm - 1, -1, -1, th->fg);

  // glitch: a couple of frames jump sideways
  int jx = 0;
  if (s_anim_t >= 0 && (s_anim_frame % 4) == 1) jx = (s_anim_frame & 4) ? 3 : -3;
  // centre the ink, not the advance (the last glyph's advance has trailing room)
  int w = text_w(s_time, s_font_time);
  int x = (b.size.w - (w - FM_TIME_RSB) + 1) / 2;
  text_at(ctx, s_time, s_font_time, FM_TIME_TOP, x + jx, T_DIGITS, w + 8, th->fg);

  int pos = s_minute * 1000 / 60;
  if (s_anim_t >= 0) pos = lerp(pos, s_anim_t < 500 ? 1000 : 0, s_anim_p);  // sweep
  GRect ruler = GRect(MARGIN + 4, T_RULER, b.size.w - 2 * (MARGIN + 4), 7);
  shapes_ruler(ctx, ruler, pos, th->muted, th->fg);

  // where the weather is from (phone position), else the firmware version
  char where[24];
  state_format_position(where, sizeof(where));
  if (where[0] == '\0') {
    WatchInfoVersion v = watch_info_get_firmware_version();
    snprintf(where, sizeof(where), "FW %d.%d.%d", v.major, v.minor, v.patch);
  }
  code_at(ctx, where, ruler.origin.x, T_LABELS, th->muted);
  code_at(ctx, s_ampm, ruler.origin.x + ruler.size.w - text_w(s_ampm, s_font_code), T_LABELS, th->muted);
}

/* ---- date: WED   07.10◢            [checker] --------------------------- */

#define DATE_X 54  // date column, clear of the widest weekday

static void date_update(Layer *layer, GContext *ctx) {
  begin(ctx);
  const Theme *th = theme_get();
  GRect b = layer_get_bounds(layer);
  fill(ctx, b, th->bg);
  const int y = 1;  // ink rows 1..15

  text_at(ctx, s_wday, s_font_date, FM_DATE_TOP, MARGIN, y, 50, th->fg);
  text_at(ctx, s_date, s_font_date, FM_DATE_TOP, DATE_X, y, 70, th->fg);
  shapes_wedge(ctx, DATE_X + text_w(s_date, s_font_date), y + FM_DATE_H - 5, 5, th->fg);

  int phase = s_anim_t >= 0 ? s_anim_frame / 2 : 0;
  shapes_checker(ctx, GRect(b.size.w - MARGIN - 15, y, 15, 15), 3, phase, th->accent);
}

/* ---- weather cell: barcode over a block with icon + temperature -------- */

static void wx_update(Layer *layer, GContext *ctx) {
  begin(ctx);
  const Theme *th = theme_get();
  GRect b = layer_get_bounds(layer);
  bool sw = colors_swapped();
  GColor block = sw ? th->panel : th->block;
  GColor ink = sw ? th->panel_ink : th->block_ink;
  fill(ctx, b, th->bg);

  // Code 128 of the current time (HHMM): it scans as the time shown above.
  // During the clip it scrambles. It stands on the block, 18px tall.
  char code[16];
  if (s_anim_t >= 0) {
    snprintf(code, sizeof(code), "%02d%02d", (s_anim_frame * 37) % 100, (s_anim_frame * 61 + 7) % 100);
  } else {
    snprintf(code, sizeof(code), "%s", s_hhmm);
  }
  barcode_draw(ctx, GPoint(MARGIN, 0), 18, code, th->fg);

  GRect blk = GRect(0, 18, b.size.w, b.size.h - 18);
  fill(ctx, blk, block);
  // icon in an 18 x 13 box, 7px below the block top
  GPoint io = GPoint(MARGIN, blk.origin.y + 7);
  if (g_weather.code >= 1 && g_weather.code <= 3 && g_weather.is_day) {
    shapes_partly_cloudy(ctx, io, ink);
  } else {
    shapes_weather(ctx, GPoint(io.x + 2, io.y - 1), 15, g_weather.code, g_weather.is_day, ink, block);
  }

  // number in the temperature font, then a small drawn degree ring 4px after
  // the last digit, top-aligned with it (no unit letter)
  char temp[8];
  state_format_temp(temp, sizeof(temp));
  char *deg = strstr(temp, "°");
  if (deg) *deg = '\0';
  int tx = MARGIN + 24;
  int ty = blk.origin.y + (blk.size.h - FM_TEMP_H) / 2;
  int ink_w = text_w(temp, s_font_temp) - FM_TEMP_RSB;
  if (tx + ink_w + 8 > b.size.w - 2) tx = b.size.w - 2 - 8 - ink_w;  // "-12" squeezes toward the icon
  text_at(ctx, temp, s_font_temp, FM_TEMP_TOP, tx, ty, 60, ink);
  int dx = tx + ink_w + 4;
  fill(ctx, GRect(dx + 1, ty, 2, 1), ink);
  fill(ctx, GRect(dx + 1, ty + 3, 2, 1), ink);
  fill(ctx, GRect(dx, ty + 1, 1, 2), ink);
  fill(ctx, GRect(dx + 3, ty + 1, 1, 2), ink);
}

/* ---- steps panel: leader line ×+, count, % and distance ---------------- */

static void steps_update(Layer *layer, GContext *ctx) {
  begin(ctx);
  const Theme *th = theme_get();
  GRect b = layer_get_bounds(layer);
  bool sw = colors_swapped();
  GColor panel = sw ? th->block : th->panel;
  GColor ink = sw ? th->block_ink : th->panel_ink;
  fill(ctx, b, panel);
  int right = b.size.w - MARGIN;  // first column past the content

  // leader: a corner tab, a 45 degree tick, then a dotted-to-solid rule that
  // ends in a cross and a plus (registration marks)
  fill(ctx, GRect(0, 0, 10, 2), ink);
  fill(ctx, GRect(0, 2, 2, 8), ink);
  for (int i = 0; i < 8; i++) fill(ctx, GRect(1 + i, 2 + i, i == 7 ? 2 : 3, 1), ink);
  const int ry = 4;
  for (int x = 13; x < 23; x += 2) fill(ctx, GRect(x, ry, 1, 1), ink);
  fill(ctx, GRect(23, ry, right - 16 - 23, 1), ink);
  shapes_cross(ctx, GPoint(right - 10, ry), 2, ink);
  shapes_plus(ctx, GPoint(right - 3, ry), 2, ink);

  char num[16];
  snprintf(num, sizeof(num), "%05d", g_steps > 99999 ? 99999 : g_steps);
  int nw = text_w(num, s_font_steps);
  text_at(ctx, num, s_font_steps, FM_STEPS_TOP, (b.size.w - nw) / 2 + 1, 14, nw + 4, ink);

  int pct = g_steps * 100 / STEP_GOAL;
  char pbuf[16], dist[16];
  snprintf(pbuf, sizeof(pbuf), "%d%%", pct > 999 ? 999 : pct);
  snprintf(dist, sizeof(dist), "%d.%dKM", g_distance_m / 1000, (g_distance_m % 1000) / 100);
  const int ly = b.size.h - 4 - CODE_H;
  code_at(ctx, pbuf, MARGIN, ly, ink);
  code_at(ctx, dist, right - text_w(dist, s_font_code), ly, ink);
}

/* ---- shake overlay ------------------------------------------------------ */

// A ring with a crosshair and turning rays opens over the time and closes.
static void fx_update(Layer *layer, GContext *ctx) {
  if (s_anim_t < 0) return;
  begin(ctx);
  const Theme *th = theme_get();
  GRect t = layer_get_frame(s_mod[MOD_TIME]);
  GPoint c = GPoint(t.origin.x + t.size.w / 2, t.origin.y + T_DIGITS + FM_TIME_H / 2);
  int r = 6 + s_anim_p * 36 / 1000;
  graphics_context_set_stroke_color(ctx, th->accent);
  graphics_context_set_stroke_width(ctx, 3);
  graphics_draw_circle(ctx, c, r);
  graphics_context_set_stroke_width(ctx, 1);
  int turn = s_anim_t * TRIG_MAX_ANGLE / 4000;  // a quarter turn over the clip
  for (int i = 0; i < 16; i++) {
    int32_t a = turn + TRIG_MAX_ANGLE * i / 16;
    int sx = sin_lookup(a), cy = cos_lookup(a);
    int r0 = r + 4, r1 = r + 4 + r / 3;
    graphics_draw_line(ctx, GPoint(c.x + sx * r0 / TRIG_MAX_RATIO, c.y - cy * r0 / TRIG_MAX_RATIO),
                       GPoint(c.x + sx * r1 / TRIG_MAX_RATIO, c.y - cy * r1 / TRIG_MAX_RATIO));
  }
  shapes_plus(ctx, c, 5, th->accent);
}

/* ---- structure ---------------------------------------------------------- */

static void root_update(Layer *layer, GContext *ctx) {
  fill(ctx, layer_get_bounds(layer), theme_get()->bg);
}

static void place_modules(void) {
  const Layout *lay = layout_get(g_settings.layout);
  for (int i = 0; i < MOD_COUNT; i++) layer_set_frame(s_mod[i], lay->mod[i]);
  if (s_anim_t >= 0) {
    // weather and steps cells trade places and come back
    GRect a = lay->mod[MOD_WX], c = lay->mod[MOD_STEPS];
    GRect ma = a, mc = c;
    ma.origin.x = lerp(a.origin.x, c.origin.x, s_anim_p);
    mc.origin.x = lerp(c.origin.x, a.origin.x, s_anim_p);
    layer_set_frame(s_mod[MOD_WX], ma);
    layer_set_frame(s_mod[MOD_STEPS], mc);
  }
}

void face_create(Window *window) {
  s_font_time = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_TIME_53));
  s_font_date = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_DATE_21));
  s_font_temp = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_TEMP_24));
  s_font_steps = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_STEPS_30));
  s_font_code = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_CODE_8));

  s_root = window_get_root_layer(window);
  layer_set_update_proc(s_root, root_update);
  for (int i = 0; i < MOD_COUNT; i++) {
    s_mod[i] = layer_create(GRect(0, 0, 1, 1));
    layer_add_child(s_root, s_mod[i]);
  }
  layer_set_update_proc(s_mod[MOD_HEADER], header_update);
  layer_set_update_proc(s_mod[MOD_TIME], time_update);
  layer_set_update_proc(s_mod[MOD_DATE], date_update);
  layer_set_update_proc(s_mod[MOD_WX], wx_update);
  layer_set_update_proc(s_mod[MOD_STEPS], steps_update);
  s_fx = layer_create(layer_get_bounds(s_root));
  layer_set_update_proc(s_fx, fx_update);
  layer_add_child(s_root, s_fx);
  place_modules();
}

void face_destroy(void) {
  layer_destroy(s_fx);
  for (int i = 0; i < MOD_COUNT; i++) layer_destroy(s_mod[i]);
  fonts_unload_custom_font(s_font_time);
  fonts_unload_custom_font(s_font_date);
  fonts_unload_custom_font(s_font_temp);
  fonts_unload_custom_font(s_font_steps);
  fonts_unload_custom_font(s_font_code);
}

void face_apply_layout(void) {
  place_modules();
  face_mark_all_dirty();
}

void face_mark_dirty(ModuleId id) { layer_mark_dirty(s_mod[id]); }

void face_mark_all_dirty(void) {
  layer_mark_dirty(s_root);
  layer_mark_dirty(s_fx);
  for (int i = 0; i < MOD_COUNT; i++) layer_mark_dirty(s_mod[i]);
}

void face_set_anim(int t, int frame) {
  s_anim_t = t;
  s_anim_frame = frame;
  // sin(pi * t): 0 -> 1000 -> 0
  s_anim_p = t < 0 ? 0 : sin_lookup(TRIG_MAX_ANGLE / 2 * t / 1000) * 1000 / TRIG_MAX_RATIO;
  place_modules();
  face_mark_all_dirty();
}

void face_set_time(struct tm *now) {
  bool h24 = g_settings.clock_mode == CLOCK_24H ||
             (g_settings.clock_mode == CLOCK_AUTO && clock_is_24h_style());
  strftime(s_time, sizeof(s_time), h24 ? "%H:%M" : "%I:%M", now);
  if (h24) snprintf(s_ampm, sizeof(s_ampm), "24H");
  else strftime(s_ampm, sizeof(s_ampm), "%p", now);
  strftime(s_hhmm, sizeof(s_hhmm), "%H%M", now);
  s_minute = now->tm_min;

  // no pointer tables: app data is not relocated, so index into one string
  static const char days[] = "SUNMONTUEWEDTHUFRISAT";
  memcpy(s_wday, &days[(now->tm_wday % 7) * 3], 3);
  s_wday[3] = '\0';
  strftime(s_date, sizeof(s_date), "%d.%m", now);

  layer_mark_dirty(s_mod[MOD_TIME]);
  layer_mark_dirty(s_mod[MOD_DATE]);
  layer_mark_dirty(s_mod[MOD_WX]);  // the barcode carries the time
}
