// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import kotlin.math.roundToInt

// fork: settings that show their control directly in the list instead of opening a dialog

/** Slider shown in the row. The value is written when the finger is lifted, so the keyboard reloads once. */
@Composable
fun InlineSliderPreference(
    name: String,
    key: String,
    default: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String = { "${(100 * it).roundToInt()}%" },
    step: Float? = null,
    onChanged: () -> Unit = { },
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val stored = prefs.getFloat(key, default)
    var value by remember(key, stored) { mutableFloatStateOf(stored) }
    val s = LocalShadcn.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
            TextButton(
                onClick = {
                    prefs.edit { remove(key) }
                    value = default
                    onChanged()
                },
                enabled = value != default,
            ) { Text(stringResource(R.string.button_default), style = MaterialTheme.typography.labelMedium) }
        }
        Slider(
            value = value,
            onValueChange = { value = step?.let { st -> (it / st).roundToInt() * st } ?: it },
            onValueChangeFinished = {
                prefs.edit { putFloat(key, value) }
                onChanged()
            },
            valueRange = range,
            modifier = Modifier.padding(end = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = s.primary,
                activeTrackColor = s.primary,
                inactiveTrackColor = s.muted,
            ),
        )
    }
}

/** Options shown as chips in the row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineChoicePreference(
    name: String,
    key: String,
    items: List<Pair<String, String>>,
    default: String,
    onChanged: (String) -> Unit = { },
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val selected = prefs.getString(key, default)
    val s = LocalShadcn.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items.forEach { (label, value) ->
                FilterChip(
                    selected = value == selected,
                    onClick = {
                        prefs.edit { putString(key, value) }
                        onChanged(value)
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = s.primary,
                        selectedLabelColor = s.primaryForeground,
                        labelColor = s.foreground,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = value == selected,
                        borderColor = s.border, selectedBorderColor = s.primary,
                    ),
                )
            }
        }
    }
}
