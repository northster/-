// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        helium314.keyboard.settings.preferences.StepSliderPreference(setting.title, ClipPrefs.retentionChoices,
            { ClipPrefs.retentionHours(ctx.prefs()) }, {
            when {
                it == 0 -> ctx.getString(R.string.settings_no_limit)
                it % 24 == 0 -> ctx.getString(R.string.fork_unit_days, it / 24)
                else -> ctx.getString(R.string.fork_unit_hours, it)
            }
        }, ClipPrefs.DEFAULT_RETENTION_HOURS) {
            ctx.prefs().edit { putInt(setting.key, it) }
            ClipboardDao.getInstance(ctx)?.clearOldClips(true)
        }
    },
    Setting(context, ClipPrefs.MAX_ITEMS, R.string.fork_clip_max_items) { setting ->
        val ctx = LocalContext.current
        helium314.keyboard.settings.preferences.StepSliderPreference(setting.title, ClipPrefs.maxItemChoices,
            { ClipPrefs.maxItems(ctx.prefs()) }, {
            if (it == 0) ctx.getString(R.string.settings_no_limit) else it.toString()
        }, ClipPrefs.DEFAULT_MAX_ITEMS) {
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
    Setting(context, helium314.keyboard.fork.clipboard.LinkPreview.PREF_ENABLED, R.string.fork_link_preview, R.string.fork_link_preview_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, ClipPrefs.SMART_CHIPS, R.string.fork_smart_chips, R.string.fork_smart_chips_summary) {
        SwitchPreference(it, ClipPrefs.DEFAULT_SMART_CHIPS)
    },
    Setting(context, ClipPrefs.CODE_AUTO_OPEN, R.string.fork_code_auto_open, R.string.fork_code_auto_open_summary) {
        SwitchPreference(it, ClipPrefs.DEFAULT_CODE_AUTO_OPEN)
    },
    Setting(context, helium314.keyboard.fork.clipboard.NotificationOtpCapture.PREF, R.string.fork_notification_codes,
        R.string.fork_notification_codes_summary) { setting ->
        val ctx = LocalContext.current
        val access = helium314.keyboard.fork.clipboard.NotificationOtpCapture.hasAccess(ctx)
        helium314.keyboard.settings.preferences.SwitchPreference(
            name = setting.title,
            key = setting.key,
            default = false,
            description = if (access || !ctx.prefs().getBoolean(setting.key, false)) setting.description
                else ctx.getString(R.string.fork_notification_codes_no_access),
        ) { on ->
            if (!on) helium314.keyboard.fork.clipboard.NotificationOtpBus.clear()
            else if (!helium314.keyboard.fork.clipboard.NotificationOtpCapture.hasAccess(ctx)) {
                // the system screen where notification access is granted
                Toast.makeText(ctx, R.string.fork_notification_codes_grant, Toast.LENGTH_LONG).show()
                runCatching {
                    ctx.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
            }
        }
    },
    Setting(context, ClipPrefs.CODE_TEST_UNTIL, R.string.fork_code_test, R.string.fork_code_test_summary) { setting ->
        val ctx = LocalContext.current
        helium314.keyboard.settings.preferences.Preference(name = setting.title, description = setting.description, onClick = {
            ctx.prefs().edit { putLong(ClipPrefs.CODE_TEST_UNTIL, System.currentTimeMillis() + 30_000) }
            // same process as the keyboard: if it is running, apply right away
            helium314.keyboard.fork.toolbar.DynamicToolbarController.current?.refreshPasteChips()
            Toast.makeText(ctx, R.string.fork_code_test_started, Toast.LENGTH_LONG).show()
        })
    },
    Setting(context, ClipPrefs.SMART_TESTER, R.string.fork_smart_tester, R.string.fork_smart_tester_summary) { setting ->
        SmartChipTester(setting.title, setting.description)
    },
    Setting(context, ClipPrefs.CHIP_BORDER, R.string.fork_chip_border) {
        SwitchPreference(it, true)
    },
    Setting(context, ClipPrefs.CHIP_BORDER_STYLE, R.string.fork_chip_border_style) { setting ->
        val ctx = LocalContext.current
        helium314.keyboard.settings.preferences.InlineChoicePreference(setting.title, setting.key, listOf(
            ctx.getString(R.string.fork_chip_border_dots) to ClipPrefs.BORDER_DOTS,
            ctx.getString(R.string.fork_chip_border_dashed) to ClipPrefs.BORDER_DASHED,
            ctx.getString(R.string.fork_chip_border_solid) to ClipPrefs.BORDER_SOLID,
        ), ClipPrefs.BORDER_DOTS)
    },
    Setting(context, ClipPrefs.CHIP_BORDER_COLOR, R.string.fork_chip_border_color, R.string.fork_chip_color_auto_enter) { setting ->
        ChipColorPreference(setting.title, setting.key, setting.description)
    },
    Setting(context, ClipPrefs.CHIP_BG_COLOR, R.string.fork_chip_bg_color, R.string.fork_chip_color_auto_key) { setting ->
        ChipColorPreference(setting.title, setting.key, setting.description)
    },
    Setting(context, ClipPrefs.CHIP_TEXT_COLOR, R.string.fork_chip_text_color, R.string.fork_chip_color_auto_key) { setting ->
        ChipColorPreference(setting.title, setting.key, setting.description)
    },
    Setting(context, ClipPrefs.CHIP_RADIUS, R.string.fork_chip_radius) { setting ->
        val ctx = LocalContext.current
        InlineSliderPreference(
            name = setting.title,
            key = setting.key,
            default = ClipPrefs.DEFAULT_CHIP_RADIUS,
            range = 0f..30f,
            format = { ctx.getString(R.string.fork_unit_dp, it.toInt().toString()) },
            step = 1f,
        )
    },
    Setting(context, helium314.keyboard.fork.smart.SmartPrefs.TYPING, R.string.fork_smart_typing, R.string.fork_smart_typing_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, helium314.keyboard.fork.smart.SmartPrefs.CALC, R.string.fork_smart_calc, R.string.fork_smart_calc_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, helium314.keyboard.fork.smart.SmartPrefs.CURRENCY, R.string.fork_smart_currency, R.string.fork_smart_currency_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, helium314.keyboard.fork.smart.SmartPrefs.UNITS, R.string.fork_smart_units, R.string.fork_smart_units_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, helium314.keyboard.fork.ForkSettings.PREF_SPACE_LONG_PRESS_MS, R.string.fork_space_long_press,
        R.string.fork_space_long_press_summary) { setting ->
        val ctx = LocalContext.current
        InlineSliderPreference(
            name = setting.title,
            key = setting.key,
            default = 0f,
            range = 0f..1000f,
            format = { if (it <= 0f) ctx.getString(R.string.fork_space_long_press_default)
                else ctx.getString(R.string.abbreviation_unit_milliseconds, it.toInt().toString()) },
            step = 25f,
        )
    },
    Setting(context, helium314.keyboard.fork.ForkSettings.PREF_CURSOR_CHAR_STEP_DP, R.string.fork_cursor_char_step,
        R.string.fork_cursor_step_summary) { setting ->
        val ctx = LocalContext.current
        InlineSliderPreference(
            name = setting.title,
            key = setting.key,
            default = helium314.keyboard.fork.ForkSettings.DEFAULT_CURSOR_CHAR_STEP_DP,
            range = 3f..30f,
            format = { ctx.getString(R.string.fork_unit_dp, it.toInt().toString()) },
            step = 1f,
        )
    },
    Setting(context, helium314.keyboard.fork.ForkSettings.PREF_CURSOR_LINE_STEP_DP, R.string.fork_cursor_line_step) { setting ->
        val ctx = LocalContext.current
        InlineSliderPreference(
            name = setting.title,
            key = setting.key,
            default = helium314.keyboard.fork.ForkSettings.DEFAULT_CURSOR_LINE_STEP_DP,
            range = 10f..80f,
            format = { ctx.getString(R.string.fork_unit_dp, it.toInt().toString()) },
            step = 2f,
        )
    },
    Setting(context, helium314.keyboard.fork.ForkSettings.PREF_VIRTUAL_CARET, R.string.fork_virtual_caret,
        R.string.fork_virtual_caret_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, helium314.keyboard.fork.smart.SmartPrefs.TESTER, R.string.fork_smart_typed_tester,
        R.string.fork_smart_typed_tester_summary) { setting ->
        SmartTypedTester(setting.title, setting.description)
    },
    Setting(context, helium314.keyboard.fork.smart.SmartPrefs.DEBUG, R.string.fork_smart_debug, R.string.fork_smart_debug_summary) {
        SwitchPreference(it, false)
    },
    Setting(context, helium314.keyboard.fork.ForkSettings.PREF_EMOJI_DICT_LINK, R.string.fork_emoji_dict,
        R.string.fork_emoji_dict_summary) { setting ->
        helium314.keyboard.settings.preferences.Preference(name = setting.title, description = setting.description,
            onClick = { helium314.keyboard.settings.SettingsDestination.navigateTo(helium314.keyboard.settings.SettingsDestination.Dictionaries) },
        ) { helium314.keyboard.latin.utils.NextScreenIcon() }
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

/** paste or type a message and see what the smart chips find in it, nothing is stored */
@Composable
private fun SmartChipTester(title: String, description: String?) {
    val s = LocalShadcn.current
    val ctx = LocalContext.current
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ctx.getString(R.string.fork_clip_code_test_text)) }
    val actions = androidx.compose.runtime.remember(text) { ClipPrefs.findActions(text) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = MaterialTheme.shapes.medium,
            minLines = 2,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = s.ring,
                unfocusedBorderColor = s.input,
                cursorColor = s.foreground,
            ),
        )
        val found = if (actions.isEmpty()) stringResource(R.string.fork_smart_found_nothing)
            else actions.joinToString("\n") { action ->
                val kind = when (action) {
                    is ClipPrefs.SmartAction.Code -> R.string.fork_smart_kind_code
                    is ClipPrefs.SmartAction.Link -> R.string.fork_smart_kind_link
                    is ClipPrefs.SmartAction.Phone -> R.string.fork_smart_kind_phone
                    is ClipPrefs.SmartAction.Email -> R.string.fork_smart_kind_email
                }
                "${ctx.getString(kind)}: ${action.value}"
            }
        Text(found, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

/** a chip color: swatch, or "theme" when not set; the picker's default button goes back to the theme color */
@Composable
internal fun ChipColorPreference(title: String, key: String, autoDescription: String?, onChanged: () -> Unit = { }) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val set = prefs.contains(key)
    val color = prefs.getInt(key, android.graphics.Color.GRAY)
    var showDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    helium314.keyboard.settings.preferences.Preference(
        name = title,
        description = if (set) String.format("#%06X", 0xFFFFFF and color) else autoDescription,
        onClick = { showDialog = true },
    ) {
        if (set) androidx.compose.foundation.layout.Box(
            Modifier.padding(end = 8.dp).size(28.dp)
                .background(androidx.compose.ui.graphics.Color(color), MaterialTheme.shapes.small)
                .border(1.dp, LocalShadcn.current.border, MaterialTheme.shapes.small)
        )
    }
    if (showDialog)
        helium314.keyboard.settings.dialogs.ColorPickerDialog(
            onDismissRequest = { showDialog = false },
            initialColor = color,
            title = title,
            showDefault = set,
            onDefault = { prefs.edit { remove(key) }; onChanged() },
            onConfirmed = { prefs.edit { putInt(key, it) }; onChanged() },
        )
}

/** type a sum / amount / measure and see what the typing smart chips find, with the phone's own regex engine */
@Composable
private fun SmartTypedTester(title: String, description: String?) {
    val s = LocalShadcn.current
    val ctx = LocalContext.current
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("10달러") }
    val smart = androidx.compose.runtime.remember(text) { helium314.keyboard.fork.smart.SmartPrefs.context(ctx) }
    val hit = androidx.compose.runtime.remember(text, smart) { helium314.keyboard.fork.smart.SmartSuggest.detect(text, smart) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = s.ring,
                unfocusedBorderColor = s.input,
                cursorColor = s.foreground,
            ),
        )
        val result = hit?.let { "${it.query} → ${it.result}" } ?: stringResource(R.string.fork_smart_found_nothing)
        val rates = if (smart.rates == null) " · " + stringResource(R.string.fork_smart_no_rates) else ""
        Text(result + rates, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        // where and when the exchange rates are from
        androidx.compose.runtime.LaunchedEffect(Unit) { helium314.keyboard.fork.smart.CurrencyRates.refreshIfOld(ctx) }
        val fetched = helium314.keyboard.fork.smart.CurrencyRates.fetchedAt(ctx)
        if (fetched > 0) {
            val time = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(fetched))
            val source = helium314.keyboard.fork.smart.CurrencyRates.source(ctx).ifEmpty { "ExchangeRate-API" }
            Text(stringResource(R.string.fork_smart_rates_from, source, time), style = MaterialTheme.typography.bodySmall,
                color = s.mutedForeground, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
