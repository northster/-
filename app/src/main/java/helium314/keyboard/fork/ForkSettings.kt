// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.fork.gesture.SwipeThresholds
import helium314.keyboard.latin.utils.prefs

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
    const val PREF_KB_HEIGHT_DP = "fork_kb_height_dp"
    const val PREF_BOTTOM_PADDING_DP = "fork_bottom_padding_dp"
    const val PREF_SIDE_PADDING_DP = "fork_side_padding_dp"
    const val PREF_KEY_GAP_H_DP = "fork_key_gap_h_dp"
    const val PREF_KEY_GAP_V_DP = "fork_key_gap_v_dp"
    const val SIZE_UNSET = -1f

    /** dp value of a size pref, or [SIZE_UNSET] */
    @JvmStatic
    fun sizeDp(prefs: SharedPreferences, key: String): Float =
        if (prefs.contains(key)) prefs.getFloat(key, SIZE_UNSET) else SIZE_UNSET

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
        initialized = true
    }

    @JvmStatic
    fun isToolbarSwipeEnabled() = initialized && cachedSwipeEnabled

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
