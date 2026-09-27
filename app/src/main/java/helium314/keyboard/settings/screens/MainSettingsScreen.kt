// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.ImeSetupState
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsSection
import helium314.keyboard.settings.SetupAlert
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

/**
 * fork: shadcn style main screen.
 *  - setup state (keyboard not enabled / not selected) is shown as an alert on top instead of a wizard
 *  - a text field to try the keyboard right away (at the bottom of every settings screen, see SearchScreen)
 *  - entries grouped into Input / Look / Toolbar / More, each with a short description
 */
@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickDynamicToolbar: () -> Unit,
    onClickClipboard: () -> Unit,
    onClickSmartChips: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickThemeColors: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
    ) {
        val ctx = LocalContext.current
        // fork: computed once, not again on every recomposition
        val subtypeNames = androidx.compose.runtime.remember {
            SubtypeSettings.getEnabledSubtypes(true).joinToString(", ") { it.displayName() }
        }
        val setupState by SettingsActivity.imeSetupState.collectAsState()
        val appName = stringResource(R.string.english_ime_name)
        // fork: the bottom inset (keyboard, dynamic toolbar) is taken by SearchScreen while laying out. Reading
        // Scaffold's innerPadding here recomposed the whole screen on every frame the keyboard or toolbar moved, on
        // the main thread the keyboard shares with this activity. The keyboard test field is at the bottom of every
        // settings screen.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            when (setupState) {
                ImeSetupState.NOT_ENABLED -> SetupAlert(
                    title = stringResource(R.string.fork_setup_not_enabled_title),
                    description = stringResource(R.string.fork_setup_not_enabled_desc, appName),
                    actionText = stringResource(R.string.fork_setup_not_enabled_action),
                    onAction = { openImeSettings(ctx) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                ImeSetupState.NOT_CURRENT -> SetupAlert(
                    title = stringResource(R.string.fork_setup_not_current_title),
                    description = stringResource(R.string.fork_setup_not_current_desc, appName),
                    actionText = stringResource(R.string.fork_setup_not_current_action),
                    onAction = { showImePicker(ctx) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                ImeSetupState.OK -> {}
            }

            SettingsSection(stringResource(R.string.fork_main_input), listOfNotNull(
                entry(R.string.language_and_layouts_title, subtypeNames,
                    R.drawable.ic_settings_languages, onClickLanguage),
                entry(R.string.settings_screen_preferences, stringResource(R.string.fork_desc_preferences),
                    R.drawable.ic_settings_preferences, onClickPreferences),
                entry(R.string.settings_screen_correction, stringResource(R.string.fork_desc_correction),
                    R.drawable.ic_settings_correction, onClickTextCorrection),
                entry(R.string.dictionary_settings_category, stringResource(R.string.fork_desc_dictionaries),
                    R.drawable.ic_dictionary, onClickDictionaries),
                if (JniUtils.sHaveGestureLib)
                    entry(R.string.settings_screen_gesture, stringResource(R.string.fork_desc_gesture),
                        R.drawable.ic_settings_gesture, onClickGestureTyping)
                else null,
                // we don't even show the menu if data gathering phase ended more than 2 weeks ago
                if (JniUtils.sHaveGestureLib && System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS)
                    entry(R.string.gesture_data_screen, null, R.drawable.ic_settings_gesture, onClickDataGathering)
                else null,
            ))
            SettingsSection(stringResource(R.string.fork_main_look), listOf(
                entry(R.string.fork_theme_colors, stringResource(R.string.fork_desc_theme_colors),
                    R.drawable.ic_settings_appearance, onClickThemeColors),
                entry(R.string.settings_screen_appearance, stringResource(R.string.fork_desc_appearance),
                    R.drawable.ic_settings_appearance, onClickAppearance),
                entry(R.string.settings_screen_secondary_layouts, stringResource(R.string.fork_desc_layouts),
                    R.drawable.ic_settings_layout, onClickLayouts),
            ))
            SettingsSection(stringResource(R.string.fork_main_tools), listOf(
                // fork: the toolbar, and the tools on it with their own screens
                entry(R.string.settings_screen_toolbar, stringResource(R.string.fork_desc_dynamic_toolbar),
                    R.drawable.ic_settings_toolbar, onClickDynamicToolbar),
                entry(R.string.fork_screen_clipboard, stringResource(R.string.fork_desc_clipboard),
                    R.drawable.ic_dot_clipboard, onClickClipboard),
                entry(R.string.fork_screen_smart_chips, stringResource(R.string.fork_desc_smart_chips),
                    R.drawable.ic_dot_sparkles, onClickSmartChips),
            ))
            SettingsSection(stringResource(R.string.fork_main_more), listOf(
                entry(R.string.settings_screen_advanced, stringResource(R.string.fork_desc_advanced),
                    R.drawable.ic_settings_advanced, onClickAdvanced),
                entry(R.string.settings_screen_about, stringResource(R.string.fork_desc_about),
                    R.drawable.ic_settings_about, onClickAbout),
            ))
        }
    }
}

private fun entry(title: Int, description: String?, @DrawableRes icon: Int, onClick: () -> Unit): @Composable () -> Unit = {
    Preference(
        name = stringResource(title),
        description = description,
        onClick = onClick,
        icon = icon
    ) { NextScreenIcon() }
}

private fun openImeSettings(ctx: Context) {
    ctx.startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun showImePicker(ctx: Context) {
    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
