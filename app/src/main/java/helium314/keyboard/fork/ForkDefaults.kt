// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs

/**
 * fork: the settings a new install starts with, taken from the developer's own setup (sizes, theme, icons, toolbar
 * motion, glows, clipboard, keys shown, the Doto Rounded Black font). Written once, on the very first start of a fresh
 * install (nothing stored yet), so nobody's own settings are ever changed; afterwards they are ordinary settings.
 * Personal things (keys, sessions, clipboard, learned typos, per-app languages, AI commands) are not part of it.
 */
object ForkDefaults {
    private const val TAG = "ForkDefaults"
    private const val PREF_SEEDED = "fork_defaults_seeded"
    /** the font a new install uses for letters, from assets (SIL Open Font License, see the open source notices) */
    private const val FONT_ASSET = "fonts/Doto_Rounded-Black.ttf"

    /** before anything else reads the settings: a fresh install gets the starting settings */
    fun seedIfFresh(context: Context) {
        val prefs = context.prefs()
        if (prefs.getInt(Settings.PREF_VERSION_CODE, 0) != 0 || prefs.contains(PREF_SEEDED)) return
        prefs.edit {
            BOOLEANS.forEach { (k, v) -> putBoolean(k, v) }
            INTS.forEach { (k, v) -> putInt(k, v) }
            FLOATS.forEach { (k, v) -> putFloat(k, v) }
            STRINGS.forEach { (k, v) -> putString(k, v) }
            putBoolean(PREF_SEEDED, true)
        }
        runCatching {
            val font = Settings.getCustomFontFile(context)
            if (!font.exists()) context.assets.open(FONT_ASSET).use { input -> font.outputStream().use { input.copyTo(it) } }
        }.onFailure { Log.w(TAG, "could not copy the starting font", it) }
    }

    private val BOOLEANS = mapOf(
        "abc_after_clip" to true,
        "fork_accent_on_delete" to false,
        "fork_chip_border" to false,
        "fork_clip_notification_codes" to true,
        "fork_letter_hold_cursor" to true,
        "fork_one_handed_swipe" to false,
        "fork_toolbar_start_closed" to true,
        "fork_toolbar_swipe_enabled" to true,
        "fork_virtual_caret" to false,
        "fork_widget_emoji" to false,
        "fork_widget_on_space" to false,
        "navbar_color" to false,
        "popup_on" to false,
        "save_subtype_per_app" to true,
        "show_hints" to true,
        "show_popup_hints" to false,
        "theme_auto_day_night" to false,
        "use_double_space_period" to false,
        "show_dpad_key" to false,
        "show_emoji_key" to true,
        "show_language_switch_key" to true,
        "split_keyboard" to false,
        "fork_show_keyboard_preview" to true,
        "fork_key_height_migrated" to true,
        "fork_clip_actions_pinned_added" to true,
    )

    private val INTS = mapOf(
        "fork_chip_border_color" to -13290187,
        "fork_clip_max_items" to 100,
        "fork_clip_retention_hours" to 24,
        "fork_emoji_columns" to 10,
    )

    private val FLOATS = mapOf(
        "bottom_row_scale_false_false" to 1.0f,
        "font_scale" to 0.72f,
        "fork_ai_glow_in" to 400.0f,
        "fork_ai_glow_max" to 1.0f,
        "fork_ai_glow_min" to 0.7f,
        "fork_bottom_padding_dp" to 28.0f,
        "fork_clip_preview_lines_f" to 4.0f,
        "fork_glow_height" to 0.6f,
        "fork_glow_max" to 0.9f,
        "fork_glow_min" to 0.45f,
        "fork_glow_width" to 1.3f,
        "fork_key_alpha" to 1.0f,
        "fork_key_gap_v_dp" to 11.0f,
        "fork_key_height_dp" to 40.0f,
        "fork_space_long_press_ms" to 200.0f,
        "fork_split_side_padding_dp" to 60.0f,
        "fork_toolbar_height_dp" to 40.0f,
        "fork_toolbar_motion_row_delay" to 15.0f,
        "fork_toolbar_motion_speed" to 1.15f,
        "fork_top_padding_dp" to 8.0f,
        "fork_wave_brightness" to 0.35f,
        "fork_wave_duration" to 500.0f,
        "fork_wave_ripple_depth" to 340.0f,
        "fork_wave_thickness" to 96.0f,
        "split_spacer_scale_false_false" to 1.13f,
    )

    private val STRINGS = mapOf(
        "fork_ai_glow_look" to "edge_lines",
        "fork_glow_position" to "bottom",
        "fork_clip_actions" to "SEARCH;PINNED;CLEAR_CLIPBOARD",
        "fork_clip_chip_hint" to "glow",
        "fork_icon_style" to "line",
        "fork_theme_active" to "samsung_dark",
        "fork_themes" to "[{\"id\":\"samsung_dark\",\"name\":\"Samsung dark\",\"background\":-16119286,\"key\":-15066598,\"keyText\":-263173,\"hintText\":-6381922,\"functionalKey\":-14277082,\"functionalText\":-1,\"pressed\":null,\"border\":0,\"enterKey\":-2349530,\"enterIcon\":-1,\"accent\":-1,\"popup\":-12961222,\"popupText\":-1,\"toolbarIcon\":-2039584,\"keyRadius\":5.0,\"borderWidth\":0.0,\"enterRadius\":25.0},{\"id\":\"samsung_light\",\"name\":\"Samsung light\",\"background\":-986896,\"key\":-1,\"keyText\":-15066598,\"hintText\":-8750470,\"functionalKey\":-2434342,\"functionalText\":-15066598,\"pressed\":null,\"border\":0,\"enterKey\":-2434342,\"enterIcon\":-15066598,\"accent\":-12676609,\"popup\":-1,\"popupText\":-15066598,\"toolbarIcon\":-12566464,\"keyRadius\":5.0,\"borderWidth\":0.0,\"enterRadius\":5.0}]",
        "fork_toolbar_motion" to "pull",
        "fork_wave_shape" to "ripple",
        "fork_chip_border_style" to "solid",
        "fork_currency_rates_source" to "Coinbase",
        "fork_font_name_latin" to "Doto_Rounded-Black.ttf",
    )
}
