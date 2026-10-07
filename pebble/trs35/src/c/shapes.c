#include "shapes.h"

void shapes_half_disc(GContext *ctx, GPoint center, int r, int rot_deg, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  GRect box = GRect(center.x - r, center.y - r, 2 * r + 1, 2 * r + 1);
  // fill_radial angles are clockwise from 12 o'clock; -90..90 is the upper half
  int32_t a0 = DEG_TO_TRIGANGLE(rot_deg - 90);
  int32_t a1 = DEG_TO_TRIGANGLE(rot_deg + 90);
  graphics_fill_radial(ctx, box, GOvalScaleModeFitCircle, r, a0, a1);
}

void shapes_triangle_row(GContext *ctx, GRect box, int size, int gap, int phase, GColor color) {
  graphics_context_set_stroke_color(ctx, color);
  int pitch = size + gap;
  if (pitch <= 0) return;
  int start = box.origin.x - pitch + (phase % pitch + pitch) % pitch;
  int right = box.origin.x + box.size.w;
  int half = size / 2;
  for (int x0 = start; x0 < right; x0 += pitch) {
    // triangle pointing right: column i is a vertical line of shrinking height
    for (int i = 0; i < size; i++) {
      int x = x0 + i;
      if (x < box.origin.x || x >= right) continue;
      int span = half - (i * half) / size;  // half-height of this column
      int y0 = box.origin.y + half - span;
      int y1 = box.origin.y + half + span;
      if (y1 >= box.origin.y + box.size.h) y1 = box.origin.y + box.size.h - 1;
      graphics_draw_line(ctx, GPoint(x, y0), GPoint(x, y1));
    }
  }
}

void shapes_rings(GContext *ctx, GPoint center, int r_min, int r_max, int step, GColor color) {
  graphics_context_set_stroke_color(ctx, color);
  graphics_context_set_stroke_width(ctx, 1);
  for (int r = r_min; r <= r_max; r += step) {
    graphics_draw_circle(ctx, center, r);
  }
}

void shapes_hatch(GContext *ctx, GRect box, int step, GColor color) {
  graphics_context_set_stroke_color(ctx, color);
  int w = box.size.w, h = box.size.h;
  for (int d = -h; d < w; d += step) {
    // line from (d, h) to (d + h, 0), clipped to the box
    int x0 = d, y0 = h - 1, x1 = d + h - 1, y1 = 0;
    if (x0 < 0) { y0 += x0; x0 = 0; }
    if (x1 >= w) { y1 += x1 - (w - 1); x1 = w - 1; }
    if (y0 < y1) continue;
    graphics_draw_line(ctx, GPoint(box.origin.x + x0, box.origin.y + y0),
                       GPoint(box.origin.x + x1, box.origin.y + y1));
  }
}

void shapes_barcode(GContext *ctx, GRect box, uint32_t seed, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  uint32_t s = seed * 2654435761u + 1;
  int x = box.origin.x;
  int right = box.origin.x + box.size.w;
  while (x < right) {
    s = s * 1103515245u + 12345u;
    int bar = 1 + ((s >> 16) % 3);
    int space = 1 + ((s >> 20) % 2);
    if (x + bar > right) bar = right - x;
    graphics_fill_rect(ctx, GRect(x, box.origin.y, bar, box.size.h), 0, GCornerNone);
    x += bar + space;
  }
}

void shapes_crosshair(GContext *ctx, GPoint c, int r, GColor color) {
  graphics_context_set_stroke_color(ctx, color);
  graphics_draw_circle(ctx, c, r);
  graphics_draw_line(ctx, GPoint(c.x - r - 2, c.y), GPoint(c.x + r + 2, c.y));
  graphics_draw_line(ctx, GPoint(c.x, c.y - r - 2), GPoint(c.x, c.y + r + 2));
}

/* ---- weather glyphs ---------------------------------------------------- */

static void sun(GContext *ctx, GPoint c, int r, GColor fg) {
  graphics_context_set_fill_color(ctx, fg);
  graphics_fill_circle(ctx, c, r);
  graphics_context_set_stroke_color(ctx, fg);
  graphics_context_set_stroke_width(ctx, 1);
  int r0 = r + 2, r1 = r + 2 + r / 2 + 1;
  for (int i = 0; i < 8; i++) {
    int32_t a = TRIG_MAX_ANGLE * i / 8;
    int sx = sin_lookup(a), cy = cos_lookup(a);
    graphics_draw_line(ctx,
        GPoint(c.x + sx * r0 / TRIG_MAX_RATIO, c.y - cy * r0 / TRIG_MAX_RATIO),
        GPoint(c.x + sx * r1 / TRIG_MAX_RATIO, c.y - cy * r1 / TRIG_MAX_RATIO));
  }
}

static void moon(GContext *ctx, GPoint c, int r, GColor fg, GColor bg) {
  graphics_context_set_fill_color(ctx, fg);
  graphics_fill_circle(ctx, c, r);
  graphics_context_set_fill_color(ctx, bg);
  graphics_fill_circle(ctx, GPoint(c.x + r / 2 + 1, c.y - r / 2), r - 1);
}

