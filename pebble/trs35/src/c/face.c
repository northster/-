#include "face.h"
#include "shapes.h"
#include "state.h"
#include "theme.h"

#define STEP_GOAL 10000
#define HEADER_CODE "TR-S.35001"

static Layer *s_root;
static Layer *s_mod[MOD_COUNT];

static GFont s_font_time;
static GFont s_font_med;
static GFont s_font_code;

static char s_hh[4], s_mm[4], s_time[8];
static char s_ampm[4];
static char s_wday[4], s_date[8], s_doy[28];

// animation state, see face_set_anim
static int s_anim_t = -1;   // -1 = at rest
static int s_anim_p = 0;    // 0..1000..0, sin envelope of t
static int s_anim_frame = 0;

/* ---- helpers ------------------------------------------------------------ */

static int lerp(int a, int b, int p) { return a + (b - a) * p / 1000; }

static GRect local(Layer *layer) { return layer_get_bounds(layer); }

static void text(GContext *ctx, const char *s, GFont font, GRect box, GTextAlignment align,
                 GColor color) {
  graphics_context_set_text_color(ctx, color);
  graphics_draw_text(ctx, s, font, box, GTextOverflowModeFill, align, NULL);
}

static void fill(GContext *ctx, GRect r, GColor c) {
  graphics_context_set_fill_color(ctx, c);
  graphics_fill_rect(ctx, r, 0, GCornerNone);
}

// The cells trade colors for the middle of the shake clip.
static bool colors_swapped(void) { return s_anim_t >= 0 && s_anim_p > 600; }

/* ---- header ------------------------------------------------------------- */

static void draw_battery(GContext *ctx, GRect b, const Theme *th, bool show_pct) {
  int pct = g_battery.charge_percent;
  bool low = pct <= 20 && !g_battery.is_charging;
  GColor c = low ? th->alert : th->fg;
  int seg_w = 4, gap = 1, n = 5;
  int x0 = b.size.w - 2 - n * (seg_w + gap);
  int filled = (pct + 19) / 20;
  graphics_context_set_stroke_color(ctx, th->muted);
  for (int i = 0; i < n; i++) {
    GRect seg = GRect(x0 + i * (seg_w + gap), 4, seg_w, 8);
    if (i < filled) fill(ctx, seg, c);
    else graphics_draw_rect(ctx, seg);
  }
  if (!show_pct) return;
  char buf[16];
  snprintf(buf, sizeof(buf), g_battery.is_charging ? "+%d" : "%d", pct);
  text(ctx, buf, s_font_code, GRect(x0 - 26, 2, 24, 10), GTextAlignmentRight, low ? th->alert : th->muted);
}

static void header_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  GRect b = local(layer);
  fill(ctx, b, th->bg);

  // lime tab with the code label; shrinks during the shake clip
  int full = 70;
  int bw = lerp(full, 22, s_anim_p);
  fill(ctx, GRect(0, 0, bw, b.size.h), th->a1);
  shapes_triangle_row(ctx, GRect(bw, 0, 8, b.size.h), b.size.h, 200, 0, th->a1);
  GRect code_box = GRect(3, 2, bw - 3, 12);
  if (bw > 50) text(ctx, HEADER_CODE, s_font_code, code_box, GTextAlignmentLeft, th->a1_ink);

  if (g_bt_connected) {
    shapes_bt(ctx, GPoint(84, 3), 10, th->muted);
  } else {
    // phone lost: alert block takes the place of the rune and battery %
    GRect alert = GRect(78, 1, 38, 14);
    fill(ctx, alert, th->alert);
    text(ctx, "LINK-X", s_font_code, GRect(alert.origin.x + 2, alert.origin.y + 1, alert.size.w - 2, 12),
         GTextAlignmentLeft, th->alert_ink);
  }
  draw_battery(ctx, b, th, g_bt_connected);
}

/* ---- time --------------------------------------------------------------- */

static void time_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  const Layout *lay = layout_get(g_settings.layout);
  GRect b = local(layer);
  fill(ctx, b, th->bg);

  // glitch: a couple of frames jump sideways
  int jx = 0;
  if (s_anim_t >= 0 && (s_anim_frame % 4) == 1) jx = (s_anim_frame & 4) ? 4 : -4;

  if (lay->time_stacked) {
    int half = b.size.h / 2;
    // cyan slab behind the hour column edge
    fill(ctx, GRect(b.size.w - 6, 4, 4, b.size.h - 8), th->a2);
    text(ctx, s_hh, s_font_time, GRect(jx, -10, b.size.w - 8, half + 10), GTextAlignmentCenter, th->fg);
    text(ctx, s_mm, s_font_time, GRect(-jx, half - 12, b.size.w - 8, half + 12), GTextAlignmentCenter, th->fg);
    fill(ctx, GRect(10, half - 1, b.size.w - 28, 2), th->a1);
    text(ctx, s_ampm, s_font_code, GRect(4, b.size.h - 12, 40, 10), GTextAlignmentLeft, th->muted);
  } else {
    fill(ctx, GRect(0, 6, 3, b.size.h - 10), th->a2);
    // the 46px digits sit ~15px below the text origin, ~31px tall
    text(ctx, s_time, s_font_time, GRect(jx, -14, b.size.w, b.size.h + 14), GTextAlignmentCenter, th->fg);
    text(ctx, s_doy, s_font_code, GRect(7, b.size.h - 11, 80, 10), GTextAlignmentLeft, th->muted);
    text(ctx, s_ampm, s_font_code, GRect(b.size.w - 42, b.size.h - 11, 38, 10), GTextAlignmentRight, th->muted);
  }
}

