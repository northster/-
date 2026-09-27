// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import helium314.keyboard.fork.settings.KeyboardPreviewScaffold
import helium314.keyboard.fork.settings.ForkThemeSettings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import helium314.keyboard.fork.ForkSettings
import helium314.keyboard.fork.ForkLive
import helium314.keyboard.fork.settings.DpSliderPreference
import helium314.keyboard.fork.settings.ForkSize
import helium314.keyboard.fork.settings.ForkFonts
import helium314.keyboard.fork.settings.ForkFontPreference
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.settings.SettingsSection
import helium314.keyboard.settings.SettingsSections
import helium314.keyboard.settings.preferences.InlineSliderPreference
import helium314.keyboard.settings.preferences.InlineChoicePreference
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.dialogs.ColorThemePickerDialog
import helium314.keyboard.settings.dialogs.CustomizeIconsDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.BackgroundImagePref
import helium314.keyboard.settings.preferences.CustomFontPreference
import helium314.keyboard.settings.preferences.KeyboardScalePreference
import helium314.keyboard.settings.preferences.TextInputPreference
import helium314.keyboard.latin.utils.previewDark
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val dayNightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)
    // fork: every option is visible (no conditional items), sliders and choices are shown inline,
    //  background images are removed, one size profile for every screen (ForkSettings.SINGLE_SIZE_PROFILE)
    val items = listOf(
        // fork: theme, colors, day / night and icons are in the separate "Theme & colors" screen
        R.string.fork_cat_size,
        Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX,
        Settings.PREF_BOTTOM_ROW_SCALE_PREFIX,
        ForkSettings.PREF_TOP_PADDING_DP,
        Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX,
        Settings.PREF_SIDE_PADDING_SCALE_PREFIX,
        Settings.PREF_KEY_GAP_SCALE_PREFIX,
        ForkSettings.PREF_KEY_GAP_V_DP,
        ForkSettings.PREF_TOOLBAR_HEIGHT_DP,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD,
        Settings.PREF_SPLIT_SPACER_SCALE_PREFIX,
        R.string.fork_cat_text,
        Settings.PREF_SPACE_BAR_TEXT,
        Settings.PREF_FONT_SCALE,
        Settings.PREF_HINT_FONT_SCALE,
        Settings.PREF_EMOJI_FONT_SCALE,
        ForkSettings.PREF_EMOJI_COLUMNS,
        helium314.keyboard.fork.emoji.EmojiTabs.PREF,
        Settings.PREF_EMOJI_KEY_FIT,
        Settings.PREF_EMOJI_SKIN_TONE,
        ForkSettings.PREF_EMOJI_DICT_LINK,
        // fork: fonts by script
        R.string.fork_cat_fonts,
        SettingsWithoutKey.CUSTOM_FONT,
        ForkFonts.KOREAN,
        SettingsWithoutKey.CUSTOM_EMOJI_FONT,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
        settings = items,
    ) {
        KeyboardPreviewScaffold { SettingsSections(items) }
    }
}

