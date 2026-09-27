// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.fork.clipboard.ClipAction
import helium314.keyboard.fork.clipboard.ClipPrefs
import helium314.keyboard.fork.clipboard.ScreenshotWatcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.InlineSliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference

/** fork: clipboard history settings, shown in the clipboard section of the preferences screen */
fun createForkClipboardSettings(context: Context) = listOf(
    Setting(context, helium314.keyboard.fork.ForkSettings.PREF_HIDE_COMPOSING_UNDERLINE,
        R.string.fork_hide_composing_underline, R.string.fork_hide_composing_underline_summary) {
        SwitchPreference(it, helium314.keyboard.fork.ForkSettings.DEFAULT_HIDE_COMPOSING_UNDERLINE)
    },
    Setting(context, ClipAction.PREF, R.string.fork_clip_actions, R.string.fork_clip_actions_summary) { setting ->
        ClipActionsPreference(setting.title, setting.description)
    },
    Setting(context, ClipPrefs.RETENTION_HOURS, R.string.fork_clip_retention) { setting ->
        val ctx = LocalContext.current
        IntChoicePreference(setting.title, { ClipPrefs.retentionHours(ctx.prefs()) }, ClipPrefs.retentionChoices, {
            when {
                it == 0 -> ctx.getString(R.string.settings_no_limit)
                it % 24 == 0 -> ctx.getString(R.string.fork_unit_days, it / 24)
                else -> ctx.getString(R.string.fork_unit_hours, it)
            }
        }) {
            ctx.prefs().edit { putInt(setting.key, it) }
            ClipboardDao.getInstance(ctx)?.clearOldClips(true)
        }
    },
    Setting(context, ClipPrefs.MAX_ITEMS, R.string.fork_clip_max_items) { setting ->
        val ctx = LocalContext.current
        IntChoicePreference(setting.title, { ClipPrefs.maxItems(ctx.prefs()) }, ClipPrefs.maxItemChoices, {
            if (it == 0) ctx.getString(R.string.settings_no_limit) else it.toString()
        }) {
            ctx.prefs().edit { putInt(setting.key, it) }
            ClipboardDao.getInstance(ctx)?.clearOldClips(true)
        }
    },
    Setting(context, ClipPrefs.COLUMNS, R.string.fork_clip_columns) { setting ->
        val ctx = LocalContext.current
        IntChoicePreference(setting.title, { ClipPrefs.columns(ctx.prefs()) }, listOf(1, 2, 3, 4), { it.toString() }) {
            ctx.prefs().edit { putInt(setting.key, it) }
        }
    },
    Setting(context, ClipPrefs.PREVIEW_LINES, R.string.fork_clip_preview_lines) { setting ->
        // stored as int, the slider works with a float copy
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val floatKey = setting.key + "_f"
        if (!prefs.contains(floatKey)) prefs.edit { putFloat(floatKey, ClipPrefs.previewLines(prefs).toFloat()) }
        InlineSliderPreference(
            name = setting.title,
            key = floatKey,
            default = ClipPrefs.DEFAULT_PREVIEW_LINES.toFloat(),
            range = 1f..12f,
            format = { ctx.getString(R.string.fork_unit_lines, it.toInt()) },
            step = 1f,
        ) { prefs.edit { putInt(setting.key, prefs.getFloat(floatKey, ClipPrefs.DEFAULT_PREVIEW_LINES.toFloat()).toInt()) } }
    },
    Setting(context, ClipPrefs.PASTE_CHIP, R.string.fork_clip_paste_chip, R.string.fork_clip_paste_chip_summary) {
        SwitchPreference(it, ClipPrefs.DEFAULT_PASTE_CHIP)
    },
    Setting(context, ClipPrefs.CHIP_HINT, R.string.fork_clip_chip_hint) { setting ->
        val ctx = LocalContext.current
        helium314.keyboard.settings.preferences.InlineChoicePreference(setting.title, setting.key, listOf(
            ctx.getString(R.string.fork_clip_chip_hint_glow) to ClipPrefs.HINT_GLOW,
            ctx.getString(R.string.fork_clip_chip_hint_dot) to ClipPrefs.HINT_DOT,
        ), ClipPrefs.HINT_GLOW)
    },
    Setting(context, ClipPrefs.HINT_TEST_UNTIL, R.string.fork_clip_hint_test, R.string.fork_clip_hint_test_summary) { setting ->
        val ctx = LocalContext.current
        helium314.keyboard.settings.preferences.Preference(name = setting.title, description = setting.description, onClick = {
            ctx.prefs().edit { putLong(ClipPrefs.HINT_TEST_UNTIL, System.currentTimeMillis() + 30_000) }
            Toast.makeText(ctx, R.string.fork_clip_hint_test_started, Toast.LENGTH_LONG).show()
        })
    },
    Setting(context, ClipPrefs.SCREENSHOTS, R.string.fork_clip_screenshots, R.string.fork_clip_screenshots_summary) { setting ->
        val ctx = LocalContext.current
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) ctx.prefs().edit { putBoolean(setting.key, true) }
            else Toast.makeText(ctx, R.string.fork_clip_screenshots_denied, Toast.LENGTH_LONG).show()
        }
        SwitchPreference(setting, ClipPrefs.DEFAULT_SCREENSHOTS, allowCheckedChange = { checked ->
            if (checked && !ScreenshotWatcher.hasPermission(ctx)) {
                launcher.launch(ScreenshotWatcher.permission)
                false // switched on once the permission is granted
            } else true
        })
    },
)

/** fork: which actions the toolbar header shows while the clipboard panel is open */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipActionsPreference(name: String, description: String?) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabled = ClipAction.enabled(prefs)
    val s = LocalShadcn.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        if (description != null)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            ClipAction.entries.forEach { action ->
                val on = action in enabled
                FilterChip(
                    selected = on,
                    onClick = { ClipAction.setEnabled(prefs, action, !on) },
                    label = { Text(action.label(ctx), style = MaterialTheme.typography.labelLarge) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = s.primary,
                        selectedLabelColor = s.primaryForeground,
                        labelColor = s.foreground,
                    ),
                )
            }
        }
    }
}

/** fork: a choice between numbers, as chips */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntChoicePreference(name: String, read: () -> Int, choices: List<Int>, label: (Int) -> String, onSelect: (Int) -> Unit) {
    val ctx = LocalContext.current
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val selected = read()
    val s = LocalShadcn.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            choices.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label(value), style = MaterialTheme.typography.labelLarge) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = s.primary,
                        selectedLabelColor = s.primaryForeground,
                        labelColor = s.foreground,
                    ),
                )
            }
        }
    }
}