/* ---- date --------------------------------------------------------------- */

static void date_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  GRect b = local(layer);
  fill(ctx, b, th->bg);

  int pw = 50;
  int phase = s_anim_t >= 0 ? s_anim_t * 40 / 1000 : 0;
  fill(ctx, GRect(0, 1, pw, 18), th->panel);
  text(ctx, s_wday, s_font_med, GRect(0, -4, pw, 24), GTextAlignmentCenter, th->panel_ink);

  if (b.size.h >= 36) {
    // two rows (narrow column): weekday + triangles, then the date
    shapes_triangle_row(ctx, GRect(pw + 4, 5, b.size.w - pw - 6, 10), 10, 2, phase, th->a1);
    text(ctx, s_date, s_font_med, GRect(0, 16, b.size.w - 4, 24), GTextAlignmentLeft, th->fg);
  } else {
    text(ctx, s_date, s_font_med, GRect(pw + 4, -4, 60, 24), GTextAlignmentLeft, th->fg);
    if (b.size.w >= 120) {
      shapes_triangle_row(ctx, GRect(b.size.w - 36, 5, 34, 10), 10, 2, phase, th->a1);
    }
  }
}

/* ---- weather cell ------------------------------------------------------- */

static void wx_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  GRect b = local(layer);
  bool sw = colors_swapped();
  GColor cell = sw ? th->a1 : th->a3;
  GColor ink = sw ? th->a1_ink : th->a3_ink;
  GColor shape = sw ? th->a3 : th->a2;
  fill(ctx, b, cell);

  // half disc rising from the bottom right; spins during the clip
  int r = (b.size.w < 60 ? b.size.w : b.size.h) * 2 / 5;
  int rot = s_anim_t >= 0 ? s_anim_t * 360 / 1000 : 0;
  shapes_half_disc(ctx, GPoint(b.size.w - r / 3, b.size.h), r, rot, shape);

  const char *label = state_weather_stale() && g_weather.updated ? "WX.OLD" : "WX.CUR";
  text(ctx, label, s_font_code, GRect(3, 2, b.size.w - 4, 10), GTextAlignmentLeft, ink);

  int icon = b.size.w < 60 ? 22 : 26;
  shapes_weather(ctx, GPoint(4, 15), icon, g_weather.code, g_weather.is_day, ink, cell);

  char buf[8];
  state_format_temp(buf, sizeof(buf));
  text(ctx, buf, s_font_med, GRect(3, 15 + icon - 2, b.size.w - 4, 24), GTextAlignmentLeft, ink);

  char unit[4];
  snprintf(unit, sizeof(unit), g_settings.temp_unit == UNIT_F ? "F" : "C");
  text(ctx, unit, s_font_code, GRect(b.size.w - 12, 2, 10, 10), GTextAlignmentRight, ink);
}

/* ---- steps cell --------------------------------------------------------- */

static void steps_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  GRect b = local(layer);
  bool sw = colors_swapped();
  GColor cell = sw ? th->a2 : th->panel;
  GColor ink = sw ? th->a2_ink : th->panel_ink;
  fill(ctx, b, cell);

  bool narrow = b.size.w < 60;
  int grow = s_anim_p / 120;
  shapes_rings(ctx, GPoint(b.size.w - 2, b.size.h - 2), 4 + grow, 22 + grow, 5, ink);

  text(ctx, "STP.CNT", s_font_code, GRect(3, 2, b.size.w - 4, 10), GTextAlignmentLeft, ink);

  char buf[16];
  if (narrow && g_steps >= 10000) snprintf(buf, sizeof(buf), "%dK", g_steps / 1000);
  else if (narrow) snprintf(buf, sizeof(buf), "%d", g_steps);
  else snprintf(buf, sizeof(buf), "%05d", g_steps > 99999 ? 99999 : g_steps);
  text(ctx, buf, s_font_med, GRect(3, 12, b.size.w - 4, 24), GTextAlignmentLeft, ink);

  // goal progress: 10 ticks (5 in the narrow column)
  int n = narrow ? 5 : 10;
  int pct = g_steps * 100 / STEP_GOAL;
  int done = pct * n / 100;
  int tw = (b.size.w - 8) / n;
  graphics_context_set_stroke_color(ctx, ink);
  for (int i = 0; i < n; i++) {
    GRect t = GRect(4 + i * tw, 40, tw - 2, 6);
    if (i < done) fill(ctx, t, ink);
    else graphics_draw_rect(ctx, t);
  }
  char pbuf[16];
  snprintf(pbuf, sizeof(pbuf), "%d%%", pct > 999 ? 999 : pct);
  text(ctx, pbuf, s_font_code, GRect(3, 48, 40, 10), GTextAlignmentLeft, ink);

  if (!narrow) shapes_barcode(ctx, GRect(4, b.size.h - 13, 38, 9), 35001, ink);
}

