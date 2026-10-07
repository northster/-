#include "state.h"
#include <stdlib.h>

#define PERSIST_SETTINGS 1
#define PERSIST_WEATHER 2

// Weather older than this is drawn as stale (tiny "OLD" tag).
#define WEATHER_STALE_SEC (3 * 60 * 60)

Settings g_settings = {
  .theme = 0,
  .layout = 0,
  .clock_mode = CLOCK_AUTO,
  .temp_unit = UNIT_C,
  .shake_anim = true,
  .bt_vibe = true,
};

Weather g_weather = {
  .temp_c10 = TEMP_NONE,
  .code = -1,
  .is_day = true,
  .updated = 0,
  .lat100 = TEMP_NONE,
  .lon100 = TEMP_NONE,
};

int g_steps = 0;
int g_distance_m = 0;
BatteryChargeState g_battery;
bool g_bt_connected = true;

void state_load(void) {
  if (persist_exists(PERSIST_SETTINGS) &&
      persist_get_size(PERSIST_SETTINGS) == (int)sizeof(Settings)) {
    persist_read_data(PERSIST_SETTINGS, &g_settings, sizeof(Settings));
  }
  if (persist_exists(PERSIST_WEATHER) &&
      persist_get_size(PERSIST_WEATHER) == (int)sizeof(Weather)) {
    persist_read_data(PERSIST_WEATHER, &g_weather, sizeof(Weather));
  }
  if (g_settings.theme >= THEME_COUNT) g_settings.theme = 0;
  if (g_settings.layout >= LAYOUT_COUNT) g_settings.layout = 0;
}

void state_save_settings(void) {
  persist_write_data(PERSIST_SETTINGS, &g_settings, sizeof(Settings));
}

void state_save_weather(void) {
  persist_write_data(PERSIST_WEATHER, &g_weather, sizeof(Weather));
}

// Clay sends selects as strings and toggles as ints; accept both.
static int tuple_int(Tuple *t) {
  if (t->type == TUPLE_CSTRING) return atoi(t->value->cstring);
  switch (t->length) {
    case 1: return t->type == TUPLE_INT ? t->value->int8 : t->value->uint8;
    case 2: return t->type == TUPLE_INT ? t->value->int16 : t->value->uint16;
    default: return t->type == TUPLE_INT ? (int)t->value->int32 : (int)t->value->uint32;
  }
}

static bool read_u8(DictionaryIterator *iter, uint32_t key, uint8_t *out, int max) {
  Tuple *t = dict_find(iter, key);
  if (!t) return false;
  int v = tuple_int(t);
  if (v < 0 || v > max) return false;
  if (*out == v) return false;
  *out = (uint8_t)v;
  return true;
}

static bool read_bool(DictionaryIterator *iter, uint32_t key, bool *out) {
  Tuple *t = dict_find(iter, key);
  if (!t) return false;
  bool v = tuple_int(t) != 0;
  if (*out == v) return false;
  *out = v;
  return true;
}

bool state_apply_message(DictionaryIterator *iter, bool *layout_changed) {
  bool settings_changed = false;
  *layout_changed = false;

  settings_changed |= read_u8(iter, MESSAGE_KEY_THEME, &g_settings.theme, THEME_COUNT - 1);
  if (read_u8(iter, MESSAGE_KEY_LAYOUT, &g_settings.layout, LAYOUT_COUNT - 1)) {
    settings_changed = true;
    *layout_changed = true;
  }
  settings_changed |= read_u8(iter, MESSAGE_KEY_CLOCK_MODE, &g_settings.clock_mode, CLOCK_24H);
  settings_changed |= read_u8(iter, MESSAGE_KEY_TEMP_UNIT, &g_settings.temp_unit, UNIT_F);
  settings_changed |= read_bool(iter, MESSAGE_KEY_SHAKE_ANIM, &g_settings.shake_anim);
  settings_changed |= read_bool(iter, MESSAGE_KEY_BT_VIBE, &g_settings.bt_vibe);
  if (settings_changed) state_save_settings();

  bool weather_changed = false;
  Tuple *temp = dict_find(iter, MESSAGE_KEY_TEMP_C10);
  Tuple *code = dict_find(iter, MESSAGE_KEY_WCODE);
#if defined(TRS_DEMO)
  temp = NULL;  // keep the sample weather for screenshots
#endif
  if (temp && code) {
    g_weather.temp_c10 = (int16_t)tuple_int(temp);
    g_weather.code = (int16_t)tuple_int(code);
    Tuple *day = dict_find(iter, MESSAGE_KEY_IS_DAY);
    g_weather.is_day = day ? tuple_int(day) != 0 : true;
    Tuple *lat = dict_find(iter, MESSAGE_KEY_LAT100);
    Tuple *lon = dict_find(iter, MESSAGE_KEY_LON100);
    if (lat && lon) {
      g_weather.lat100 = (int16_t)tuple_int(lat);
      g_weather.lon100 = (int16_t)tuple_int(lon);
    }
    g_weather.updated = time(NULL);
    state_save_weather();
    weather_changed = true;
  }
  return settings_changed || weather_changed;
}

void state_update_steps(void) {
#if defined(TRS_DEMO)
  g_steps = 8421;
  g_distance_m = 6130;
#elif defined(PBL_HEALTH)
  time_t start = time_start_of_today(), now = time(NULL);
  g_steps = (health_service_metric_accessible(HealthMetricStepCount, start, now) &
             HealthServiceAccessibilityMaskAvailable)
                ? (int)health_service_sum_today(HealthMetricStepCount)
                : 0;
  g_distance_m = (health_service_metric_accessible(HealthMetricWalkedDistanceMeters, start, now) &
                  HealthServiceAccessibilityMaskAvailable)
                     ? (int)health_service_sum_today(HealthMetricWalkedDistanceMeters)
                     : 0;
#endif
}

void state_format_temp(char *buf, size_t len) {
  if (g_weather.temp_c10 == TEMP_NONE) {
    snprintf(buf, len, "--°");
    return;
  }
  int t10 = g_weather.temp_c10;
  if (g_settings.temp_unit == UNIT_F) t10 = t10 * 9 / 5 + 320;
  // round half away from zero
  int t = t10 >= 0 ? (t10 + 5) / 10 : -((-t10 + 5) / 10);
  snprintf(buf, len, "%d°", t);
}

bool state_weather_stale(void) {
  if (g_weather.updated == 0) return true;
  return time(NULL) - g_weather.updated > WEATHER_STALE_SEC;
}

void state_format_position(char *buf, size_t len) {
  if (g_weather.lat100 == TEMP_NONE || g_weather.lon100 == TEMP_NONE) {
    buf[0] = '\0';
    return;
  }
  int la = g_weather.lat100, lo = g_weather.lon100;
  snprintf(buf, len, "%d.%02d%c %d.%02d%c", abs(la) / 100, abs(la) % 100, la < 0 ? 'S' : 'N',
           abs(lo) / 100, abs(lo) % 100, lo < 0 ? 'W' : 'E');
}

const char *state_watch_name(void) {
  // basalt only runs on Pebble Time and Time Steel
  return watch_info_get_model() == WATCH_INFO_MODEL_PEBBLE_TIME_STEEL ? "TIME STEEL" : "PEBBLE TIME";
}
