// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import kotlin.math.roundToInt
import helium314.keyboard.fork.ForkSettings
import helium314.keyboard.fork.gesture.SwipeThresholds
import helium314.keyboard.fork.toolbar.DynamicToolbarController
import helium314.keyboard.fork.toolbar.GlowPrefs
import helium314.keyboard.settings.preferences.InlineSliderPreference
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun DynamicToolbarScreen(
    onClickBack: () -> Unit,
) {
    // fork: the dynamic toolbar's settings
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? helium314.keyboard.settings.SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        helium314.keyboard.latin.utils.Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val items = listOf(
            R.string.fork_cat_gesture,
            ForkSettings.PREF_TOOLBAR_SWIPE_ENABLED,
            ForkSettings.PREF_SWIPE_MIN_DISTANCE_DP,
            ForkSettings.PREF_SWIPE_MIN_VELOCITY,
            ForkSettings.PREF_SWIPE_MAX_ANGLE,
            ForkSettings.PREF_SWIPE_HORIZONTAL_REJECT_DP,
            ForkSettings.PREF_SWIPE_MAX_DURATION,
            R.string.fork_cat_animation,
            ForkSettings.PREF_TOOLBAR_ANIM_DURATION,
            ForkSettings.PREF_TOOLBAR_SMOOTH_RESIZE,
            ForkSettings.PREF_TOOLBAR_OVERLAY,
            R.string.fork_cat_wave,
            GlowPrefs.WAVE,
            GlowPrefs.WAVE_DURATION,
            GlowPrefs.WAVE_BRIGHTNESS,
            GlowPrefs.WAVE_THICKNESS,
            GlowPrefs.WAVE_SHAPE,
            if (prefs.getString(GlowPrefs.WAVE_SHAPE, GlowPrefs.SHAPE_LINE) == GlowPrefs.SHAPE_RIPPLE) GlowPrefs.WAVE_RIPPLE_DEPTH else null,
            R.string.fork_cat_glow,
            GlowPrefs.GLOW,
            GlowPrefs.GLOW_TEST_UNTIL,
            GlowPrefs.GLOW_POSITION,
            GlowPrefs.GLOW_COLOR,
            GlowPrefs.GLOW_MAX,
            GlowPrefs.GLOW_MIN,
            GlowPrefs.GLOW_PERIOD,
            GlowPrefs.GLOW_HEIGHT,
            GlowPrefs.GLOW_WIDTH,
            GlowPrefs.GLOW_DOT_SIZE,
            GlowPrefs.GLOW_DOT_SPACING,
            R.string.fork_cat_gif,
            helium314.keyboard.fork.gif.GifClient.PREF_KLIPY_KEY,
            helium314.keyboard.fork.gif.GifClient.PREF_GIPHY_KEY,
            // HeliBoard's own toolbar (suggestion strip modes) is not shown: it must stay hidden for the dynamic toolbar
        )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_toolbar),
        settings = items,
    ) {
        KeyboardPreviewScaffold { helium314.keyboard.settings.SettingsSections(items) }
    }
}