/* ---- structure ---------------------------------------------------------- */

static void root_update(Layer *layer, GContext *ctx) {
  fill(ctx, local(layer), theme_get()->bg);
}

static void place_modules(void) {
  const Layout *lay = layout_get(g_settings.layout);
  for (int i = 0; i < MOD_COUNT; i++) {
    layer_set_frame(s_mod[i], lay->mod[i]);
  }
  if (s_anim_t >= 0) {
    // weather and steps cells trade places and come back
    GRect a = lay->mod[MOD_WX], b = lay->mod[MOD_STEPS];
    GRect ma = a, mb = b;
    ma.origin.x = lerp(a.origin.x, b.origin.x, s_anim_p);
    ma.origin.y = lerp(a.origin.y, b.origin.y, s_anim_p);
    mb.origin.x = lerp(b.origin.x, a.origin.x, s_anim_p);
    mb.origin.y = lerp(b.origin.y, a.origin.y, s_anim_p);
    layer_set_frame(s_mod[MOD_WX], ma);
    layer_set_frame(s_mod[MOD_STEPS], mb);
  }
}

void face_create(Window *window) {
  s_font_time = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_TIME_46));
  s_font_med = fonts_load_custom_font(resource_get_handle(RESOURCE_ID_FONT_MED_20));
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
  place_modules();
}

void face_destroy(void) {
  for (int i = 0; i < MOD_COUNT; i++) layer_destroy(s_mod[i]);
  fonts_unload_custom_font(s_font_time);
  fonts_unload_custom_font(s_font_med);
  fonts_unload_custom_font(s_font_code);
}

void face_apply_layout(void) {
  place_modules();
  face_mark_all_dirty();
}

void face_mark_dirty(ModuleId id) { layer_mark_dirty(s_mod[id]); }

void face_mark_all_dirty(void) {
  layer_mark_dirty(s_root);
  for (int i = 0; i < MOD_COUNT; i++) layer_mark_dirty(s_mod[i]);
}

void face_set_anim(int t, int frame) {
  s_anim_t = t;
  s_anim_frame = frame;
  if (t < 0) {
    s_anim_p = 0;
  } else {
    // sin(pi * t): 0 -> 1000 -> 0
    s_anim_p = sin_lookup(TRIG_MAX_ANGLE / 2 * t / 1000) * 1000 / TRIG_MAX_RATIO;
  }
  place_modules();
  face_mark_all_dirty();
}

void face_set_time(struct tm *now) {
  bool h24 = g_settings.clock_mode == CLOCK_24H ||
             (g_settings.clock_mode == CLOCK_AUTO && clock_is_24h_style());
  strftime(s_hh, sizeof(s_hh), h24 ? "%H" : "%I", now);
  strftime(s_mm, sizeof(s_mm), "%M", now);
  snprintf(s_time, sizeof(s_time), "%s:%s", s_hh, s_mm);
  if (h24) snprintf(s_ampm, sizeof(s_ampm), "24H");
  else strftime(s_ampm, sizeof(s_ampm), "%p", now);

  // no pointer tables: app data is not relocated, so index into one string
  static const char days[] = "SUNMONTUEWEDTHUFRISAT";
  memcpy(s_wday, &days[(now->tm_wday % 7) * 3], 3);
  s_wday[3] = '\0';
  strftime(s_date, sizeof(s_date), "%d.%m", now);
  // ISO-8601 week, clamped at the year edges (good enough for a label)
  int iso_wday = now->tm_wday == 0 ? 7 : now->tm_wday;
  int week = (now->tm_yday + 1 - iso_wday + 10) / 7;
  if (week < 1) week = 52;
  if (week > 52) week = 52 + (week > 53 ? 1 : week - 52);
  snprintf(s_doy, sizeof(s_doy), "D.%03d/W%02d", now->tm_yday + 1, week);

  layer_mark_dirty(s_mod[MOD_TIME]);
  layer_mark_dirty(s_mod[MOD_DATE]);
}
