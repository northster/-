// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import androidx.compose.ui.res.stringResource
import helium314.keyboard.fork.ForkSettings
import helium314.keyboard.fork.gesture.SwipeThresholds
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun DynamicToolbarScreen(
    onClickBack: () -> Unit,
) {
    // fork: one toolbar screen, the dynamic toolbar first and HeliBoard's toolbar settings below
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? helium314.keyboard.settings.SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        helium314.keyboard.latin.utils.Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_toolbar),
        settings = listOf(
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
            R.string.fork_cat_tool_header,
            helium314.keyboard.fork.clipboard.ClipAction.PREF,
            R.string.fork_cat_classic_toolbar,
        ) + helium314.keyboard.settings.screens.classicToolbarItems(prefs)
    )
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
)
