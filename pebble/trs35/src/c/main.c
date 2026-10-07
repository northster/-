#include <pebble.h>
#include "face.h"
#include "state.h"

// Ask the phone for fresh weather every 30 minutes (on :00 and :30).
#define WEATHER_EVERY_MIN 30

// Shake clip: 16 frames at ~15 fps (about 1.1 s), then back to rest.
// Frame count and rate are capped for battery; a 3 s cool-down stops
// back-to-back flicks from chaining clips, and below 10% it does not play.
#define ANIM_FRAMES 16
#define ANIM_FRAME_MS 66
#define ANIM_COOLDOWN_MS 3000
#define ANIM_MIN_BATTERY 10

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

/* ---- phone link --------------------------------------------------------- */

static void request_weather(void) {
  if (!g_bt_connected) return;
  DictionaryIterator *iter;
  if (app_message_outbox_begin(&iter) != APP_MSG_OK) return;
  dict_write_uint8(iter, MESSAGE_KEY_WEATHER_REQ, 1);
  app_message_outbox_send();
}

static void request_weather_cb(void *data) { request_weather(); }

static void update_tap_subscription(void);

static void inbox_received(DictionaryIterator *iter, void *context) {
  bool layout_changed;
  if (!state_apply_message(iter, &layout_changed)) return;
  update_tap_subscription();
  if (layout_changed) face_apply_layout();
  update_time();  // clock mode may have changed
  face_mark_all_dirty();
}

static void inbox_dropped(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_WARNING, "inbox dropped: %d", (int)reason);
}

/* ---- shake animation ---------------------------------------------------- */

static AppTimer *s_anim_timer;
static int s_anim_frame;
// Cool-down is a timer rather than a timestamp, so a clock change from the
// phone (time sync, time zone) cannot leave the animation locked.
static bool s_anim_cooling;

static void cooldown_done(void *data) { s_anim_cooling = false; }

static void anim_step(void *data) {
  s_anim_frame++;
  if (s_anim_frame >= ANIM_FRAMES) {
    s_anim_timer = NULL;
    face_set_anim(-1, 0);
    s_anim_cooling = true;
    app_timer_register(ANIM_COOLDOWN_MS, cooldown_done, NULL);
    return;
  }
  face_set_anim(s_anim_frame * 1000 / (ANIM_FRAMES - 1), s_anim_frame);
  s_anim_timer = app_timer_register(ANIM_FRAME_MS, anim_step, NULL);
}

static void tap_handler(AccelAxisType axis, int32_t direction) {
  if (s_anim_timer || s_anim_cooling) return;
  if (g_battery.charge_percent <= ANIM_MIN_BATTERY && !g_battery.is_charging) return;
  s_anim_frame = 0;
  face_set_anim(0, 0);
  s_anim_timer = app_timer_register(ANIM_FRAME_MS, anim_step, NULL);
}

// The tap service only runs while the option is on.
static void update_tap_subscription(void) {
  static bool subscribed;
  if (g_settings.shake_anim && !subscribed) {
    accel_tap_service_subscribe(tap_handler);
    subscribed = true;
  } else if (!g_settings.shake_anim && subscribed) {
    accel_tap_service_unsubscribe();
    subscribed = false;
  }
}

/* ---- services ----------------------------------------------------------- */

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time();
  if (tick_time->tm_min % WEATHER_EVERY_MIN == 0) request_weather();
  else if (tick_time->tm_min % 10 == 0) face_mark_dirty(MOD_WX);  // stale tag
}

static void battery_handler(BatteryChargeState state) {
  g_battery = state;
  face_mark_dirty(MOD_HEADER);
}

static void connection_handler(bool connected) {
  g_bt_connected = connected;
  face_mark_dirty(MOD_HEADER);
  if (!connected) {
    if (g_settings.bt_vibe && !quiet_time_is_active()) vibes_double_pulse();
  } else if (state_weather_stale()) {
    // give the phone a moment to start PebbleKit JS
    app_timer_register(5000, request_weather_cb, NULL);
  }
}

#if defined(PBL_HEALTH)
static void health_handler(HealthEventType event, void *context) {
  if (event == HealthEventMovementUpdate || event == HealthEventSignificantUpdate) {
    int before = g_steps;
    state_update_steps();
    if (g_steps != before) face_mark_dirty(MOD_STEPS);
  }
}
#endif

/* ---- lifecycle ---------------------------------------------------------- */

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
  g_weather.updated = time(NULL) - 23 * 60;
  g_weather.lat100 = 3757;
  g_weather.lon100 = 12698;
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
  battery_state_service_subscribe(battery_handler);
  connection_service_subscribe((ConnectionHandlers){
    .pebble_app_connection_handler = connection_handler,
  });
#if defined(PBL_HEALTH) && !defined(TRS_DEMO)
  health_service_events_subscribe(health_handler, NULL);
#endif

  update_tap_subscription();

  app_message_register_inbox_received(inbox_received);
  app_message_register_inbox_dropped(inbox_dropped);
  app_message_open(256, 64);
}

static void deinit(void) {
  if (s_anim_timer) app_timer_cancel(s_anim_timer);
  if (g_settings.shake_anim) accel_tap_service_unsubscribe();
  tick_timer_service_unsubscribe();
  battery_state_service_unsubscribe();
  connection_service_unsubscribe();
#if defined(PBL_HEALTH) && !defined(TRS_DEMO)
  health_service_events_unsubscribe();
#endif
  app_message_deregister_callbacks();
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
