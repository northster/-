// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.SharedPreferences
import helium314.keyboard.fork.ForkSettings
import helium314.keyboard.fork.clipboard.ClipPrefs
import helium314.keyboard.fork.emoji.EmojiTabs
import helium314.keyboard.fork.slate.SlateKeys
import helium314.keyboard.fork.smart.SmartPrefs
import helium314.keyboard.fork.toolbar.GlowPrefs
import helium314.keyboard.fork.widget.WidgetPrefs
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsWithoutKey

/**
 * fork: easy settings mode (Advanced > easy settings). Only what a light user sets is shown: fine tuning, animation
 * details, testers and rarely used keys are hidden, their values stay as they are. The search still finds everything.
 */
object EasySettings {
    const val PREF = "fork_easy_settings"

    fun enabled(prefs: SharedPreferences) = prefs.getBoolean(PREF, false)

    fun shows(prefs: SharedPreferences, key: String) = !enabled(prefs) || key !in HIDDEN

    private val HIDDEN = setOf(
        // keys & feedback
        Settings.PREF_POPUP_KEYS_HINT_ORDER,
        Settings.PREF_POPUP_KEYS_ORDER,
        Settings.PREF_SHOW_POPUP_HINTS,
        Settings.PREF_VIBRATION_DURATION_SETTINGS,
        Settings.PREF_VIBRATE_IN_DND_MODE,
        Settings.PREF_KEYPRESS_SOUND_VOLUME,
        Settings.PREF_LOCALIZED_NUMBER_ROW,
        Settings.PREF_SHOW_NUMBER_ROW_HINTS,
        Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS,
        Settings.PREF_LANGUAGE_SWITCH_KEY,
        ForkSettings.PREF_CURSOR_CHAR_STEP_DP,
        ForkSettings.PREF_CURSOR_LINE_STEP_DP,
        ForkSettings.PREF_HIDE_COMPOSING_UNDERLINE,
        Settings.PREF_SAVE_SUBTYPE_PER_APP,
        Settings.PREF_SHIFT_REMOVES_AUTOSPACE,
        // size & text: the keyboard height, split and text size stay
        Settings.PREF_BOTTOM_ROW_SCALE_PREFIX,
        ForkSettings.PREF_TOP_PADDING_DP,
        Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX,
        Settings.PREF_SIDE_PADDING_SCALE_PREFIX,
        Settings.PREF_KEY_GAP_SCALE_PREFIX,
        ForkSettings.PREF_KEY_GAP_V_DP,
        ForkSettings.PREF_TOOLBAR_HEIGHT_DP,
        ForkSettings.PREF_TOOLBAR_PADDING_DP,
        Settings.PREF_SPLIT_SPACER_SCALE_PREFIX,
        ForkSettings.PREF_SPLIT_SIDE_PADDING_DP,
        Settings.PREF_SPACE_BAR_TEXT,
        Settings.PREF_HINT_FONT_SCALE,
        Settings.PREF_EMOJI_FONT_SCALE,
        ForkSettings.PREF_EMOJI_COLUMNS,
        EmojiTabs.PREF,
        Settings.PREF_EMOJI_KEY_FIT,
        ForkSettings.PREF_EMOJI_DICT_LINK,
        SettingsWithoutKey.CUSTOM_FONT,
        ForkFonts.KOREAN,
        SettingsWithoutKey.CUSTOM_EMOJI_FONT,
        // theme: display details and the chip look
        Settings.PREF_NAVBAR_COLOR,
        Settings.PREF_CUSTOM_ICON_NAMES,
        ClipPrefs.CHIP_BORDER,
        ClipPrefs.CHIP_BORDER_STYLE,
        ClipPrefs.CHIP_BORDER_COLOR,
        ClipPrefs.CHIP_BG_COLOR,
        ClipPrefs.CHIP_TEXT_COLOR,
        ClipPrefs.CHIP_RADIUS,
        // toolbar: swipe tuning, animation, wave and glow details
        ForkSettings.PREF_SWIPE_MIN_DISTANCE_DP,
        ForkSettings.PREF_SWIPE_MIN_VELOCITY,
        ForkSettings.PREF_SWIPE_MAX_ANGLE,
        ForkSettings.PREF_SWIPE_HORIZONTAL_REJECT_DP,
        ForkSettings.PREF_SWIPE_MAX_DURATION,
        ForkSettings.PREF_TOOLBAR_ANIM_DURATION,
        ForkSettings.PREF_TOOLBAR_SMOOTH_RESIZE,
        ForkSettings.PREF_TOOLBAR_OVERLAY,
        GlowPrefs.WAVE_DURATION,
        GlowPrefs.WAVE_BRIGHTNESS,
        GlowPrefs.WAVE_THICKNESS,
        GlowPrefs.WAVE_SHAPE,
        GlowPrefs.WAVE_RIPPLE_DEPTH,
        GlowPrefs.KEY_MASK,
        GlowPrefs.GLOW_TEST_UNTIL,
        GlowPrefs.GLOW_POSITION,
        GlowPrefs.GLOW_MAX,
        GlowPrefs.GLOW_MIN,
        GlowPrefs.GLOW_PERIOD,
        GlowPrefs.GLOW_HEIGHT,
        GlowPrefs.GLOW_WIDTH,
        GlowPrefs.GLOW_DOT_SIZE,
        GlowPrefs.GLOW_DOT_SPACING,
        GlowPrefs.GLOW_BLOOM,
        GlowPrefs.AI_GLOW_TEST,
        GlowPrefs.AI_GLOW_BLOOM,
        GlowPrefs.AI_GLOW_POSITION,
        GlowPrefs.AI_GLOW_HEIGHT,
        GlowPrefs.AI_GLOW_WIDTH,
        GlowPrefs.AI_GLOW_MAX,
        GlowPrefs.AI_GLOW_MIN,
        GlowPrefs.AI_GLOW_BREATH,
        GlowPrefs.AI_GLOW_IN,
        // clipboard and smart chips: display details and testers
        ClipPrefs.PREVIEW_LINES,
        Settings.PREF_ABC_AFTER_CLIP,
        Settings.PREF_CLIPBOARD_USE_FILES,
        Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT,
        SmartPrefs.TESTER,
        SmartPrefs.DEBUG,
        ClipPrefs.SMART_TESTER,
        ClipPrefs.CODE_TEST_UNTIL,
        // widgets
        WidgetPrefs.TRIVIA_NOW,
        WidgetPrefs.TRIVIA_AI,
        // advanced
        Settings.PREF_KEY_LONGPRESS_TIMEOUT,
        Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD,
        Settings.PREF_ABC_AFTER_SYMBOL_SPACE,
        Settings.PREF_ABC_AFTER_NUMPAD_SPACE,
        Settings.PREF_ABC_AFTER_EMOJI,
        Settings.PREF_CUSTOM_CURRENCY_KEY,
        Settings.PREF_MORE_POPUP_KEYS,
        Settings.PREF_TIMESTAMP_FORMAT,
        SlateKeys.PREF_MODEL,
    )
}
