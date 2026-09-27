// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.fork.clipboard.ClipAction
import helium314.keyboard.fork.clipboard.ClipPrefs
import helium314.keyboard.fork.clipboard.NotificationOtpCapture
import helium314.keyboard.fork.smart.SmartPrefs
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity

// fork: the "Tools" part of the settings: clipboard and smart chips each get their own screen (the toolbar has its own)

@Composable
fun ClipboardScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val history = prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY)
    val files = prefs.getBoolean(Settings.PREF_CLIPBOARD_USE_FILES, Defaults.PREF_CLIPBOARD_USE_FILES)
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.fork_screen_clipboard),
        settings = listOf(
            R.string.settings_category_clipboard_history,
            Settings.PREF_ENABLE_CLIPBOARD_HISTORY,
            if (history) ClipPrefs.RETENTION_HOURS else null,
            if (history) ClipPrefs.MAX_ITEMS else null,
            R.string.fork_cat_clip_panel,
            if (history) ClipPrefs.COLUMNS else null,
            if (history) ClipPrefs.PREVIEW_LINES else null,
            ClipAction.PREF,
            Settings.PREF_ABC_AFTER_CLIP,
            R.string.fork_cat_paste_chip,
            ClipPrefs.PASTE_CHIP,
            if (history) ClipPrefs.SCREENSHOTS else null,
            R.string.fork_cat_clip_files,
            if (history) Settings.PREF_CLIPBOARD_USE_FILES else null,
            if (history && files) Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT else null,
        ),
    )
}

@Composable
fun SmartChipsScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val typing = prefs.getBoolean(SmartPrefs.TYPING, true)
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.fork_screen_smart_chips),
        settings = listOf(
            R.string.fork_cat_smart_typing,
            SmartPrefs.TYPING,
            if (typing) SmartPrefs.CALC else null,
            if (typing) SmartPrefs.CURRENCY else null,
            if (typing) SmartPrefs.UNITS else null,
            SmartPrefs.TESTER,
            SmartPrefs.DEBUG,
            R.string.fork_cat_smart_clip,
            ClipPrefs.SMART_CHIPS,
            ClipPrefs.SMART_TESTER,
            R.string.fork_cat_codes,
            ClipPrefs.CODE_AUTO_OPEN,
            NotificationOtpCapture.PREF,
            ClipPrefs.CODE_TEST_UNTIL,
        ),
    )
}
