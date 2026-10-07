#include "theme.h"
#include "state.h"

// Pebble palette colors (see tools/palette_map.py and README "색 매핑").
// Each theme uses its field, black/white blocks and one spot color.
#define C(x) {GColor##x##ARGB8}
static const Theme s_themes[THEME_COUNT] = {
  // 0 LIME: acid-lime field, black and white blocks, blue spot (the mock-up)
  { C(SpringBud), C(Black), C(DarkGray), C(Black), C(White), C(White), C(Black), C(BlueMoon) },
  // 1 MINT: pale mint field
  { C(Celeste), C(Black), C(DarkGray), C(Black), C(White), C(White), C(Black), C(BlueMoon) },
  // 2 ACID: black field, lime block
  { C(Black), C(White), C(LightGray), C(SpringBud), C(Black), C(White), C(Black), C(ElectricBlue) },
  // 3 VIOLET: black field, violet block, lime spot
  { C(Black), C(White), C(LightGray), C(LavenderIndigo), C(Black), C(White), C(Black), C(SpringBud) },
  // 4 SIGNAL: white paper, lime panel
  { C(White), C(Black), C(DarkGray), C(Black), C(White), C(SpringBud), C(Black), C(BlueMoon) },
  // 5 NULL: hot pink field, white panel, black spot
  { C(Folly), C(Black), C(White), C(Black), C(White), C(White), C(Black), C(Black) },
  // 6 FLARE: ultramarine field, lime panel, pink spot
  { C(ElectricUltramarine), C(White), C(Celeste), C(Black), C(White), C(SpringBud), C(Black), C(Folly) },
  // 7 GREEN: LIME on pure green, the most saturated field the Pebble Time
  // panel can show (SpringBud reads pale on the reflective screen)
  { C(Green), C(Black), C(DarkGray), C(Black), C(White), C(White), C(Black), C(BlueMoon) },
};

const Theme *theme_get(void) {
  return &s_themes[g_settings.theme < THEME_COUNT ? g_settings.theme : 0];
}
