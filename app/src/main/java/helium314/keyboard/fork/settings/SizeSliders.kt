// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.fork.ForkLive
import helium314.keyboard.fork.ForkSettings
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import kotlin.math.roundToInt

/**
 * fork: keyboard sizes in dp.
 * Before these existed, sizes were HeliBoard scale factors (share of a fixed default height / of the screen width).
 * As long as a dp pref is absent the scale still applies, and the slider shows what it currently results in,
 * so nothing changes for the user until a slider is moved.
 */
enum class ForkSize(
    val dpKey: String,
    /** HeliBoard scale pref this dp value replaces (single size profile, so only index 0), null if there was none */
    val legacyKey: String?,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
) {
    HEIGHT(ForkSettings.PREF_KB_HEIGHT_DP, createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2), 120f..420f, 1f),
    BOTTOM_PADDING(ForkSettings.PREF_BOTTOM_PADDING_DP, createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 0, 2), 0f..48f, 0.5f),
    SIDE_PADDING(ForkSettings.PREF_SIDE_PADDING_DP, createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3), 0f..80f, 0.5f),
    KEY_GAP_H(ForkSettings.PREF_KEY_GAP_H_DP, createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2), 0f..16f, 0.5f),
    KEY_GAP_V(ForkSettings.PREF_KEY_GAP_V_DP, createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2), 0f..24f, 0.5f),
    TOP_PADDING(ForkSettings.PREF_TOP_PADDING_DP, null, 0f..40f, 0.5f),
    TOOLBAR_HEIGHT(ForkSettings.PREF_TOOLBAR_HEIGHT_DP, null, 32f..72f, 1f);

    /** dp value the keyboard uses now: the dp pref, or what the scale pref results in on this screen */
    fun effectiveDp(context: Context, prefs: SharedPreferences = context.prefs()): Float {
        ForkSettings.sizeDp(prefs, dpKey).takeIf { it >= 0 }?.let { return it }
        val res = context.resources
        val dm = res.displayMetrics
        val heightPx = if (this == HEIGHT) null else HEIGHT.effectiveDp(context, prefs) * dm.density
        val widthPx = dm.widthPixels.toFloat()
        fun fraction(id: Int, base: Float) = res.getFraction(id, 1, 1) * base
        val px = when (this) {
            HEIGHT -> ResourceUtils.getDefaultKeyboardHeight(res, false) * Settings.readHeightScale(prefs, false, false)
            BOTTOM_PADDING -> fraction(R.fraction.config_keyboard_bottom_padding_holo, heightPx!!) *
                    Settings.readBottomPaddingScale(prefs, false, false)
            SIDE_PADDING -> fraction(R.fraction.config_keyboard_left_padding, widthPx) *
                    Settings.readSidePaddingScale(prefs, false, false, false)
            KEY_GAP_H -> fraction(R.fraction.config_key_horizontal_gap_holo, widthPx) * Settings.readKeyGapScale(prefs, false, false)
            KEY_GAP_V -> fraction(R.fraction.config_key_vertical_gap_holo, heightPx!!) * Settings.readKeyGapScale(prefs, false, false)
            TOP_PADDING -> fraction(R.fraction.config_keyboard_top_padding_holo, heightPx!!)
            TOOLBAR_HEIGHT -> res.getDimension(R.dimen.fork_dynamic_toolbar_height)
        }
        return ((px / dm.density) / step).roundToInt() * step
    }

    /** back to the built-in default: removes the dp value and the old scale value */
    fun reset(prefs: SharedPreferences) = prefs.edit {
        remove(dpKey)
        // the gap scale is shared by both gaps, keep it while the other gap still depends on it
        val other = when (this@ForkSize) { KEY_GAP_H -> KEY_GAP_V; KEY_GAP_V -> KEY_GAP_H; else -> null }
        if (legacyKey != null && (other == null || prefs.contains(other.dpKey))) remove(legacyKey)
    }

    fun isDefault(prefs: SharedPreferences) = !prefs.contains(dpKey) && (legacyKey == null || !prefs.contains(legacyKey))
}

/** Slider in dp, changes are written and shown on the keyboard while dragging. */
@Composable
fun DpSliderPreference(name: String, size: ForkSize) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val stored = size.effectiveDp(ctx, prefs)
    var value by remember(size, stored) { mutableFloatStateOf(stored) }
    val s = LocalShadcn.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val shown = if (size.step < 1f && value % 1f != 0f) "%.1f".format(value) else value.roundToInt().toString()
            Text("$shown dp", style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
            TextButton(
                onClick = {
                    size.reset(prefs)
                    value = size.effectiveDp(ctx, prefs)
                    ForkLive.requestReload()
                },
                enabled = !size.isDefault(prefs),
            ) { Text(stringResource(R.string.button_default), style = MaterialTheme.typography.labelMedium) }
        }
        Slider(
            value = value.coerceIn(size.range),
            onValueChange = {
                val stepped = (it / size.step).roundToInt() * size.step
                if (stepped != value) {
                    value = stepped
                    prefs.edit { putFloat(size.dpKey, stepped) }
                    ForkLive.requestReload()
                }
            },
            valueRange = size.range,
            modifier = Modifier.padding(end = 8.dp),
            colors = SliderDefaults.colors(thumbColor = s.primary, activeTrackColor = s.primary, inactiveTrackColor = s.muted),
        )
    }
}
