// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
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
    // sub-options only while the switch they belong to is on
    val swipe = prefs.getBoolean(ForkSettings.PREF_TOOLBAR_SWIPE_ENABLED, ForkSettings.DEFAULT_TOOLBAR_SWIPE_ENABLED)
    val wave = GlowPrefs.waveEnabled(prefs)
    val glow = GlowPrefs.glowEnabled(prefs)
    val aiGlow = prefs.getBoolean(GlowPrefs.AI_GLOW, true)
    val aiStyle = prefs.getString(GlowPrefs.AI_GLOW_STYLE, GlowPrefs.AI_STYLE_SWEEP)
    val autofill = prefs.getBoolean(DynamicToolbarController.PREF_AUTOFILL, true)
    val items = listOfNotNull(
            R.string.fork_cat_gesture,
            ForkSettings.PREF_TOOLBAR_SWIPE_ENABLED,
            ForkSettings.PREF_SWIPE_MIN_DISTANCE_DP.takeIf { swipe },
            ForkSettings.PREF_SWIPE_MIN_VELOCITY.takeIf { swipe },
            ForkSettings.PREF_SWIPE_MAX_ANGLE.takeIf { swipe },
            ForkSettings.PREF_SWIPE_HORIZONTAL_REJECT_DP.takeIf { swipe },
            ForkSettings.PREF_SWIPE_MAX_DURATION.takeIf { swipe },
            ForkSettings.PREF_ONE_HANDED_SWIPE,
            R.string.fork_cat_animation,
            ForkSettings.PREF_TOOLBAR_ANIM_DURATION,
            ForkSettings.PREF_TOOLBAR_SMOOTH_RESIZE,
            ForkSettings.PREF_TOOLBAR_OVERLAY,
            R.string.fork_cat_wave,
            GlowPrefs.WAVE,
            GlowPrefs.WAVE_DURATION.takeIf { wave },
            GlowPrefs.WAVE_BRIGHTNESS.takeIf { wave },
            GlowPrefs.WAVE_THICKNESS.takeIf { wave },
            GlowPrefs.WAVE_SHAPE.takeIf { wave },
            GlowPrefs.WAVE_RIPPLE_DEPTH.takeIf {
                wave && prefs.getString(GlowPrefs.WAVE_SHAPE, GlowPrefs.SHAPE_LINE) == GlowPrefs.SHAPE_RIPPLE
            },
            R.string.fork_cat_glow,
            GlowPrefs.GLOW,
            GlowPrefs.GLOW_TEST_UNTIL.takeIf { glow },
            GlowPrefs.GLOW_POSITION.takeIf { glow },
            GlowPrefs.GLOW_COLOR.takeIf { glow },
            GlowPrefs.GLOW_MAX.takeIf { glow },
            GlowPrefs.GLOW_MIN.takeIf { glow },
            GlowPrefs.GLOW_PERIOD.takeIf { glow },
            GlowPrefs.GLOW_HEIGHT.takeIf { glow },
            GlowPrefs.GLOW_WIDTH.takeIf { glow },
            GlowPrefs.GLOW_DOT_SIZE.takeIf { glow },
            GlowPrefs.GLOW_DOT_SPACING.takeIf { glow },
            R.string.fork_cat_claude_usage,
            helium314.keyboard.fork.usage.ClaudeUsage.PREF_SESSION_KEY,
            R.string.fork_cat_ai_glow,
            GlowPrefs.AI_GLOW,
            GlowPrefs.AI_GLOW_STYLE.takeIf { aiGlow },
            // the options that do something in the chosen style
            GlowPrefs.AI_GLOW_IN.takeIf { aiGlow },
            GlowPrefs.AI_GLOW_BRIGHTNESS.takeIf { aiGlow },
            GlowPrefs.AI_GLOW_PERIOD.takeIf { aiGlow && aiStyle != GlowPrefs.AI_STYLE_RISE },
            GlowPrefs.AI_GLOW_DEPTH.takeIf { aiGlow && aiStyle != GlowPrefs.AI_STYLE_BOTTOM },
            GlowPrefs.AI_GLOW_BOTTOM_DEPTH.takeIf { aiGlow && aiStyle == GlowPrefs.AI_STYLE_BOTTOM },
            R.string.fork_cat_autofill,
            DynamicToolbarController.PREF_AUTOFILL,
            DynamicToolbarController.PREF_AUTOFILL_OPEN.takeIf { autofill },
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
    Setting(context, ForkSettings.PREF_ONE_HANDED_SWIPE, R.string.fork_one_handed_swipe, R.string.fork_one_handed_swipe_summary) {
        SwitchPreference(it, true)
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
    Setting(context, helium314.keyboard.fork.usage.ClaudeUsage.PREF_SESSION_KEY, R.string.fork_claude_key) {
        ClaudeKeyPreference(it.title)
    },
    Setting(context, GlowPrefs.AI_GLOW, R.string.fork_ai_glow, R.string.fork_ai_glow_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, GlowPrefs.AI_GLOW_STYLE, R.string.fork_ai_glow_style) { setting ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        helium314.keyboard.settings.preferences.InlineChoicePreference(setting.title, setting.key, listOf(
            ctx.getString(R.string.fork_ai_glow_style_sweep) to GlowPrefs.AI_STYLE_SWEEP,
            ctx.getString(R.string.fork_ai_glow_style_rise) to GlowPrefs.AI_STYLE_RISE,
            ctx.getString(R.string.fork_ai_glow_style_fade) to GlowPrefs.AI_STYLE_FADE,
            ctx.getString(R.string.fork_ai_glow_style_bottom) to GlowPrefs.AI_STYLE_BOTTOM,
        ), GlowPrefs.AI_STYLE_SWEEP)
    },
    Setting(context, GlowPrefs.AI_GLOW_IN, R.string.fork_ai_glow_in) {
        GlowSlider(it, GlowPrefs.DEFAULT_AI_GLOW_IN, 200f..4000f, 100f, ::ms)
    },
    Setting(context, GlowPrefs.AI_GLOW_BOTTOM_DEPTH, R.string.fork_ai_glow_depth) { setting ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        GlowSlider(setting, GlowPrefs.DEFAULT_AI_GLOW_BOTTOM_DEPTH, 2f..40f, 1f) { ctx.getString(R.string.fork_ai_glow_rows, it.roundToInt()) }
    },
    Setting(context, GlowPrefs.AI_GLOW_BRIGHTNESS, R.string.fork_ai_glow_brightness) {
        GlowSlider(it, GlowPrefs.DEFAULT_AI_GLOW_BRIGHTNESS, 0.1f..1f, 0.05f, ::percent)
    },
    Setting(context, GlowPrefs.AI_GLOW_PERIOD, R.string.fork_ai_glow_period) {
        GlowSlider(it, GlowPrefs.DEFAULT_AI_GLOW_PERIOD, 800f..10000f, 100f, ::ms)
    },
    Setting(context, GlowPrefs.AI_GLOW_DEPTH, R.string.fork_ai_glow_depth) { setting ->
        val ctx = androidx.compose.ui.platform.LocalContext.current
        GlowSlider(setting, GlowPrefs.DEFAULT_AI_GLOW_DEPTH, 1f..8f, 1f) { ctx.getString(R.string.fork_ai_glow_rows, it.roundToInt()) }
    },
    Setting(context, DynamicToolbarController.PREF_AUTOFILL, R.string.fork_autofill, R.string.fork_autofill_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, DynamicToolbarController.PREF_AUTOFILL_OPEN, R.string.fork_autofill_open, R.string.fork_autofill_open_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, helium314.keyboard.fork.translate.TranslatePanel.PREF_LANGUAGE, R.string.fork_translate_language) {
        TranslateChoice(it, helium314.keyboard.fork.translate.TranslatePanel.KIND_LANGUAGE)
    },
    Setting(context, helium314.keyboard.fork.translate.TranslatePanel.PREF_STYLE, R.string.fork_translate_style) {
        TranslateChoice(it, helium314.keyboard.fork.translate.TranslatePanel.KIND_STYLE)
    },
    Setting(context, helium314.keyboard.fork.translate.TranslatePanel.PREF_PURPOSE, R.string.fork_translate_purpose) {
        TranslateChoice(it, helium314.keyboard.fork.translate.TranslatePanel.KIND_PURPOSE)
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
internal fun GifKeyPreference(title: String, pref: String, site: String) {
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

/** a translation default (the panel starts with it and remembers the last used), and the entries added by the user */
@Composable
private fun TranslateChoice(setting: Setting, kind: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = ctx.prefs()
    val panel = helium314.keyboard.fork.translate.TranslatePanel
    var refresh by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val options = androidx.compose.runtime.remember(refresh) { panel.options(prefs, kind) }
    val custom = androidx.compose.runtime.remember(refresh) { panel.custom(prefs, kind) }
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-2) } // -1 = new
    androidx.compose.foundation.layout.Column {
        helium314.keyboard.settings.preferences.InlineChoicePreference(setting.title, setting.key,
            options.map { it.label to it.label }, options.first().label)
        custom.forEachIndexed { i, option ->
            helium314.keyboard.settings.preferences.Preference(name = option.label,
                description = option.prompt.ifBlank { stringResource(R.string.fork_translate_custom_no_prompt) },
                onClick = { editing = i })
        }
        helium314.keyboard.settings.preferences.Preference(name = stringResource(R.string.fork_translate_custom_add),
            onClick = { editing = -1 }, icon = R.drawable.ic_plus)
    }
    if (editing >= -1) {
        val original = custom.getOrNull(editing)
        var label by androidx.compose.runtime.remember(editing) { androidx.compose.runtime.mutableStateOf(original?.label.orEmpty()) }
        var prompt by androidx.compose.runtime.remember(editing) { androidx.compose.runtime.mutableStateOf(original?.prompt.orEmpty()) }
        helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog(
            onDismissRequest = { editing = -2 },
            onConfirmed = {
                val list = custom.toMutableList()
                val entry = helium314.keyboard.fork.translate.TranslatePanel.Option(label.trim(), prompt.trim())
                if (editing >= 0) list[editing] = entry else list.add(entry)
                panel.saveCustom(prefs, kind, list)
                refresh++
            },
            checkOk = { label.isNotBlank() },
            neutralButtonText = if (original != null) stringResource(R.string.delete) else null,
            onNeutral = {
                panel.saveCustom(prefs, kind, custom.filterIndexed { i, _ -> i != editing })
                refresh++
            },
            title = { androidx.compose.material3.Text(setting.title) },
            content = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.OutlinedTextField(label, { label = it }, singleLine = true,
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        label = { androidx.compose.material3.Text(stringResource(R.string.fork_translate_custom_name)) })
                    androidx.compose.material3.OutlinedTextField(prompt, { prompt = it }, minLines = 2,
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { androidx.compose.material3.Text(stringResource(R.string.fork_translate_custom_prompt)) },
                        supportingText = { androidx.compose.material3.Text(stringResource(R.string.fork_translate_custom_prompt_help)) })
                }
            },
        )
    }
}

