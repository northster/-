// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.fork.gesture.SwipeThresholds
import androidx.core.content.edit
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import kotlin.math.roundToInt

/**
 * Settings that are specific to this fork. Kept separate from HeliBoard's Settings / Defaults
 * to make merging upstream changes easier.
 */
object ForkSettings {
    // ---- gesture policy (compile time, not user configurable) ----
    /** Glide / gesture typing. Disabled, it conflicts with vertical toolbar swipes. */
    const val GLIDE_TYPING_ALLOWED = false
    /** Swipe on backspace to select and delete. */
    const val DELETE_SWIPE_ALLOWED = false
    /** Vertical swipe on space bar (language switch, touchpad, hide...). The key area swipe owns this direction. */
    const val VERTICAL_SPACE_SWIPE_ALLOWED = false
    /** Long press space, then drag to move the cursor. */
    const val SPACE_LONG_PRESS_CURSOR = true
    /** Show the language name on the space bar. Samsung style shows only the space icon. */
    const val LANGUAGE_ON_SPACEBAR = false
    /** One set of size values (height, paddings, gaps, split...) for every screen, no per-orientation / fold variants. */
    const val SINGLE_SIZE_PROFILE = true
    /** Keyboard background images. Removed from settings. */
    const val BACKGROUND_IMAGE_ALLOWED = false
    /** Word suggestions (suggestion strip candidates). Removed from settings. */
    const val SUGGESTIONS_ALLOWED = false
    /** Keyboard look comes from editable theme files (fork/theme) instead of HeliBoard's color themes. */
    const val THEME_FILES = true
    const val PREF_SHOW_KEYBOARD_PREVIEW = "fork_show_keyboard_preview"

    // ---- preference keys ----
    const val PREF_TOOLBAR_SWIPE_ENABLED = "fork_toolbar_swipe_enabled"
    const val PREF_SWIPE_MIN_DISTANCE_DP = "fork_swipe_min_distance_dp"
    const val PREF_SWIPE_MIN_VELOCITY = "fork_swipe_min_velocity_dp_s"
    const val PREF_SWIPE_MAX_ANGLE = "fork_swipe_max_angle_deg"
    const val PREF_SWIPE_HORIZONTAL_REJECT_DP = "fork_swipe_horizontal_reject_dp"
    const val PREF_SWIPE_MAX_DURATION = "fork_swipe_max_duration_ms"
    const val PREF_TOOLBAR_OVERLAY = "fork_toolbar_overlay"
    const val PREF_TOOLBAR_SMOOTH_RESIZE = "fork_toolbar_smooth_resize"
    const val PREF_TOOLBAR_ANIM_DURATION = "fork_toolbar_anim_duration_ms"
    /** Toolbar state, persisted so it survives input view re-creation (e.g. fold / unfold) and process death. */
    const val PREF_TOOLBAR_EXPANDED = "fork_toolbar_expanded"

    // ---- keyboard size in dp (Appearance & size). Absent = HeliBoard's scale values still apply,
    //      so sizes set before the dp sliders existed are kept until a slider is moved.
    /** visible height of a letter key. The keyboard height follows from it plus paddings and gaps. */
    const val PREF_KEY_HEIGHT_DP = "fork_key_height_dp"
    /** total keyboard height in dp, only used by builds before the key height existed (migrated) */
    private const val PREF_KB_HEIGHT_DP = "fork_kb_height_dp"
    private const val PREF_KEY_HEIGHT_MIGRATED = "fork_key_height_migrated"
    const val PREF_BOTTOM_PADDING_DP = "fork_bottom_padding_dp"
    const val PREF_SIDE_PADDING_DP = "fork_side_padding_dp"
    const val PREF_KEY_GAP_H_DP = "fork_key_gap_h_dp"
    const val PREF_KEY_GAP_V_DP = "fork_key_gap_v_dp"
    const val PREF_TOP_PADDING_DP = "fork_top_padding_dp"
    /** height of the bottom row keys (space bar row), replaces HeliBoard's bottom row scale once set */
    const val PREF_BOTTOM_ROW_DP = "fork_bottom_row_dp"
    /** height of the dynamic toolbar */
    const val PREF_TOOLBAR_HEIGHT_DP = "fork_toolbar_height_dp"
    const val SIZE_UNSET = -1f

    /** dp value of a size pref, or [SIZE_UNSET] */
    @JvmStatic
    fun sizeDp(prefs: SharedPreferences, key: String): Float =
        if (prefs.contains(key)) prefs.getFloat(key, SIZE_UNSET) else SIZE_UNSET

