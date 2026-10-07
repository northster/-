#include "theme.h"
#include "state.h"

// The palette is fixed: green, white and black, plus a strong red for the
// checker. The presets only move the three colors between the field, the
// weather block and the steps panel (3! = 6 arrangements). Text takes black
// on green / white and white on black; small labels and the ruler use the
// matching grey.
#define C(x) {GColor##x##ARGB8}
#define ON_LIGHT C(Black), C(DarkGray)
#define ON_DARK C(White), C(LightGray)
#define GREEN_BLK C(Green), C(Black)
#define WHITE_BLK C(White), C(Black)
#define BLACK_BLK C(Black), C(White)
#define RED C(Red)

// field, fg, muted, block, block_ink, panel, panel_ink, accent
static const Theme s_themes[THEME_COUNT] = {
  { C(Green), ON_LIGHT, BLACK_BLK, WHITE_BLK, RED },  // 0 green field, black block, white panel
  { C(Green), ON_LIGHT, WHITE_BLK, BLACK_BLK, RED },  // 1 green field, white block, black panel
  { C(White), ON_LIGHT, BLACK_BLK, GREEN_BLK, RED },  // 2 white field, black block, green panel
  { C(White), ON_LIGHT, GREEN_BLK, BLACK_BLK, RED },  // 3 white field, green block, black panel
  { C(Black), ON_DARK, GREEN_BLK, WHITE_BLK, RED },   // 4 black field, green block, white panel
  { C(Black), ON_DARK, WHITE_BLK, GREEN_BLK, RED },   // 5 black field, white block, green panel
};

const Theme *theme_get(void) {
  return &s_themes[g_settings.theme < THEME_COUNT ? g_settings.theme : 0];
}