fun createDynamicToolbarSettings(context: Context) = listOf(
    Setting(context, ForkSettings.PREF_TOOLBAR_SWIPE_ENABLED,
        R.string.fork_toolbar_swipe_enabled, R.string.fork_toolbar_swipe_enabled_summary) {
        SwitchPreference(it, ForkSettings.DEFAULT_TOOLBAR_SWIPE_ENABLED)
    },
    Setting(context, ForkSettings.PREF_SWIPE_MIN_DISTANCE_DP, R.string.fork_swipe_min_distance) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = SwipeThresholds.DEFAULT_MIN_DISTANCE_DP,
            range = 10f..120f,
            description = { stringResource(R.string.fork_unit_dp, it.toString()) },
            stepSize = 2,
        )
    },
    Setting(context, ForkSettings.PREF_SWIPE_MIN_VELOCITY, R.string.fork_swipe_min_velocity) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = SwipeThresholds.DEFAULT_MIN_VELOCITY_DP_PER_S,
            range = 0f..1500f,
            description = { stringResource(R.string.fork_unit_dp_per_s, it.toString()) },
            stepSize = 25,
        )
    },
    Setting(context, ForkSettings.PREF_SWIPE_MAX_ANGLE, R.string.fork_swipe_max_angle) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = SwipeThresholds.DEFAULT_MAX_ANGLE_DEG,
            range = 5f..60f,
            description = { stringResource(R.string.fork_unit_degrees, it.toString()) },
            stepSize = 1,
        )
    },
    Setting(context, ForkSettings.PREF_SWIPE_HORIZONTAL_REJECT_DP, R.string.fork_swipe_horizontal_reject) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = SwipeThresholds.DEFAULT_HORIZONTAL_REJECT_DP,
            range = 8f..80f,
            description = { stringResource(R.string.fork_unit_dp, it.toString()) },
            stepSize = 2,
        )
    },
    Setting(context, ForkSettings.PREF_SWIPE_MAX_DURATION, R.string.fork_swipe_max_duration) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = SwipeThresholds.DEFAULT_MAX_DURATION_MS,
            range = 100f..1000f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, it.toString()) },
            stepSize = 25,
        )
    },
    Setting(context, ForkSettings.PREF_TOOLBAR_ANIM_DURATION, R.string.fork_toolbar_anim_duration) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = ForkSettings.DEFAULT_TOOLBAR_ANIM_DURATION,
            range = 0f..600f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, it.toString()) },
            stepSize = 20,
        )
    },
    Setting(context, ForkSettings.PREF_TOOLBAR_SMOOTH_RESIZE,
        R.string.fork_toolbar_smooth_resize, R.string.fork_toolbar_smooth_resize_summary) {
        SwitchPreference(it, ForkSettings.DEFAULT_TOOLBAR_SMOOTH_RESIZE)
    },
    Setting(context, ForkSettings.PREF_TOOLBAR_OVERLAY,
        R.string.fork_toolbar_overlay, R.string.fork_toolbar_overlay_summary) {
        SwitchPreference(it, ForkSettings.DEFAULT_TOOLBAR_OVERLAY)
    },
    Setting(context, GlowPrefs.WAVE, R.string.fork_wave, R.string.fork_wave_summary) {
        SwitchPreference(it, GlowPrefs.DEFAULT_WAVE)
    },
    Setting(context, GlowPrefs.WAVE_DURATION, R.string.fork_wave_duration) {
        GlowSlider(it, GlowPrefs.DEFAULT_WAVE_DURATION, 100f..2000f, 50f, ::ms)
    },
    Setting(context, GlowPrefs.WAVE_BRIGHTNESS, R.string.fork_wave_brightness) {
        GlowSlider(it, GlowPrefs.DEFAULT_WAVE_BRIGHTNESS, 0.1f..1f, 0.05f, ::percent)
    },
    Setting(context, GlowPrefs.WAVE_THICKNESS, R.string.fork_wave_thickness) {
        GlowSlider(it, GlowPrefs.DEFAULT_WAVE_THICKNESS, 4f..120f, 2f, ::dp)
    },
    Setting(context, GlowPrefs.WAVE_SHAPE, R.string.fork_wave_shape) { setting ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        helium314.keyboard.settings.preferences.InlineChoicePreference(setting.title, setting.key, listOf(
            ctx.getString(R.string.fork_wave_shape_line) to GlowPrefs.SHAPE_LINE,
            ctx.getString(R.string.fork_wave_shape_ripple) to GlowPrefs.SHAPE_RIPPLE,
        ), GlowPrefs.SHAPE_LINE)
    },
    Setting(context, GlowPrefs.WAVE_RIPPLE_DEPTH, R.string.fork_wave_ripple_depth, R.string.fork_wave_ripple_depth_summary) {
        GlowSlider(it, GlowPrefs.DEFAULT_WAVE_RIPPLE_DEPTH, 10f..1000f, 10f, ::dp)
    },
    Setting(context, GlowPrefs.GLOW, R.string.fork_glow, R.string.fork_glow_summary) {
        SwitchPreference(it, GlowPrefs.DEFAULT_GLOW) { DynamicToolbarController.current?.onGlowSettingsChanged() }
    },
    Setting(context, GlowPrefs.GLOW_TEST_UNTIL, R.string.fork_clip_hint_test, R.string.fork_clip_hint_test_summary) { setting ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        helium314.keyboard.settings.preferences.Preference(name = setting.title, description = setting.description, onClick = {
            ctx.prefs().edit { putLong(GlowPrefs.GLOW_TEST_UNTIL, System.currentTimeMillis() + 30_000) }
            // same process as the keyboard: if it is running, apply right away
            DynamicToolbarController.current?.refreshPasteChips()
            android.widget.Toast.makeText(ctx, R.string.fork_clip_hint_test_started, android.widget.Toast.LENGTH_LONG).show()
        })
    },
    Setting(context, GlowPrefs.GLOW_POSITION, R.string.fork_glow_position) { setting ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        helium314.keyboard.settings.preferences.InlineChoicePreference(setting.title, setting.key, listOf(
            ctx.getString(R.string.fork_glow_position_top) to GlowPrefs.POSITION_TOP,
            ctx.getString(R.string.fork_glow_position_bottom) to GlowPrefs.POSITION_BOTTOM,
        ), GlowPrefs.POSITION_TOP) { DynamicToolbarController.current?.onGlowSettingsChanged() }
    },
    Setting(context, GlowPrefs.GLOW_COLOR, R.string.fork_glow_color, R.string.fork_glow_color_default) { setting ->
        ChipColorPreference(setting.title, setting.key, setting.description) {
            DynamicToolbarController.current?.onGlowSettingsChanged()
        }
    },
    Setting(context, GlowPrefs.GLOW_MAX, R.string.fork_glow_max) {
        GlowSlider(it, GlowPrefs.DEFAULT_GLOW_MAX, 0.05f..1f, 0.05f, ::percent)
    },
    Setting(context, GlowPrefs.GLOW_MIN, R.string.fork_glow_min) {
        GlowSlider(it, GlowPrefs.DEFAULT_GLOW_MIN, 0f..1f, 0.05f, ::percent)
    },
    Setting(context, GlowPrefs.GLOW_PERIOD, R.string.fork_glow_period) {
        GlowSlider(it, GlowPrefs.DEFAULT_GLOW_PERIOD, 400f..8000f, 100f, ::ms)
    },
    Setting(context, GlowPrefs.GLOW_HEIGHT, R.string.fork_glow_height) {
        GlowSlider(it, GlowPrefs.DEFAULT_GLOW_HEIGHT, 0.1f..1f, 0.05f, ::percent)
    },
    Setting(context, GlowPrefs.GLOW_WIDTH, R.string.fork_glow_width) {
        GlowSlider(it, GlowPrefs.DEFAULT_GLOW_WIDTH, 0.2f..2f, 0.05f, ::percent)
    },
    Setting(context, GlowPrefs.GLOW_DOT_SIZE, R.string.fork_glow_dot_size) {
        GlowSlider(it, GlowPrefs.DEFAULT_GLOW_DOT_SIZE, 0.4f..3f, 0.1f, ::dp)
    },
    Setting(context, GlowPrefs.GLOW_DOT_SPACING, R.string.fork_glow_dot_spacing) {
        GlowSlider(it, GlowPrefs.DEFAULT_GLOW_DOT_SPACING, 2.5f..12f, 0.5f, ::dp)
    },
    Setting(context, helium314.keyboard.fork.gif.GifClient.PREF_KLIPY_KEY, R.string.fork_gif_klipy_key) {
        GifKeyPreference(it.title, it.key, "partner.klipy.com")
    },
    Setting(context, helium314.keyboard.fork.gif.GifClient.PREF_GIPHY_KEY, R.string.fork_gif_giphy_key) {
        GifKeyPreference(it.title, it.key, "developers.giphy.com")
    },
)

