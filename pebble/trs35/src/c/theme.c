#include "theme.h"
#include "state.h"

// Colors were sampled from the reference images and mapped with
// tools/palette_map.py (nearest Pebble color by Lab delta-E); where two
// candidates were about equally close the more saturated one won, since the
// Pebble Time screen washes colors out. See README "색 매핑".
static const Theme s_themes[THEME_COUNT] = {
  // 0 ACID: black field, grey tiles, lime / cyan / blue blocks
  {
    .bg = {GColorBlackARGB8}, .fg = {GColorWhiteARGB8}, .muted = {GColorLightGrayARGB8},
    .tile = {GColorDarkGrayARGB8},
    .a1 = {GColorSpringBudARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorElectricBlueARGB8}, .a2_ink = {GColorBlackARGB8},
    .a3 = {GColorBlueMoonARGB8}, .a3_ink = {GColorWhiteARGB8},
    .panel = {GColorWhiteARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorOrangeARGB8}, .alert_ink = {GColorBlackARGB8},
  },
  // 1 VIOLET: black / violet / lime / white
  {
    .bg = {GColorBlackARGB8}, .fg = {GColorWhiteARGB8}, .muted = {GColorLightGrayARGB8},
    .tile = {GColorIndigoARGB8},
    .a1 = {GColorSpringBudARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorWhiteARGB8}, .a2_ink = {GColorBlackARGB8},
    .a3 = {GColorLavenderIndigoARGB8}, .a3_ink = {GColorBlackARGB8},
    .panel = {GColorWhiteARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorFollyARGB8}, .alert_ink = {GColorWhiteARGB8},
  },
  // 2 MINT: pale mint paper, black blocks, lime and cyan
  {
    .bg = {GColorCelesteARGB8}, .fg = {GColorBlackARGB8}, .muted = {GColorDarkGrayARGB8},
    .tile = {GColorElectricBlueARGB8},
    .a1 = {GColorSpringBudARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorBlackARGB8}, .a2_ink = {GColorWhiteARGB8},
    .a3 = {GColorBlackARGB8}, .a3_ink = {GColorWhiteARGB8},
    .panel = {GColorWhiteARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorFollyARGB8}, .alert_ink = {GColorWhiteARGB8},
  },
  // 3 SIGNAL: white paper, black bars, lime
  {
    .bg = {GColorWhiteARGB8}, .fg = {GColorBlackARGB8}, .muted = {GColorDarkGrayARGB8},
    .tile = {GColorLightGrayARGB8},
    .a1 = {GColorSpringBudARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorSpringBudARGB8}, .a2_ink = {GColorBlackARGB8},
    .a3 = {GColorBlackARGB8}, .a3_ink = {GColorWhiteARGB8},
    .panel = {GColorSpringBudARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorRedARGB8}, .alert_ink = {GColorWhiteARGB8},
  },
  // 4 NULL: hot pink and white, black ink
  {
    .bg = {GColorFollyARGB8}, .fg = {GColorBlackARGB8}, .muted = {GColorBlackARGB8},
    .tile = {GColorBrilliantRoseARGB8},
    .a1 = {GColorBlackARGB8}, .a1_ink = {GColorWhiteARGB8},
    .a2 = {GColorFollyARGB8}, .a2_ink = {GColorBlackARGB8},
    .a3 = {GColorBlackARGB8}, .a3_ink = {GColorWhiteARGB8},
    .panel = {GColorWhiteARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorSpringBudARGB8}, .alert_ink = {GColorBlackARGB8},
  },
  // 5 FLARE: ultramarine field, lime, coral red, pink
  {
    .bg = {GColorElectricUltramarineARGB8}, .fg = {GColorWhiteARGB8}, .muted = {GColorCelesteARGB8},
    .tile = {GColorIndigoARGB8},
    .a1 = {GColorSpringBudARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorSunsetOrangeARGB8}, .a2_ink = {GColorBlackARGB8},
    .a3 = {GColorFollyARGB8}, .a3_ink = {GColorBlackARGB8},
    .panel = {GColorSpringBudARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorYellowARGB8}, .alert_ink = {GColorBlackARGB8},
  },
};

const Theme *theme_get(void) {
  return &s_themes[g_settings.theme < THEME_COUNT ? g_settings.theme : 0];
}
