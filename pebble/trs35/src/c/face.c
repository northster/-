#include "face.h"
#include "shapes.h"
#include "state.h"
#include "theme.h"

#define STEP_GOAL 10000

static Layer *s_root;
static Layer *s_mod[MOD_COUNT];

static GFont s_font_time;
static GFont s_font_med;
static GFont s_font_code;

static char s_hh[4], s_mm[4], s_time[8];
static char s_ampm[4];
static char s_wday[4], s_date[8];

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
  snprintf(buf, sizeof(buf), g_battery.is_charging ? "+%d%%" : "%d%%", pct);
  text(ctx, buf, s_font_code, GRect(x0 - 30, 2, 28, 10), GTextAlignmentRight, low ? th->alert : th->muted);
}

static void header_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  GRect b = local(layer);
  fill(ctx, b, th->bg);

  // accent tab with the watch's name; shrinks during the shake clip
  int full = 66;
  int bw = lerp(full, 22, s_anim_p);
  fill(ctx, GRect(0, 0, bw, b.size.h), th->a1);
  shapes_triangle_row(ctx, GRect(bw, 0, 8, b.size.h), b.size.h, 200, 0, th->a1);
  if (bw > 56) {
    text(ctx, state_watch_name(), s_font_code, GRect(3, 2, bw - 3, 12), GTextAlignmentLeft, th->a1_ink);
  }

  if (g_bt_connected) {
    shapes_bt(ctx, GPoint(80, 3), 10, th->muted);
  } else {
    // phone lost: alert block takes the place of the rune and battery %
    GRect alert = GRect(76, 1, 38, 14);
    fill(ctx, alert, th->alert);
    text(ctx, "LINK-X", s_font_code, GRect(alert.origin.x + 2, alert.origin.y + 1, alert.size.w - 2, 12),
         GTextAlignmentLeft, th->alert_ink);
  }
  draw_battery(ctx, b, th, g_bt_connected);
}

/* ---- time --------------------------------------------------------------- */

// Chakra Petch Bold 46: the digits start 14px below the text origin and are
// 32px tall. Everything in the time box is placed from these two numbers.
#define DIGIT_TOP 14
#define DIGIT_H 32
#define LABEL_GAP 6   // digits to the label line
#define LABEL_H 8

static void digits(GContext *ctx, const char *s, GRect box, int top, const Theme *th) {
  text(ctx, s, s_font_time, GRect(box.origin.x, top - DIGIT_TOP, box.size.w, DIGIT_H + DIGIT_TOP + 8),
       GTextAlignmentCenter, th->fg);
}

static void time_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  const Layout *lay = layout_get(g_settings.layout);
  GRect b = local(layer);
  fill(ctx, b, th->bg);

  // glitch: a couple of frames jump sideways
  int jx = 0;
  if (s_anim_t >= 0 && (s_anim_frame % 4) == 1) jx = (s_anim_frame & 4) ? 4 : -4;

  char pos[24];
  state_format_position(pos, sizeof(pos));

  if (lay->time_stacked) {
    // hours over minutes with an accent rule between, labels at the bottom
    int block = 2 * DIGIT_H + 2 * LABEL_GAP + 2 + LABEL_GAP + LABEL_H;
    int top = (b.size.h - block) / 2;
    int rule = top + DIGIT_H + LABEL_GAP;
    int mm_top = rule + 2 + LABEL_GAP;
    int label_y = mm_top + DIGIT_H + LABEL_GAP - 2;
    bool right_edge = b.origin.x == 0;  // mirrored layout: accent slab on the outer edge
    int slab_x = right_edge ? 2 : b.size.w - 6;
    int inner_x = right_edge ? 8 : 0;
    int inner_w = b.size.w - 8;
    fill(ctx, GRect(slab_x, top, 4, label_y + LABEL_H - top), th->a2);
    digits(ctx, s_hh, GRect(inner_x + jx, 0, inner_w, 0), top, th);
    fill(ctx, GRect(inner_x + 10, rule, inner_w - 20, 2), th->a1);
    digits(ctx, s_mm, GRect(inner_x - jx, 0, inner_w, 0), mm_top, th);
    text(ctx, s_ampm, s_font_code, GRect(inner_x + 10, label_y - 1, inner_w - 20, 10), GTextAlignmentRight,
         th->muted);
    shapes_plus(ctx, GPoint(inner_x + 13, label_y + 3), 3, th->fg);
  } else {
    int block = DIGIT_H + LABEL_GAP + LABEL_H;
    int top = (b.size.h - block) / 2;
    int label_y = top + DIGIT_H + LABEL_GAP;
    fill(ctx, GRect(0, top, 3, block), th->a2);
    digits(ctx, s_time, GRect(jx, 0, b.size.w, 0), top, th);
    // where the weather is from (phone position), else the firmware version
    if (pos[0] == '\0') {
      WatchInfoVersion v = watch_info_get_firmware_version();
      snprintf(pos, sizeof(pos), "FW %d.%d.%d", v.major, v.minor, v.patch);
    }
    text(ctx, pos, s_font_code, GRect(8, label_y - 1, 100, 10), GTextAlignmentLeft, th->muted);
    text(ctx, s_ampm, s_font_code, GRect(b.size.w - 40, label_y - 1, 36, 10), GTextAlignmentRight, th->muted);
    shapes_plus(ctx, GPoint(b.size.w - 6, top + 3), 3, th->fg);
  }
}

