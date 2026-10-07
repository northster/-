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

void shapes_quarter_tiles(GContext *ctx, GRect box, int tile, int phase, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  for (int j = 0; j * tile < box.size.h; j++) {
    for (int i = 0; i * tile < box.size.w; i++) {
      // pairs of cells face each other, so neighbours form leaves and full discs
      int corner = ((i & 1) + 2 * (j & 1) + phase + ((i / 2 + j / 2) % 2) * 2) & 3;
      int x = box.origin.x + i * tile, y = box.origin.y + j * tile;
      // corners: 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left
      int cx = (corner == 1 || corner == 2) ? x + tile : x;
      int cy = (corner >= 2) ? y + tile : y;
      // fill_radial: clockwise from 12 o'clock; pick the quadrant inside the cell
      int start = (corner == 0) ? 90 : (corner == 1) ? 180 : (corner == 2) ? 270 : 0;
      GRect circle = GRect(cx - tile, cy - tile, 2 * tile, 2 * tile);
      graphics_fill_radial(ctx, circle, GOvalScaleModeFitCircle, tile,
                           DEG_TO_TRIGANGLE(start), DEG_TO_TRIGANGLE(start + 90));
    }
  }
}

void shapes_plus(GContext *ctx, GPoint c, int r, GColor color) {
  graphics_context_set_stroke_color(ctx, color);
  graphics_draw_line(ctx, GPoint(c.x - r, c.y), GPoint(c.x + r, c.y));
  graphics_draw_line(ctx, GPoint(c.x, c.y - r), GPoint(c.x, c.y + r));
}

void shapes_brackets(GContext *ctx, GRect b, int len, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  int x0 = b.origin.x, y0 = b.origin.y, x1 = x0 + b.size.w - 1, y1 = y0 + b.size.h - 1;
  graphics_fill_rect(ctx, GRect(x0, y0, len, 1), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x0, y0, 1, len), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x1 - len + 1, y0, len, 1), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x1, y0, 1, len), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x0, y1, len, 1), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x0, y1 - len + 1, 1, len), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x1 - len + 1, y1, len, 1), 0, GCornerNone);
  graphics_fill_rect(ctx, GRect(x1, y1 - len + 1, 1, len), 0, GCornerNone);
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
  // marker: a small triangle standing on the baseline, pointing down
  int mx = b.origin.x + pos * (b.size.w - 1) / 1000;
  graphics_context_set_stroke_color(ctx, marker);
  for (int r = 0; r < 4; r++) {
    graphics_draw_line(ctx, GPoint(mx - 3 + r, base - 6 + r), GPoint(mx + 3 - r, base - 6 + r));
  }
  graphics_context_set_fill_color(ctx, marker);
  graphics_fill_rect(ctx, GRect(mx, base - 2, 1, 3), 0, GCornerNone);
}

void shapes_checker(GContext *ctx, GRect b, int cell, int phase, GColor color) {
  graphics_context_set_fill_color(ctx, color);
  for (int j = 0; j * cell < b.size.h; j++) {
    for (int i = 0; i * cell < b.size.w; i++) {
      if (((i + j + phase) & 1) == 0) continue;
      int w = cell, h = cell;
      if ((i + 1) * cell > b.size.w) w = b.size.w - i * cell;
      if ((j + 1) * cell > b.size.h) h = b.size.h - j * cell;
      graphics_fill_rect(ctx, GRect(b.origin.x + i * cell, b.origin.y + j * cell, w, h), 0, GCornerNone);
    }
  }
}

void shapes_stripes(GContext *ctx, GRect b, int step, int width, int phase, GColor color) {
  // one run per row: every row is the row above shifted by one pixel
  graphics_context_set_fill_color(ctx, color);
  for (int y = 0; y < b.size.h; y++) {
    int off = ((phase + y) % step + step) % step;
    for (int x = -off; x < b.size.w; x += step) {
      int x0 = x < 0 ? 0 : x;
      int x1 = x + width > b.size.w ? b.size.w : x + width;
      if (x1 > x0) graphics_fill_rect(ctx, GRect(b.origin.x + x0, b.origin.y + y, x1 - x0, 1), 0, GCornerNone);
    }
  }
}

void shapes_chevrons(GContext *ctx, GRect b, int n, int phase, GColor color) {
  graphics_context_set_stroke_color(ctx, color);
  graphics_context_set_stroke_width(ctx, 2);
  int pitch = b.size.w / n;
  int half = b.size.h / 2;
  for (int i = 0; i < n; i++) {
    int x = b.origin.x + i * pitch + (phase % pitch);
    if (x + half >= b.origin.x + b.size.w) x -= b.size.w;
    if (x < b.origin.x) continue;
    graphics_draw_line(ctx, GPoint(x, b.origin.y + 1), GPoint(x + half - 1, b.origin.y + half));
    graphics_draw_line(ctx, GPoint(x + half - 1, b.origin.y + half), GPoint(x, b.origin.y + b.size.h - 2));
  }
  graphics_context_set_stroke_width(ctx, 1);
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