fun createAppearanceSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_THEME_STYLE, R.string.theme_style) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val items = KeyboardTheme.STYLES.map {
            it.getStringResourceOrName("style_name_", ctx) to it
        }
        InlineChoicePreference(
            setting.title,
            setting.key,
            items,
            Defaults.PREF_THEME_STYLE
        ) {
            if (it != KeyboardTheme.STYLE_HOLO) {
                if (prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS) == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit { remove(Settings.PREF_THEME_COLORS) }
                if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT) == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit { remove(Settings.PREF_THEME_COLORS_NIGHT) }
            }
            KeyboardIconsSet.needsReload = true // only relevant for Settings.PREF_CUSTOM_ICON_NAMES
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_ICON_STYLE, R.string.icon_style) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val items = KeyboardTheme.STYLES.map { it.getStringResourceOrName("style_name_", ctx) to it }
        InlineChoicePreference(
            setting.title,
            setting.key,
            items,
            Defaults.PREF_ICON_STYLE(ctx.prefs()),
        ) {
            KeyboardIconsSet.needsReload = true // only relevant for Settings.PREF_CUSTOM_ICON_NAMES
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { setting ->
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            KeyboardIconsSet.instance.loadIcons(LocalContext.current)
            CustomizeIconsDialog(setting.key) { showDialog = false }
        }
    },
    Setting(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = prefs.getString(setting.key, Defaults.PREF_THEME_COLORS)!!.getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = false,
                default = Defaults.PREF_THEME_COLORS
            )
    },
    Setting(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        val prefs = ctx.prefs()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = prefs.getString(setting.key, Defaults.PREF_THEME_COLORS_NIGHT)!!.getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = true,
                default = Defaults.PREF_THEME_COLORS_NIGHT
            )
    },
    Setting(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) {
        SwitchPreference(it, Defaults.PREF_THEME_KEY_BORDERS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_THEME_DAY_NIGHT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_NAVBAR_COLOR)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE, R.string.customize_background_image) {
        BackgroundImagePref(it, false)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape)
    {
        BackgroundImagePref(it, true)
    },
    Setting(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD, R.string.enable_split_keyboard) {
        // fork: one setting for every screen
        SwitchPreference(it, Defaults.PREF_ENABLE_SPLIT_KEYBOARD) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SPLIT_SPACER_SCALE_PREFIX, R.string.split_spacer_scale) { setting ->
        InlineSliderPreference(
            name = setting.title,
            key = createPrefKeyForBooleanSettings(setting.key, 0, 2),
            default = Defaults.PREF_SPLIT_SPACER_SCALE[0],
            range = 0.5f..2f,
            step = 0.01f,
            live = true,
        ) { ForkLive.requestReload() }
    },
    Setting(context, Settings.PREF_KEY_GAP_SCALE_PREFIX, R.string.fork_size_key_gap_h) { setting ->
        DpSliderPreference(setting.title, ForkSize.KEY_GAP_H)
    },
    Setting(context, ForkSettings.PREF_KEY_GAP_V_DP, R.string.fork_size_key_gap_v) { setting ->
        DpSliderPreference(setting.title, ForkSize.KEY_GAP_V)
    },
    Setting(context, ForkSettings.PREF_TOP_PADDING_DP, R.string.fork_size_top_padding) { setting ->
        DpSliderPreference(setting.title, ForkSize.TOP_PADDING)
    },
    Setting(context, ForkSettings.PREF_TOOLBAR_HEIGHT_DP, R.string.fork_size_toolbar_height) { setting ->
        DpSliderPreference(setting.title, ForkSize.TOOLBAR_HEIGHT)
    },
    Setting(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, R.string.fork_size_key_height, R.string.fork_size_key_height_summary) { setting ->
        DpSliderPreference(setting.title, ForkSize.KEY_HEIGHT, setting.description)
    },
    Setting(context, Settings.PREF_BOTTOM_ROW_SCALE_PREFIX, R.string.fork_size_bottom_row) { setting ->
        DpSliderPreference(setting.title, ForkSize.BOTTOM_ROW)
    },
    Setting(context, Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, R.string.fork_size_bottom_padding) { setting ->
        DpSliderPreference(setting.title, ForkSize.BOTTOM_PADDING)
    },
    Setting(context, Settings.PREF_SIDE_PADDING_SCALE_PREFIX, R.string.fork_size_side_padding) { setting ->
        DpSliderPreference(setting.title, ForkSize.SIDE_PADDING)
    },
    Setting(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) {
        TextInputPreference(it, Defaults.PREF_SPACE_BAR_TEXT)
    },
    Setting(context, SettingsWithoutKey.CUSTOM_FONT, R.string.fork_font_latin) {
        ForkFontPreference(it, Settings.getCustomFontFile(LocalContext.current), ForkFonts.NAME_LATIN)
    },
    Setting(context, Settings.PREF_FONT_SCALE, R.string.prefs_font_scale) { setting ->
        InlineSliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_FONT_SCALE,
            range = 0.5f..1.5f,
            step = 0.01f,
            live = true,
        ) { ForkLive.requestReload() }
    },
    Setting(context, Settings.PREF_HINT_FONT_SCALE, R.string.prefs_hint_font_scale) { setting ->
        InlineSliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_HINT_FONT_SCALE,
            range = 0.5f..1.5f,
            step = 0.01f,
            live = true,
        ) { ForkLive.requestReload() }
    },
    Setting(context, SettingsWithoutKey.CUSTOM_EMOJI_FONT, R.string.fork_font_emoji) {
        ForkFontPreference(it, Settings.getCustomEmojiFontFile(LocalContext.current), ForkFonts.NAME_EMOJI)
    },
    Setting(context, ForkFonts.KOREAN, R.string.fork_font_korean) {
        ForkFontPreference(it, KeyboardTypeface.koreanFontFile(LocalContext.current), ForkFonts.NAME_KOREAN)
    },
    Setting(context, Settings.PREF_EMOJI_FONT_SCALE, R.string.prefs_emoji_font_scale) { setting ->
        InlineSliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_EMOJI_FONT_SCALE,
            range = 0.5f..1.5f,
            step = 0.01f,
            live = true,
        ) { ForkLive.requestReload() }
    },
    Setting(context, ForkSettings.PREF_EMOJI_COLUMNS, R.string.fork_emoji_columns) { setting ->
        val ctx = LocalContext.current
        helium314.keyboard.fork.settings.IntChoicePreference(setting.title, { ctx.prefs().getInt(setting.key, 0) },
            listOf(0, 6, 7, 8, 9, 10, 11), { if (it == 0) ctx.getString(R.string.fork_auto) else it.toString() }) {
            ctx.prefs().edit { putInt(setting.key, it) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_EMOJI_KEY_FIT, R.string.prefs_emoji_key_fit) {
        SwitchPreference(it, Defaults.PREF_EMOJI_KEY_FIT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_EMOJI_SKIN_TONE, R.string.prefs_emoji_skin_tone) { setting ->
        val items = listOf(
            stringResource(R.string.prefs_emoji_skin_tone_neutral) to "",
            "\uD83C\uDFFB" to "\uD83C\uDFFB",
            "\uD83C\uDFFC" to "\uD83C\uDFFC",
            "\uD83C\uDFFD" to "\uD83C\uDFFD",
            "\uD83C\uDFFE" to "\uD83C\uDFFE",
            "\uD83C\uDFFF" to "\uD83C\uDFFF"
        )
        InlineChoicePreference(setting.title, setting.key, items, Defaults.PREF_EMOJI_SKIN_TONE) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
)

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            AppearanceScreen { }
        }
    }
}