    /** emojis per row in the emoji panel, 0 = automatic (from key size) */
    const val PREF_EMOJI_COLUMNS = "fork_emoji_columns"

    /** settings entry that opens the dictionaries screen, where the emoji search dictionary is added (no stored value) */
    const val PREF_EMOJI_DICT_LINK = "fork_emoji_dict_link"

    /** type Hangul (and other composed text) without the app's composing underline */
    const val PREF_HIDE_COMPOSING_UNDERLINE = "fork_hide_composing_underline"
    const val DEFAULT_HIDE_COMPOSING_UNDERLINE = true

    @JvmStatic
    fun isComposingUnderlineHidden() =
        !initialized || appPrefs.getBoolean(PREF_HIDE_COMPOSING_UNDERLINE, DEFAULT_HIDE_COMPOSING_UNDERLINE)

    /** finger travel per character / per line in space cursor mode, dp */
    const val PREF_CURSOR_CHAR_STEP_DP = "fork_cursor_char_step_dp"
    const val PREF_CURSOR_LINE_STEP_DP = "fork_cursor_line_step_dp"
    const val DEFAULT_CURSOR_CHAR_STEP_DP = 10f
    const val DEFAULT_CURSOR_LINE_STEP_DP = 30f

    @JvmStatic
    fun cursorCharStepPx(): Int {
        val dp = if (initialized) appPrefs.getFloat(PREF_CURSOR_CHAR_STEP_DP, DEFAULT_CURSOR_CHAR_STEP_DP) else DEFAULT_CURSOR_CHAR_STEP_DP
        return (dp * density).toInt().coerceAtLeast(1)
    }

    @JvmStatic
    fun cursorLineStepPx(): Int {
        val dp = if (initialized) appPrefs.getFloat(PREF_CURSOR_LINE_STEP_DP, DEFAULT_CURSOR_LINE_STEP_DP) else DEFAULT_CURSOR_LINE_STEP_DP
        return (dp * density).toInt().coerceAtLeast(1)
    }

    /** alpha (0..255) of the glows drawn over the keys, from the key mask opacity; 0 = keys hide the glows */
    @JvmStatic
    fun glowOnKeysAlpha(): Int {
        val mask = if (initialized) appPrefs.getFloat(helium314.keyboard.fork.toolbar.GlowPrefs.KEY_MASK,
            helium314.keyboard.fork.toolbar.GlowPrefs.DEFAULT_KEY_MASK) else 1f
        return ((1f - mask.coerceIn(0f, 1f)) * 255).toInt()
    }