/** a GIF provider's API key, stored encrypted; shown as its last characters */
@Composable
private fun GifKeyPreference(title: String, pref: String, site: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = ctx.prefs()
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var refresh by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val saved = androidx.compose.runtime.remember(refresh) { helium314.keyboard.fork.gif.GifClient.key(prefs, pref) }
    helium314.keyboard.settings.preferences.Preference(
        name = title,
        description = if (saved != null) "••••" + saved.takeLast(4) else stringResource(R.string.fork_gif_key_none, site),
        onClick = { editing = true },
    )
    if (editing) {
        var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(saved.orEmpty()) }
        helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog(
            onDismissRequest = { editing = false },
            onConfirmed = {
                if (!helium314.keyboard.fork.gif.GifClient.setKey(prefs, pref, text))
                    android.widget.Toast.makeText(ctx, R.string.fork_slate_key_failed, android.widget.Toast.LENGTH_LONG).show()
                refresh++
            },
            neutralButtonText = if (saved != null) stringResource(R.string.delete) else null,
            onNeutral = { helium314.keyboard.fork.gif.GifClient.setKey(prefs, pref, ""); refresh++ },
            title = { androidx.compose.material3.Text(title) },
            content = {
                androidx.compose.material3.OutlinedTextField(text, { text = it.trim() }, singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    supportingText = { androidx.compose.material3.Text(stringResource(R.string.fork_gif_key_help, site)) })
            },
        )
    }
}

private fun percent(v: Float) = "${(100 * v).roundToInt()}%"
private fun ms(v: Float) = "${v.roundToInt()} ms"
private fun dp(v: Float) = "${(v * 10).roundToInt() / 10f} dp"

/** slider for a glow / wave value; a running glow is redrawn with the new value right away */
@Composable
private fun GlowSlider(setting: Setting, default: Float, range: ClosedFloatingPointRange<Float>, step: Float, format: (Float) -> String) {
    InlineSliderPreference(
        name = setting.title,
        key = setting.key,
        default = default,
        range = range,
        format = format,
        step = step,
    ) { DynamicToolbarController.current?.onGlowSettingsChanged() }
}
