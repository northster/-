// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import helium314.keyboard.settings.ShadcnSwitch
import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.clickable
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.MissingDictionaryDialog
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import java.util.Locale

/**
 * fork: the languages in use as cards (name in its own language · English name, layout below, tap for the details),
 * and a row that opens the list of every language to add one ([LanguageAddScreen]).
 */
@Composable
fun LanguageScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes()
    val allCount = remember { SubtypeSettings.getAllAvailableSubtypes().map { it.locale().language }.distinct().size }
    helium314.keyboard.settings.SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.language_and_layouts_title),
        settings = emptyList(),
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 24.dp)
        ) {
            helium314.keyboard.settings.SettingsSection(stringResource(R.string.fork_languages_in_use), enabledSubtypes.map { subtype ->
                @Composable {
                    helium314.keyboard.settings.preferences.Preference(
                        name = languageTitle(subtype),
                        description = layoutName(subtype),
                        onClick = { SettingsDestination.navigateTo(SettingsDestination.Subtype + subtype.toSettingsSubtype().toPref()) },
                    ) { helium314.keyboard.latin.utils.NextScreenIcon() }
                }
            } + listOf(@Composable {
                helium314.keyboard.settings.preferences.Preference(
                    name = stringResource(R.string.fork_language_add),
                    description = stringResource(R.string.fork_language_add_summary, allCount),
                    onClick = { SettingsDestination.navigateTo(SettingsDestination.ForkLanguageAdd) },
                    icon = R.drawable.ic_plus,
                ) { helium314.keyboard.latin.utils.NextScreenIcon() }
            }))
        }
    }
}

/** "한국어 · Korean": the language in its own words, and in English when that reads differently */
private fun languageTitle(subtype: InputMethodSubtype): String {
    val locale = subtype.locale()
    val own = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    val english = locale.getDisplayName(Locale.ENGLISH)
    return if (own.isEmpty() || own == english) english.ifEmpty { subtype.displayName() } else "$own · $english"
}

private fun layoutName(subtype: InputMethodSubtype): String = runCatching {
    SubtypeLocaleUtils.getLayoutDisplayNameInSystemLocale(subtype.mainLayoutNameOrQwerty(), subtype.locale())
}.getOrDefault(subtype.displayName())

/** fork: every language, searchable; tap one to use it (or to stop using it), the arrow opens its details */
@Composable
fun LanguageAddScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val sortedSubtypes by remember { mutableStateOf(getSortedSubtypes(ctx)) }
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes()
    SearchScreen(
        onClickBack = onClickBack,
        title = {
            Column {
                Text(stringResource(R.string.fork_language_add))
                Text(stringResource(
                    R.string.text_tap_languages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        filteredItems = { term ->
            sortedSubtypes.filter { subtype ->
                subtype.displayName().replace("(", "")
                    .splitOnWhitespace().any { it.startsWith(term, true) }
            }
        },
        itemContent = { SubtypeRow(it, it in enabledSubtypes) }
    )
}

@Composable
private fun SubtypeRow(subtype: InputMethodSubtype, isEnabled: Boolean) {
    val ctx = LocalContext.current
    // local state, so the check mark follows the tap right away
    var enabled by remember(subtype, isEnabled) { mutableStateOf(isEnabled) }
    var showNoDictDialog by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val on = !enabled
                if (on && !dictsAvailable(subtype.locale(), ctx)) showNoDictDialog = true
                if (on) SubtypeSettings.addEnabledSubtype(ctx.prefs(), subtype)
                else SubtypeSettings.removeEnabledSubtype(ctx, subtype)
                enabled = on
            }
            .padding(vertical = 10.dp, horizontal = 16.dp)
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.ui.res.painterResource(helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_check)), null,
            modifier = Modifier.padding(end = 12.dp).size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(subtype.displayName(), style = MaterialTheme.typography.bodyLarge)
            val description = if (SubtypeSettings.isAdditionalSubtype(subtype)) {
                val secondaryLocales = subtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)?.split(Separators.KV)
                    ?.joinToString(", ") { it.constructLocale().localizedDisplayName(ctx.resources) }
                stringResource(R.string.custom_subtype) + (secondaryLocales?.let { "\n$it" } ?: "")
            } else null
            if (description != null)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        androidx.compose.material3.IconButton(onClick = {
            SettingsDestination.navigateTo(SettingsDestination.Subtype + subtype.toSettingsSubtype().toPref())
        }) { helium314.keyboard.latin.utils.NextScreenIcon() }
        if (showNoDictDialog)
            MissingDictionaryDialog({ showNoDictDialog = false }, subtype.locale())
    }
}

private fun dictsAvailable(locale: Locale, context: Context): Boolean {
    if (locale.language == SubtypeLocaleUtils.NO_LANGUAGE) return true // incorrect, but we don't want to show the dialog for "no language"
    val (dicts, hasInternal) = getUserAndInternalDictionaries(context, locale)
    return hasInternal || dicts.isNotEmpty()
}

// sorting by display name is still slow, even with the cache... but probably good enough
private fun getSortedSubtypes(context: Context): List<InputMethodSubtype> {
    val systemLocales = SubtypeSettings.getSystemLocales()
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
    val localesWithDictionary = DictionaryInfoUtils.getCacheDirectories(context).mapNotNull { dir ->
        if (dir.list()?.any { it.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX) } == true)
            dir.name.constructLocale()
        else null
    }

    val defaultAdditionalSubtypes = Defaults.PREF_ADDITIONAL_SUBTYPES.split(Separators.SETS).map {
        it.substringBefore(Separators.SET) to (it.substringAfter(Separators.SET) + ",AsciiCapable,EmojiCapable,isAdditionalSubtype")
    }
    fun isDefaultSubtype(subtype: InputMethodSubtype): Boolean =
        defaultAdditionalSubtypes.any { it.first == subtype.locale().language && it.second == subtype.extraValue }

    val subtypeSortComparator = compareBy<InputMethodSubtype>(
        { it !in enabledSubtypes },
        { it.locale() !in localesWithDictionary },
        { it.locale() !in systemLocales},
        { !(SubtypeSettings.isAdditionalSubtype(it) && !isDefaultSubtype(it) ) },
        {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.languageTag == SubtypeLocaleUtils.NO_LANGUAGE
            else it.locale == SubtypeLocaleUtils.NO_LANGUAGE
        },
        { it.displayName() }
    )
    return SubtypeSettings.getAllAvailableSubtypes().sortedWith(subtypeSortComparator)
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            LanguageScreen { }
        }
    }
}