    /**
     * HeliBoard options that do nothing here, or fight the fork's own features: word suggestions and autocorrect are
     * off (no suggestion strip), the space bar moves the cursor on its own, there is no gesture typing library, the
     * bottom row is Samsung's. They are left out of the screens and the settings search.
     */
    @JvmField
    val removedHeliBoardSettings = setOf(
        helium314.keyboard.latin.settings.Settings.PREF_AUTO_CORRECTION,
        helium314.keyboard.latin.settings.Settings.PREF_MORE_AUTO_CORRECTION,
        helium314.keyboard.latin.settings.Settings.PREF_AUTOCORRECT_SHORTCUTS,
        helium314.keyboard.latin.settings.Settings.PREF_AUTOCORRECT_CAPITALIZED_SUGGESTION,
        helium314.keyboard.latin.settings.Settings.PREF_AUTO_CORRECT_CONFIDENCE,
        helium314.keyboard.latin.settings.Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT,
        helium314.keyboard.latin.settings.Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
        helium314.keyboard.latin.settings.Settings.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING,
        helium314.keyboard.latin.settings.Settings.PREF_AUTOSPACE_AFTER_GESTURE_TYPING,
        helium314.keyboard.latin.settings.Settings.PREF_SPACE_HORIZONTAL_SWIPE,
        helium314.keyboard.latin.settings.Settings.PREF_SPACE_VERTICAL_SWIPE,
        helium314.keyboard.latin.settings.Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY,
        helium314.keyboard.latin.settings.Settings.PREF_SHOW_SETUP_WIZARD_ICON,
        helium314.keyboard.latin.settings.Settings.PREF_EMOJI_MAX_SDK,
        helium314.keyboard.latin.settings.Settings.PREF_URL_DETECTION,
        helium314.keyboard.latin.settings.Settings.PREF_SHOW_TLD_POPUP_KEYS,
        helium314.keyboard.latin.settings.Settings.PREF_REMOVE_REDUNDANT_POPUPS,
        helium314.keyboard.latin.settings.Settings.PREF_SHOW_DPAD_KEY,
        helium314.keyboard.latin.settings.Settings.PREF_SHOW_EMOJI_DESCRIPTIONS,
        helium314.keyboard.latin.settings.Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST,
        helium314.keyboard.latin.settings.Settings.PREF_TOOLBAR_MODE,
        helium314.keyboard.latin.settings.Settings.PREF_TOOLBAR_HIDING_GLOBAL,
        helium314.keyboard.latin.settings.Settings.PREF_TOOLBAR_KEYS,
        helium314.keyboard.latin.settings.Settings.PREF_PINNED_TOOLBAR_KEYS,
        helium314.keyboard.latin.settings.Settings.PREF_CLIPBOARD_TOOLBAR_KEYS,
        helium314.keyboard.latin.settings.Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES,
        helium314.keyboard.latin.settings.Settings.PREF_QUICK_PIN_TOOLBAR_KEYS,
        helium314.keyboard.latin.settings.Settings.PREF_AUTO_SHOW_TOOLBAR,
        helium314.keyboard.latin.settings.Settings.PREF_AUTO_HIDE_TOOLBAR,
        helium314.keyboard.latin.settings.Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD,
        helium314.keyboard.latin.settings.Settings.PREF_VARIABLE_TOOLBAR_DIRECTION,
        helium314.keyboard.latin.settings.Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE,
        helium314.keyboard.settings.SettingsWithoutKey.LOAD_GESTURE_LIB,
        helium314.keyboard.settings.SettingsWithoutKey.EDIT_PERSONAL_DICTIONARY,
    )

    /** iPhone style floating caret while space is held (falls back to steps in apps that don't support it) */
    const val PREF_VIRTUAL_CARET = "fork_virtual_caret"

    @JvmStatic
    fun isVirtualCaretEnabled() = !initialized || appPrefs.getBoolean(PREF_VIRTUAL_CARET, true)

    /** how long space must be held before it moves the cursor (and other space long presses), ms; 0 = like HeliBoard */
    const val PREF_SPACE_LONG_PRESS_MS = "fork_space_long_press_ms"

    /**
     * the space long press time: [default] (HeliBoard: 1.5 x the key long press time, set in Advanced). The separate
     * setting was removed again (the long press delay covers it); a value stored before is ignored.
     */
    @JvmStatic
    fun spaceLongPressMs(default: Int): Int = default

    const val DEFAULT_TOOLBAR_SWIPE_ENABLED = true
    const val DEFAULT_TOOLBAR_OVERLAY = false
    const val DEFAULT_TOOLBAR_SMOOTH_RESIZE = true
    const val DEFAULT_TOOLBAR_ANIM_DURATION = 180

    @Volatile private var cachedThresholds: SwipeThresholds? = null
    @Volatile private var cachedSwipeEnabled = DEFAULT_TOOLBAR_SWIPE_ENABLED

