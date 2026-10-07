#include "shapes.h"

void shapes_corner(GContext *ctx, int x, int y, int dx, int dy, GColor color) {
  // a 3x3 block on the corner, a 3x2 block beside it along the edge and a
  // 1px tick further along the other edge, mirrored by (dx, dy) = (+-1, +-1)
  graphics_context_set_fill_color(ctx, color);
  int bx = dx > 0 ? x : x - 2, by = dy > 0 ? y : y - 2;
  graphics_fill_rect(ctx, GRect(bx, by, 3, 3), 0, GCornerNone);
  int sx = dx > 0 ? x + 4 : x - 6, sy = dy > 0 ? y : y - 1;
  graphics_fill_rect(ctx, GRect(sx, sy, 3, 2), 0, GCornerNone);
  int ty = dy > 0 ? y + 5 : y - 7;
  graphics_fill_rect(ctx, GRect(x, ty, 1, 3), 0, GCornerNone);
}

void shapes_ruler(GContext *ctx, GRect b, int pos, GColor ticks, GColor marker) {
  int base = b.origin.y + b.size.h - 1;
  graphics_context_set_fill_color(ctx, ticks);
  graphics_fill_rect(ctx, GRect(b.origin.x, base, b.size.w, 1), 0, GCornerNone);
  for (int i = 0; i <= 12; i++) {
    int x = b.origin.x + i * (b.size.w - 1) / 12;
    int h = (i % 3 == 0) ? 4 : 2;
    graphics_fill_rect(ctx, GRect(x, base - h, 1, h), 0, GCornerNone);
  }
  // marker: a 7px wide triangle on a stem that stands on the baseline
  int mx = b.origin.x + pos * (b.size.w - 1) / 1000;
  int top = base - 6;
  graphics_context_set_fill_color(ctx, marker);
  for (int r = 0; r < 4; r++) {
    graphics_fill_rect(ctx, GRect(mx - 3 + r, top + r, 7 - 2 * r, 1), 0, GCornerNone);
  }
  graphics_fill_rect(ctx, GRect(mx, top + 4, 1, 3), 0, GCornerNone);
}

void shapes_checker(GContext *ctx, GRect b, int cell, int phase, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  for (int j = 0; j * cell < b.size.h; j++) {
    for (int i = 0; i * cell < b.size.w; i++) {
      if (((i + j + phase) & 1) == 1) continue;
      graphics_fill_rect(ctx, GRect(b.origin.x + i * cell, b.origin.y + j * cell, cell, cell), 0,
                         GCornerNone);
    }
  }
}

void shapes_wedge(GContext *ctx, int x, int y, int n, GColor color) {
  // right triangle with its right angle at the bottom right of an n x n box
  graphics_context_set_fill_color(ctx, color);
  for (int r = 0; r < n; r++) {
    graphics_fill_rect(ctx, GRect(x + n - 1 - r, y + r, r + 1, 1), 0, GCornerNone);
  }
}

void shapes_plus(GContext *ctx, GPoint c, int r, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  graphics_fill_rect(ctx, GRect(c.x - r, c.y, 2 * r + 1, 1), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(c.x, c.y - r, 1, 2 * r + 1), 0, GCornerNone);
}

void shapes_cross(GContext *ctx, GPoint c, int r, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  for (int i = -r; i <= r; i++) {
    graphics_fill_rect(ctx, GRect(c.x + i, c.y + i, 1, 1), 0, GCornerNone);
    graphics_fill_rect(ctx, GRect(c.x + i, c.y - i, 1, 1), 0, GCornerNone);
  }
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
