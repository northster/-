#pragma once
#include <pebble.h>

// Everything the face shows lives here; drawing code only reads it.

typedef enum { CLOCK_AUTO = 0, CLOCK_12H = 1, CLOCK_24H = 2 } ClockMode;
typedef enum { UNIT_C = 0, UNIT_F = 1 } TempUnit;

#define THEME_COUNT 5
#define LAYOUT_COUNT 3

typedef struct {
  uint8_t theme;       // index into the theme table (theme.c)
  uint8_t layout;      // layout preset (layout.c)
  uint8_t clock_mode;  // ClockMode
  uint8_t temp_unit;   // TempUnit
  bool shake_anim;     // play the shuffle animation on a wrist flick
  bool bt_vibe;        // vibrate when the phone disconnects
} Settings;

#define TEMP_NONE INT16_MIN

typedef struct {
  int16_t temp_c10;    // tenths of a degree Celsius, TEMP_NONE when unknown
  int16_t code;        // WMO weather code, -1 when unknown
  bool is_day;
  time_t updated;      // when the phone last sent weather, 0 = never
} Weather;

extern Settings g_settings;
extern Weather g_weather;
extern int g_steps;
extern BatteryChargeState g_battery;
extern bool g_bt_connected;

void state_load(void);
void state_save_settings(void);
void state_save_weather(void);

// Applies an AppMessage from the phone (Clay settings or weather).
// Returns true when anything visible changed.
bool state_apply_message(DictionaryIterator *iter, bool *layout_changed);

void state_update_steps(void);

// Writes "23°" / "-4°" / "--°" in the user's unit.
void state_format_temp(char *buf, size_t len);

bool state_weather_stale(void);
