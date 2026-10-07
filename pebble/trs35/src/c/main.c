#include <pebble.h>
#include "face.h"
#include "state.h"

static Window *s_window;

static void update_time(void) {
  time_t now = time(NULL);
  struct tm *t = localtime(&now);
#if defined(TRS_DEMO)
  t->tm_hour = 10;
  t->tm_min = 8;
#endif
  face_set_time(t);
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time();
}

static void window_load(Window *window) {
  face_create(window);
  update_time();
}

static void window_unload(Window *window) {
  face_destroy();
}

static void init(void) {
  state_load();
#if defined(TRS_DEMO)
  g_weather.temp_c10 = 234;
  g_weather.code = 2;
  g_weather.is_day = true;
  g_weather.updated = time(NULL);
#if defined(TRS_THEME)
  g_settings.theme = TRS_THEME;
#endif
#if defined(TRS_LAYOUT)
  g_settings.layout = TRS_LAYOUT;
#endif
#endif
  state_update_steps();
  g_battery = battery_state_service_peek();
  g_bt_connected = connection_service_peek_pebble_app_connection();

  s_window = window_create();
  window_set_window_handlers(s_window, (WindowHandlers){
    .load = window_load,
    .unload = window_unload,
  });
  window_stack_push(s_window, false);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
}

static void deinit(void) {
  tick_timer_service_unsubscribe();
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
