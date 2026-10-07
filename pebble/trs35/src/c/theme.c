#include "theme.h"
#include "state.h"

// Colors come from tools/palette_map.py (nearest Pebble color by Lab
// delta-E), nudged toward the more saturated neighbor where the Pebble Time
// screen would otherwise look washed out. See README "색 매핑".
static const Theme s_themes[THEME_COUNT] = {
  // 0 ACID: black field, lime / cyan / blue blocks
  {
    .bg = {GColorBlackARGB8}, .fg = {GColorWhiteARGB8}, .muted = {GColorLightGrayARGB8},
    .a1 = {GColorSpringBudARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorCyanARGB8}, .a2_ink = {GColorBlackARGB8},
    .a3 = {GColorBlueMoonARGB8}, .a3_ink = {GColorWhiteARGB8},
    .panel = {GColorWhiteARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorOrangeARGB8}, .alert_ink = {GColorBlackARGB8},
  },
  // 1 SIGNAL: white paper, black ink, blue + lime accents
  {
    .bg = {GColorWhiteARGB8}, .fg = {GColorBlackARGB8}, .muted = {GColorDarkGrayARGB8},
    .a1 = {GColorSpringBudARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorBlueARGB8}, .a2_ink = {GColorWhiteARGB8},
    .a3 = {GColorBlackARGB8}, .a3_ink = {GColorWhiteARGB8},
    .panel = {GColorLightGrayARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorRedARGB8}, .alert_ink = {GColorWhiteARGB8},
  },
  // 2 CRYO: deep blue field, cyan and white
  {
    .bg = {GColorOxfordBlueARGB8}, .fg = {GColorWhiteARGB8}, .muted = {GColorPictonBlueARGB8},
    .a1 = {GColorCyanARGB8}, .a1_ink = {GColorOxfordBlueARGB8},
    .a2 = {GColorWhiteARGB8}, .a2_ink = {GColorOxfordBlueARGB8},
    .a3 = {GColorBlueMoonARGB8}, .a3_ink = {GColorWhiteARGB8},
    .panel = {GColorElectricBlueARGB8}, .panel_ink = {GColorOxfordBlueARGB8},
    .alert = {GColorBrilliantRoseARGB8}, .alert_ink = {GColorBlackARGB8},
  },
  // 3 MONO: greys with a single lime accent
  {
    .bg = {GColorBlackARGB8}, .fg = {GColorWhiteARGB8}, .muted = {GColorDarkGrayARGB8},
    .a1 = {GColorSpringBudARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorLightGrayARGB8}, .a2_ink = {GColorBlackARGB8},
    .a3 = {GColorDarkGrayARGB8}, .a3_ink = {GColorWhiteARGB8},
    .panel = {GColorWhiteARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorWhiteARGB8}, .alert_ink = {GColorBlackARGB8},
  },
  // 4 FLARE: black field, hot orange / rose / lime
  {
    .bg = {GColorBlackARGB8}, .fg = {GColorWhiteARGB8}, .muted = {GColorLightGrayARGB8},
    .a1 = {GColorOrangeARGB8}, .a1_ink = {GColorBlackARGB8},
    .a2 = {GColorBrilliantRoseARGB8}, .a2_ink = {GColorBlackARGB8},
    .a3 = {GColorSpringBudARGB8}, .a3_ink = {GColorBlackARGB8},
    .panel = {GColorWhiteARGB8}, .panel_ink = {GColorBlackARGB8},
    .alert = {GColorCyanARGB8}, .alert_ink = {GColorBlackARGB8},
  },
};

const Theme *theme_get(void) {
  return &s_themes[g_settings.theme < THEME_COUNT ? g_settings.theme : 0];
}