/* ---- date --------------------------------------------------------------- */

static void date_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  GRect b = local(layer);
  fill(ctx, b, th->bg);

  int pw = 48;
  int phase = s_anim_t >= 0 ? s_anim_t * 40 / 1000 : 0;
  // weekday tab with a half-disc cap (spins once during the clip)
  int rot = 90 + (s_anim_t >= 0 ? s_anim_t * 360 / 1000 : 0);
  fill(ctx, GRect(0, 1, pw - 1, 18), th->panel);
  shapes_half_disc(ctx, GPoint(pw - 2, 10), 9, rot, th->panel);
  text(ctx, s_wday, s_font_med, GRect(0, -4, pw - 2, 24), GTextAlignmentCenter, th->panel_ink);
  pw += 8;

  if (b.size.h >= 36) {
    // two rows (narrow column): weekday + triangles, then the date
    shapes_triangle_row(ctx, GRect(pw + 4, 5, b.size.w - pw - 6, 10), 10, 2, phase, th->a1);
    text(ctx, s_date, s_font_med, GRect(0, 16, b.size.w - 4, 24), GTextAlignmentLeft, th->fg);
  } else {
    text(ctx, s_date, s_font_med, GRect(pw + 2, -4, 60, 24), GTextAlignmentLeft, th->fg);
    if (b.size.w >= 120) {
      shapes_triangle_row(ctx, GRect(b.size.w - 24, 5, 22, 10), 10, 2, phase, th->a1);
    }
  }
}

/* ---- weather cell ------------------------------------------------------- */

// Cells come in three shapes: normal (about 72x72), narrow column (50 wide)
// and wide band (full width, under 50 tall).
typedef enum { CELL_NORMAL, CELL_NARROW, CELL_BAND } CellShape;

static CellShape cell_shape(GRect b) {
  if (b.size.h < 50) return CELL_BAND;
  return b.size.w < 60 ? CELL_NARROW : CELL_NORMAL;
}

static void wx_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  GRect b = local(layer);
  bool sw = colors_swapped();
  GColor cell = sw ? th->a1 : th->a3;
  GColor ink = sw ? th->a1_ink : th->a3_ink;
  GColor shape = sw ? th->a3 : th->a2;
  fill(ctx, b, cell);
  CellShape cs = cell_shape(b);
  int phase = s_anim_t >= 0 ? s_anim_frame / 2 : 0;

  char temp[12];
  state_format_temp(temp, sizeof(temp));
  if (cs != CELL_NARROW && g_weather.temp_c10 != TEMP_NONE) {
    strncat(temp, g_settings.temp_unit == UNIT_F ? "F" : "C", sizeof(temp) - strlen(temp) - 1);
  }
  // when the phone last delivered weather
  char upd[16] = "NO DATA";
  if (g_weather.updated) {
    struct tm *t = localtime(&g_weather.updated);
    strftime(upd, sizeof(upd), clock_is_24h_style() ? "UPD %H:%M" : "UPD %I:%M", t);
  }
  const char *label = state_weather_stale() && g_weather.updated ? "WEATHER OLD" : "WEATHER";

  if (cs == CELL_BAND) {
    // [tiles] [icon] [23°C] ............ UPD / 07:30
    int tile = (b.size.h - 6) / 2;
    shapes_quarter_tiles(ctx, GRect(3, 3, 2 * tile, 2 * tile), tile, phase, shape);
    int icon = b.size.h - 10;
    int x = 2 * tile + 8;
    shapes_weather(ctx, GPoint(x, 5), icon, g_weather.code, g_weather.is_day, ink, cell);
    text(ctx, temp, s_font_med, GRect(x + icon + 5, (b.size.h - 24) / 2 - 2, 64, 24), GTextAlignmentLeft, ink);
    char hm[8] = "--:--";
    if (g_weather.updated) strncpy(hm, upd + 4, sizeof(hm) - 1);
    text(ctx, state_weather_stale() ? "OLD" : "UPD", s_font_code, GRect(b.size.w - 36, b.size.h / 2 - 11, 32, 10),
         GTextAlignmentRight, ink);
    text(ctx, hm, s_font_code, GRect(b.size.w - 36, b.size.h / 2 + 1, 32, 10), GTextAlignmentRight, ink);
    return;
  }

  int tile = cs == CELL_NARROW ? 10 : 13;
  text(ctx, label, s_font_code, GRect(4, 3, b.size.w - 6, 10), GTextAlignmentLeft, ink);
  if (cs == CELL_NORMAL) {
    shapes_quarter_tiles(ctx, GRect(b.size.w - 2 * tile, 0, 2 * tile, 2 * tile), tile, phase, shape);
    shapes_weather(ctx, GPoint(4, 15), 24, g_weather.code, g_weather.is_day, ink, cell);
    text(ctx, temp, s_font_med, GRect(4, 36, b.size.w - 6, 24), GTextAlignmentLeft, ink);
    text(ctx, upd, s_font_code, GRect(4, b.size.h - 12, 50, 10), GTextAlignmentLeft, ink);
  } else {
    shapes_quarter_tiles(ctx, GRect(b.size.w - 2 * tile, b.size.h - 2 * tile, 2 * tile, 2 * tile), tile, phase,
                         shape);
    shapes_weather(ctx, GPoint(4, 15), 22, g_weather.code, g_weather.is_day, ink, cell);
    text(ctx, temp, s_font_med, GRect(3, 36, b.size.w - 4, 24), GTextAlignmentLeft, ink);
  }
}

