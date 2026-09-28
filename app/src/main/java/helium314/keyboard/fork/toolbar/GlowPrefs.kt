// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.content.SharedPreferences

/** fork: settings of the paste chip glow and of the wave when the toolbar opens / closes (toolbar screen). */
object GlowPrefs {
    /** glow behind the keys while a paste chip waits behind the collapsed toolbar */
    const val GLOW = "fork_glow_enabled"
    const val GLOW_MAX = "fork_glow_max"
    const val GLOW_MIN = "fork_glow_min"
    /** one full breath (dim -> bright -> dim), ms */
    const val GLOW_PERIOD = "fork_glow_period"
    /** how far into the keyboard the glow reaches from its edge, fraction of the keyboard height */
    const val GLOW_HEIGHT = "fork_glow_height"
    /** how wide the glow is, fraction of the keyboard width */
    const val GLOW_WIDTH = "fork_glow_width"
    /** edge the glow comes from, [POSITION_TOP] or [POSITION_BOTTOM] */
    const val GLOW_POSITION = "fork_glow_position"
    const val POSITION_TOP = "top"
    const val POSITION_BOTTOM = "bottom"
    /** color of the glow's dots, unset = [DEFAULT_GLOW_COLOR] */
    const val GLOW_COLOR = "fork_glow_color"
    /** a sky blue that stands out on a black keyboard */
    const val DEFAULT_GLOW_COLOR = 0xFF38BDF8.toInt()
    const val GLOW_DOT_SIZE = "fork_glow_dot_size"
    const val GLOW_DOT_SPACING = "fork_glow_dot_spacing"
    /** until this time (ms) a test chip is offered, to see the glow without copying anything */
    const val GLOW_TEST_UNTIL = "fork_clip_hint_test_until"

    /** pastel light behind the keys while an AI command or translation runs */
    const val AI_GLOW = "fork_ai_glow_enabled"
    /** how long it takes to appear (and to go away), ms */
    const val AI_GLOW_IN = "fork_ai_glow_in"
    const val AI_GLOW_MAX = "fork_ai_glow_max"
    const val AI_GLOW_MIN = "fork_ai_glow_min"
    /** breathing, like the chip glow's [GLOW_PERIOD] */
    const val AI_GLOW_BREATH = "fork_ai_glow_breath"
    /** settings entry that shows the glow for a few seconds (nothing stored) */
    const val AI_GLOW_TEST = "fork_ai_glow_test"
    const val DEFAULT_AI_GLOW_IN = 900f
    const val DEFAULT_AI_GLOW_MAX = 0.7f
    const val DEFAULT_AI_GLOW_MIN = 0.4f
    const val DEFAULT_AI_GLOW_BREATH = 2400f

    /**
     * the glows are drawn behind the keys and again over them, clipped to the key shapes: this is how opaque that
     * key shaped mask over them is (1 = the keys hide the glows, 0 = the glows show on the keys as between them)
     */
    const val KEY_MASK = "fork_key_mask_opacity"
    const val DEFAULT_KEY_MASK = 0.7f

    /** white dot wave running over the keyboard when the toolbar opens (up, into the toolbar) or closes (down) */
    const val WAVE = "fork_wave_enabled"
    const val WAVE_DURATION = "fork_wave_duration"
    const val WAVE_BRIGHTNESS = "fork_wave_brightness"
    const val WAVE_THICKNESS = "fork_wave_thickness"
    /** [SHAPE_LINE] (straight band) or [SHAPE_RIPPLE] (ring spreading from below the keyboard) */
    const val WAVE_SHAPE = "fork_wave_shape"
    const val SHAPE_LINE = "line"
    const val SHAPE_RIPPLE = "ripple"
    /** how far below the keyboard the ripple's center is, dp: small = round ring, large = almost flat */
    const val WAVE_RIPPLE_DEPTH = "fork_wave_ripple_depth"