// Flat-bottomed cloud: a base slab plus two humps.
static void cloud(GContext *ctx, GRect b, GColor fg) {
  graphics_context_set_fill_color(ctx, fg);
  int h = b.size.h, w = b.size.w;
  int base_h = h / 2;
  graphics_fill_rect(ctx, GRect(b.origin.x, b.origin.y + h - base_h, w, base_h), base_h / 2,
                     GCornersAll);
  graphics_fill_circle(ctx, GPoint(b.origin.x + w * 3 / 10, b.origin.y + h - base_h), h * 3 / 10);
  graphics_fill_circle(ctx, GPoint(b.origin.x + w * 6 / 10, b.origin.y + h * 4 / 10), h * 4 / 10);
}

void shapes_weather(GContext *ctx, GPoint o, int s, int code, bool is_day, GColor fg, GColor bg) {
  graphics_context_set_stroke_width(ctx, 1);
  GPoint mid = GPoint(o.x + s / 2, o.y + s / 2);
  GRect cl = GRect(o.x + 1, o.y + s / 5, s - 2, s * 2 / 5 + 2);  // cloud box, upper part

  if (code < 0) {
    // unknown: dashed square with a dot
    graphics_context_set_stroke_color(ctx, fg);
    for (int i = 0; i < s; i += 4) {
      graphics_draw_line(ctx, GPoint(o.x + i, o.y), GPoint(o.x + i + 1, o.y));
      graphics_draw_line(ctx, GPoint(o.x + i, o.y + s - 1), GPoint(o.x + i + 1, o.y + s - 1));
      graphics_draw_line(ctx, GPoint(o.x, o.y + i), GPoint(o.x, o.y + i + 1));
      graphics_draw_line(ctx, GPoint(o.x + s - 1, o.y + i), GPoint(o.x + s - 1, o.y + i + 1));
    }
    graphics_context_set_fill_color(ctx, fg);
    graphics_fill_rect(ctx, GRect(mid.x - 1, mid.y - 1, 3, 3), 0, GCornerNone);
    return;
  }

  if (code == 0) {
    if (is_day) sun(ctx, mid, s / 5, fg);
    else moon(ctx, mid, s / 3, fg, bg);
    return;
  }

  if (code <= 3) {
    // partly cloudy: small sun/moon behind, cloud in front-bottom
    GPoint c = GPoint(o.x + s * 2 / 3, o.y + s / 3);
    if (is_day) sun(ctx, c, s / 7, fg);
    else moon(ctx, c, s / 5, fg, bg);
    GRect low = GRect(o.x, o.y + s / 2 - 2, s * 3 / 4, s * 2 / 5);
    // knock out an outline so the cloud reads in front of the sun
    graphics_context_set_fill_color(ctx, bg);
    graphics_fill_rect(ctx, grect_inset(low, GEdgeInsets(-1)), 3, GCornersAll);
    cloud(ctx, low, fg);
    return;
  }

  if (code <= 48) {
    // fog: stacked bars of different length
    graphics_context_set_fill_color(ctx, fg);
    for (int i = 0; i < 4; i++) {
      int inset = (i % 2) ? s / 5 : 0;
      graphics_fill_rect(ctx, GRect(o.x + inset, o.y + s / 5 + i * s / 6, s - inset - (i == 3 ? s / 3 : 0), 2),
                         0, GCornerNone);
    }
    return;
  }

  cloud(ctx, cl, fg);
  int y0 = cl.origin.y + cl.size.h + 2;
  graphics_context_set_stroke_color(ctx, fg);
  graphics_context_set_fill_color(ctx, fg);

  bool snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
  bool thunder = code >= 95;
  if (thunder) {
    // zigzag bolt
    int x = mid.x;
    graphics_context_set_stroke_width(ctx, 2);
    graphics_draw_line(ctx, GPoint(x + 2, y0), GPoint(x - 2, y0 + s / 6));
    graphics_draw_line(ctx, GPoint(x - 2, y0 + s / 6), GPoint(x + 2, y0 + s / 6));
    graphics_draw_line(ctx, GPoint(x + 2, y0 + s / 6), GPoint(x - 2, y0 + s / 3));
    graphics_context_set_stroke_width(ctx, 1);
  } else if (snow) {
    for (int i = 0; i < 3; i++) {
      int x = o.x + s / 4 + i * s / 4;
      int y = y0 + 2 + (i % 2) * 4;
      graphics_fill_rect(ctx, GRect(x - 1, y - 1, 3, 3), 0, GCornerNone);
    }
  } else {
    // rain / drizzle / showers: slashes, heavier rain gets more
    int n = (code >= 63 && code <= 67) || code >= 81 ? 4 : 3;
    for (int i = 0; i < n; i++) {
      int x = o.x + 3 + i * (s - 6) / n;
      graphics_draw_line(ctx, GPoint(x + 3, y0), GPoint(x, y0 + s / 4));
    }
  }
}

void shapes_bt(GContext *ctx, GPoint o, int h, GColor color) {
  graphics_context_set_stroke_color(ctx, color);
  graphics_context_set_stroke_width(ctx, 1);
  int w = h / 2;
  int cx = o.x + w / 2;
  GPoint top = GPoint(cx, o.y), bot = GPoint(cx, o.y + h - 1);
  graphics_draw_line(ctx, top, bot);
  graphics_draw_line(ctx, top, GPoint(o.x + w, o.y + h / 4));
  graphics_draw_line(ctx, GPoint(o.x + w, o.y + h / 4), GPoint(o.x, o.y + h * 3 / 4));
  graphics_draw_line(ctx, bot, GPoint(o.x + w, o.y + h * 3 / 4));
  graphics_draw_line(ctx, GPoint(o.x + w, o.y + h * 3 / 4), GPoint(o.x, o.y + h / 4));
}