/** Tools > GIF: the KLIPY / GIPHY keys */
@Composable
fun GifScreen(onClickBack: () -> Unit) {
    val items = listOf(helium314.keyboard.fork.gif.GifClient.PREF_KLIPY_KEY, helium314.keyboard.fork.gif.GifClient.PREF_GIPHY_KEY)
    SearchSettingsScreen(onClickBack = onClickBack, title = stringResource(R.string.fork_screen_gif), settings = items) {
        androidx.compose.foundation.layout.Column {
            androidx.compose.material3.Text(stringResource(R.string.fork_gif_screen_summary),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = helium314.keyboard.latin.utils.LocalShadcn.current.mutedForeground,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            helium314.keyboard.settings.SettingsSections(items)
        }
    }
}

/** Tools > translation: what the translation panel starts with */
@Composable
fun TranslateScreen(onClickBack: () -> Unit) {
    val items = listOf(
        helium314.keyboard.fork.translate.TranslatePanel.PREF_LANGUAGE,
        helium314.keyboard.fork.translate.TranslatePanel.PREF_STYLE,
        helium314.keyboard.fork.translate.TranslatePanel.PREF_PURPOSE,
    )
    SearchSettingsScreen(onClickBack = onClickBack, title = stringResource(R.string.fork_screen_translate), settings = items) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            androidx.compose.material3.Text(stringResource(R.string.fork_translate_screen_summary),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = helium314.keyboard.latin.utils.LocalShadcn.current.mutedForeground,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            helium314.keyboard.settings.SettingsSections(items)
        }
    }
}