    // strong reference, SharedPreferences only keeps weak references to listeners
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == null || key.startsWith("fork_swipe") || key == PREF_TOOLBAR_SWIPE_ENABLED) {
            cachedThresholds = null
            cachedSwipeEnabled = prefs.getBoolean(PREF_TOOLBAR_SWIPE_ENABLED, DEFAULT_TOOLBAR_SWIPE_ENABLED)
        }
    }
    private var initialized = false
    private var density = 1f
    private lateinit var appPrefs: SharedPreferences

    @JvmStatic
    fun init(context: Context) {
        if (initialized) return
        appPrefs = context.prefs()
        density = context.resources.displayMetrics.density
        appPrefs.registerOnSharedPreferenceChangeListener(listener)
        cachedSwipeEnabled = appPrefs.getBoolean(PREF_TOOLBAR_SWIPE_ENABLED, DEFAULT_TOOLBAR_SWIPE_ENABLED)
        runCatching { migrateToKeyHeight(context) }
        initialized = true
    }

    /**
     * The keyboard height used to be set as a whole, and paddings / gaps were taken from it.
     * Now the key height is set and the rest is added. Convert once so the keyboard looks the same as before:
     * key height from the old total height, paddings and vertical gap frozen at their old px values.
     */
    private fun migrateToKeyHeight(context: Context) {
        val prefs = appPrefs
        if (prefs.getBoolean(PREF_KEY_HEIGHT_MIGRATED, false)) return
        val heightScaleKey = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)
        val numberRow = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW)
        val customized = numberRow || prefs.contains(PREF_KB_HEIGHT_DP) || prefs.contains(heightScaleKey)
            || prefs.contains(PREF_TOP_PADDING_DP) || prefs.contains(PREF_BOTTOM_PADDING_DP) || prefs.contains(PREF_KEY_GAP_V_DP)
            || prefs.contains(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 0, 2))
            || prefs.contains(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2))
        if (customized && !prefs.contains(PREF_KEY_HEIGHT_DP)) {
            val res = context.resources
            val dm = res.displayMetrics
            val d = dm.density
            val oldDp = sizeDp(prefs, PREF_KB_HEIGHT_DP)
            val height = if (oldDp > 0) minOf(oldDp * d * (if (numberRow) 1.25f else 1f), dm.heightPixels * 0.7f)
                else ResourceUtils.getDefaultKeyboardHeight(res, numberRow) * Settings.readHeightScale(prefs, false, false)
            fun frac(id: Int) = res.getFraction(id, 1, 1) * height
            val top = sizeDp(prefs, PREF_TOP_PADDING_DP).takeIf { it >= 0 }?.times(d)
                ?: frac(R.fraction.config_keyboard_top_padding_holo)
            val bottom = sizeDp(prefs, PREF_BOTTOM_PADDING_DP).takeIf { it >= 0 }?.times(d)
                ?: (frac(R.fraction.config_keyboard_bottom_padding_holo) * Settings.readBottomPaddingScale(prefs, false, false))
            val gap = sizeDp(prefs, PREF_KEY_GAP_V_DP).takeIf { it >= 0 }?.times(d)
                ?: (frac(R.fraction.config_key_vertical_gap_holo) * Settings.readKeyGapScale(prefs, false, false))
            val rows = KeyboardParams.DEFAULT_KEYBOARD_ROWS + if (numberRow) 1 else 0
            val key = (height - top - bottom + gap) / rows - gap
            fun half(px: Float) = (px / d * 2).roundToInt() / 2f
            prefs.edit {
                putFloat(PREF_KEY_HEIGHT_DP, half(key).coerceAtLeast(20f))
                putFloat(PREF_TOP_PADDING_DP, half(top))
                putFloat(PREF_BOTTOM_PADDING_DP, half(bottom))
                putFloat(PREF_KEY_GAP_V_DP, half(gap))
            }
        }
        prefs.edit { remove(PREF_KB_HEIGHT_DP); putBoolean(PREF_KEY_HEIGHT_MIGRATED, true) }
    }

    @JvmStatic
    fun isToolbarSwipeEnabled() = initialized && cachedSwipeEnabled

    /** sideways fling over the keys switches the one-handed keyboard */
    const val PREF_ONE_HANDED_SWIPE = "fork_one_handed_swipe"
    @JvmStatic
    fun isOneHandedSwipeEnabled() = initialized && appPrefs.getBoolean(PREF_ONE_HANDED_SWIPE, true)

    /** Thresholds in px. [density] should be the one of the display the keyboard is shown on. */
    @JvmStatic
    fun swipeThresholds(density: Float): SwipeThresholds {
        cachedThresholds?.let { if (this.density == density) return it }
        this.density = density
        val p = appPrefs
        return SwipeThresholds.fromDp(
            density = density,
            minDistanceDp = p.getInt(PREF_SWIPE_MIN_DISTANCE_DP, SwipeThresholds.DEFAULT_MIN_DISTANCE_DP),
            minVelocityDpPerS = p.getInt(PREF_SWIPE_MIN_VELOCITY, SwipeThresholds.DEFAULT_MIN_VELOCITY_DP_PER_S),
            maxAngleDeg = p.getInt(PREF_SWIPE_MAX_ANGLE, SwipeThresholds.DEFAULT_MAX_ANGLE_DEG),
            horizontalRejectDp = p.getInt(PREF_SWIPE_HORIZONTAL_REJECT_DP, SwipeThresholds.DEFAULT_HORIZONTAL_REJECT_DP),
            maxDurationMs = p.getInt(PREF_SWIPE_MAX_DURATION, SwipeThresholds.DEFAULT_MAX_DURATION_MS),
        ).also { cachedThresholds = it }
    }
}