    const val DEFAULT_GLOW = true
    const val DEFAULT_GLOW_MAX = 0.85f
    const val DEFAULT_GLOW_MIN = 0.25f
    const val DEFAULT_GLOW_PERIOD = 2400f
    const val DEFAULT_GLOW_HEIGHT = 0.8f
    const val DEFAULT_GLOW_WIDTH = 1f
    const val DEFAULT_GLOW_DOT_SIZE = 1.1f
    const val DEFAULT_GLOW_DOT_SPACING = 4.5f
    const val DEFAULT_WAVE = true
    const val DEFAULT_WAVE_DURATION = 450f
    const val DEFAULT_WAVE_BRIGHTNESS = 0.9f
    const val DEFAULT_WAVE_THICKNESS = 28f
    const val DEFAULT_WAVE_RIPPLE_DEPTH = 120f

    /** everything the glow drawable needs, read once when it is (re)created */
    data class Glow(
        val maxAlpha: Float, val minAlpha: Float, val periodMs: Long,
        val height: Float, val width: Float, val dotDp: Float, val spacingDp: Float, val fromBottom: Boolean,
    )

    /** [rippleDepthDp] 0 = straight band */
    data class Wave(
        val durationMs: Long, val brightness: Float, val thicknessDp: Float, val dotDp: Float, val spacingDp: Float,
        val rippleDepthDp: Float,
    )

    fun glowEnabled(prefs: SharedPreferences) = prefs.getBoolean(GLOW, DEFAULT_GLOW)
    fun color(prefs: SharedPreferences) = prefs.getInt(GLOW_COLOR, DEFAULT_GLOW_COLOR)
    fun waveEnabled(prefs: SharedPreferences) = prefs.getBoolean(WAVE, DEFAULT_WAVE)

    fun glow(prefs: SharedPreferences): Glow {
        val max = prefs.getFloat(GLOW_MAX, DEFAULT_GLOW_MAX).coerceIn(0f, 1f)
        return Glow(
            maxAlpha = max,
            minAlpha = prefs.getFloat(GLOW_MIN, DEFAULT_GLOW_MIN).coerceIn(0f, max),
            periodMs = prefs.getFloat(GLOW_PERIOD, DEFAULT_GLOW_PERIOD).toLong().coerceIn(200, 20_000),
            height = prefs.getFloat(GLOW_HEIGHT, DEFAULT_GLOW_HEIGHT).coerceIn(0.05f, 1f),
            width = prefs.getFloat(GLOW_WIDTH, DEFAULT_GLOW_WIDTH).coerceIn(0.05f, 2f),
            dotDp = prefs.getFloat(GLOW_DOT_SIZE, DEFAULT_GLOW_DOT_SIZE).coerceIn(0.3f, 4f),
            spacingDp = prefs.getFloat(GLOW_DOT_SPACING, DEFAULT_GLOW_DOT_SPACING).coerceIn(2f, 16f),
            fromBottom = prefs.getString(GLOW_POSITION, POSITION_TOP) == POSITION_BOTTOM,
        )
    }

    /** the wave uses the glow's dot grid, so both look like one dot matrix */
    fun wave(prefs: SharedPreferences) = Wave(
        durationMs = prefs.getFloat(WAVE_DURATION, DEFAULT_WAVE_DURATION).toLong().coerceIn(50, 5000),
        brightness = prefs.getFloat(WAVE_BRIGHTNESS, DEFAULT_WAVE_BRIGHTNESS).coerceIn(0f, 1f),
        thicknessDp = prefs.getFloat(WAVE_THICKNESS, DEFAULT_WAVE_THICKNESS).coerceIn(2f, 200f),
        dotDp = prefs.getFloat(GLOW_DOT_SIZE, DEFAULT_GLOW_DOT_SIZE).coerceIn(0.3f, 4f),
        spacingDp = prefs.getFloat(GLOW_DOT_SPACING, DEFAULT_GLOW_DOT_SPACING).coerceIn(2f, 16f),
        rippleDepthDp = if (prefs.getString(WAVE_SHAPE, SHAPE_LINE) == SHAPE_RIPPLE)
            prefs.getFloat(WAVE_RIPPLE_DEPTH, DEFAULT_WAVE_RIPPLE_DEPTH).coerceIn(5f, 2000f) else 0f,
    )
}