/* ---- steps cell --------------------------------------------------------- */

static void progress_ticks(GContext *ctx, GRect r, int n, int done, GColor ink) {
  int tw = r.size.w / n;
  graphics_context_set_stroke_color(ctx, ink);
  for (int i = 0; i < n; i++) {
    GRect t = GRect(r.origin.x + i * tw, r.origin.y, tw - 2, r.size.h);
    if (i < done) fill(ctx, t, ink);
    else graphics_draw_rect(ctx, t);
  }
}

static void steps_update(Layer *layer, GContext *ctx) {
  const Theme *th = theme_get();
  GRect b = local(layer);
  bool sw = colors_swapped();
  GColor cell = sw ? th->a2 : th->panel;
  GColor ink = sw ? th->a2_ink : th->panel_ink;
  fill(ctx, b, cell);
  CellShape cs = cell_shape(b);

  int pct = g_steps * 100 / STEP_GOAL;
  char pbuf[16];
  char dist[16];
  snprintf(dist, sizeof(dist), "%d.%dKM", g_distance_m / 1000, (g_distance_m % 1000) / 100);
  char num[16];
  if (cs == CELL_NARROW && g_steps >= 10000) snprintf(num, sizeof(num), "%dK", g_steps / 1000);
  else if (cs == CELL_NARROW) snprintf(num, sizeof(num), "%d", g_steps);
  else snprintf(num, sizeof(num), "%05d", g_steps > 99999 ? 99999 : g_steps);

  if (cs == CELL_BAND) {
    // STEPS 08421 [ticks........] / 84%   6.1KM
    text(ctx, "STEPS", s_font_code, GRect(6, 4, 40, 10), GTextAlignmentLeft, ink);
    text(ctx, num, s_font_med, GRect(5, 12, 70, 24), GTextAlignmentLeft, ink);
    progress_ticks(ctx, GRect(76, 10, b.size.w - 80, 8), 10, pct * 10 / 100, ink);
    snprintf(pbuf, sizeof(pbuf), "%d%%", pct > 999 ? 999 : pct);
    text(ctx, pbuf, s_font_code, GRect(76, 24, 40, 10), GTextAlignmentLeft, ink);
    text(ctx, dist, s_font_code, GRect(b.size.w - 44, 24, 40, 10), GTextAlignmentRight, ink);
    return;
  }

  text(ctx, "STEPS", s_font_code, GRect(4, 3, b.size.w - 6, 10), GTextAlignmentLeft, ink);
  text(ctx, num, s_font_med, GRect(3, 12, b.size.w - 4, 24), GTextAlignmentLeft, ink);
  if (cs == CELL_NORMAL) {
    progress_ticks(ctx, GRect(4, 39, b.size.w - 6, 6), 10, pct * 10 / 100, ink);
    snprintf(pbuf, sizeof(pbuf), "%d%%", pct > 999 ? 999 : pct);
    text(ctx, pbuf, s_font_code, GRect(4, 48, 40, 10), GTextAlignmentLeft, ink);
    text(ctx, "GOAL 10K", s_font_code, GRect(4, b.size.h - 12, 50, 10), GTextAlignmentLeft, ink);
    text(ctx, dist, s_font_code, GRect(b.size.w - 40, 48, 36, 10), GTextAlignmentRight, ink);
  } else {
    progress_ticks(ctx, GRect(4, 39, b.size.w - 6, 6), 5, pct * 5 / 100, ink);
    snprintf(pbuf, sizeof(pbuf), "%d%%", pct > 999 ? 999 : pct);
    text(ctx, pbuf, s_font_code, GRect(4, 48, 40, 10), GTextAlignmentLeft, ink);
    text(ctx, dist, s_font_code, GRect(4, 60, 44, 10), GTextAlignmentLeft, ink);
  }
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
  layer_mark_dirty(s_mod[MOD_TIME]);
  layer_mark_dirty(s_mod[MOD_DATE]);
}