/** the claude.ai session key for the usage lines on the toolbar, with what the last check found */
@Composable
private fun ClaudeKeyPreference(title: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = ctx.prefs()
    val usage = helium314.keyboard.fork.usage.ClaudeUsage
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // the key changed (re-check) / a check finished (show it)
    var refresh by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var checked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val hasKey = androidx.compose.runtime.remember(refresh) { usage.hasKey(prefs) }
    androidx.compose.runtime.LaunchedEffect(refresh) {
        // check right away, the result shows below
        if (hasKey) usage.refreshIfOld(ctx, force = true) {
            checked++
            DynamicToolbarController.current?.setToolbarItems()
        }
    }
    if (checked < 0) return
    val cached = usage.cached(prefs)
    val status = usage.status(prefs)
    val description = when {
        !hasKey -> stringResource(R.string.fork_claude_key_none)
        status.isNotEmpty() -> stringResource(R.string.fork_claude_key_error, status)
        cached != null -> stringResource(R.string.fork_claude_key_ok,
            ((cached.session?.used ?: 0f) * 100).roundToInt(), usage.remaining(cached.session?.resetsAt ?: 0),
            ((cached.week?.used ?: 0f) * 100).roundToInt(), usage.remaining(cached.week?.resetsAt ?: 0))
        else -> stringResource(R.string.fork_claude_key_checking)
    }
    helium314.keyboard.settings.preferences.Preference(name = title, description = description, onClick = { editing = true })
    if (editing) {
        var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog(
            onDismissRequest = { editing = false },
            onConfirmed = {
                if (!usage.setKey(prefs, text))
                    android.widget.Toast.makeText(ctx, R.string.fork_slate_key_failed, android.widget.Toast.LENGTH_LONG).show()
                DynamicToolbarController.current?.setToolbarItems()
                refresh++
            },
            checkOk = { text.isNotBlank() },
            neutralButtonText = if (hasKey) stringResource(R.string.delete) else null,
            onNeutral = {
                usage.setKey(prefs, "")
                DynamicToolbarController.current?.setToolbarItems()
                refresh++
            },
            title = { androidx.compose.material3.Text(title) },
            scrollContent = true,
            content = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(stringResource(R.string.fork_claude_key_help),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.OutlinedTextField(text, { text = it.trim() }, singleLine = true,
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 12.dp),
                        placeholder = { androidx.compose.material3.Text("sk-ant-sid01-…") })
                }
            },
        )
    }
}

