// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * fork: a saved keyboard theme ("theme file"). Every key uses the same shape, only the enter key
 * (bottom right) has its own color and corner radius.
 * Field order follows the order in the editor: board, keys, enter key, accent & popups, toolbar, shape.
 * Colors are ARGB ints, sizes are dp.
 */
@Serializable
data class DtTheme(
    val id: String,
    val name: String,
    // board
    val background: Int,
    // keys
    val key: Int,
    val keyText: Int,
    val hintText: Int,
    val functionalKey: Int,
    val functionalText: Int,
    /** null = derived from the key color */
    val pressed: Int? = null,
    val border: Int,
    // enter key
    val enterKey: Int,
    val enterIcon: Int,
    // accent & popups
    val accent: Int,
    val popup: Int,
    val popupText: Int,
    // toolbar
    val toolbarIcon: Int,
    // shape
    val keyRadius: Float = 5f,
    val borderWidth: Float = 0f,
    val enterRadius: Float = 5f,
) {
    companion object {
        const val SAMSUNG_DARK_ID = "samsung_dark"
        const val SAMSUNG_LIGHT_ID = "samsung_light"

        // colors measured from a Samsung keyboard screenshot (dark), light is an approximation
        val SAMSUNG_DARK = DtTheme(
            id = SAMSUNG_DARK_ID, name = "Samsung dark",
            background = 0xFF0A0A0A.toInt(),
            key = 0xFF303030.toInt(), keyText = 0xFFFFFFFF.toInt(), hintText = 0xFF9E9E9E.toInt(),
            functionalKey = 0xFF1B1B1B.toInt(), functionalText = 0xFFFFFFFF.toInt(),
            border = 0x00000000,
            enterKey = 0xFF1B1B1B.toInt(), enterIcon = 0xFFFFFFFF.toInt(),
            accent = 0xFF3E91FF.toInt(), popup = 0xFF3A3A3A.toInt(), popupText = 0xFFFFFFFF.toInt(),
            toolbarIcon = 0xFFE0E0E0.toInt(),
        )
        val SAMSUNG_LIGHT = DtTheme(
            id = SAMSUNG_LIGHT_ID, name = "Samsung light",
            background = 0xFFF0F0F0.toInt(),
            key = 0xFFFFFFFF.toInt(), keyText = 0xFF1A1A1A.toInt(), hintText = 0xFF7A7A7A.toInt(),
            functionalKey = 0xFFDADADA.toInt(), functionalText = 0xFF1A1A1A.toInt(),
            border = 0x00000000,
            enterKey = 0xFFDADADA.toInt(), enterIcon = 0xFF1A1A1A.toInt(),
            accent = 0xFF3E91FF.toInt(), popup = 0xFFFFFFFF.toInt(), popupText = 0xFF1A1A1A.toInt(),
            toolbarIcon = 0xFF404040.toInt(),
        )
        val BUILT_IN = listOf(SAMSUNG_DARK, SAMSUNG_LIGHT)
    }
}

/** Saved themes and which one is used, in the device protected preferences. */
object DtThemeStore {
    private const val PREF_THEMES = "fork_themes"
    const val PREF_ACTIVE = "fork_theme_active"
    const val PREF_ACTIVE_DARK = "fork_theme_active_dark"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun all(prefs: SharedPreferences): List<DtTheme> {
        val stored = prefs.getString(PREF_THEMES, null) ?: return DtTheme.BUILT_IN
        return runCatching { json.decodeFromString<List<DtTheme>>(stored) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: DtTheme.BUILT_IN
    }

    fun all(context: Context) = all(context.prefs())

    fun get(prefs: SharedPreferences, id: String?): DtTheme? = all(prefs).firstOrNull { it.id == id }

    private fun write(prefs: SharedPreferences, themes: List<DtTheme>) =
        prefs.edit { putString(PREF_THEMES, json.encodeToString(themes)) }

    fun save(prefs: SharedPreferences, theme: DtTheme) {
        val themes = all(prefs).toMutableList()
        val i = themes.indexOfFirst { it.id == theme.id }
        if (i >= 0) themes[i] = theme else themes.add(theme)
        write(prefs, themes)
    }

    fun duplicate(prefs: SharedPreferences, theme: DtTheme, newName: String): DtTheme {
        val copy = theme.copy(id = UUID.randomUUID().toString(), name = newName)
        save(prefs, copy)
        return copy
    }

    /** the last theme can't be deleted */
    fun delete(prefs: SharedPreferences, id: String) {
        val themes = all(prefs).filterNot { it.id == id }
        if (themes.isEmpty()) return
        write(prefs, themes)
        if (prefs.getString(PREF_ACTIVE, null) == id) prefs.edit { remove(PREF_ACTIVE) }
        if (prefs.getString(PREF_ACTIVE_DARK, null) == id) prefs.edit { remove(PREF_ACTIVE_DARK) }
    }

    fun activeId(prefs: SharedPreferences, dark: Boolean): String =
        if (dark) prefs.getString(PREF_ACTIVE_DARK, null) ?: DtTheme.SAMSUNG_DARK_ID
        else prefs.getString(PREF_ACTIVE, null) ?: DtTheme.SAMSUNG_LIGHT_ID

    fun setActive(prefs: SharedPreferences, id: String, dark: Boolean) =
        prefs.edit { putString(if (dark) PREF_ACTIVE_DARK else PREF_ACTIVE, id) }

    /** theme to draw with, falls back to a built-in one if the selected file was deleted */
    fun active(prefs: SharedPreferences, dark: Boolean): DtTheme {
        val themes = all(prefs)
        return themes.firstOrNull { it.id == activeId(prefs, dark) }
            ?: themes.firstOrNull { it.id == if (dark) DtTheme.SAMSUNG_DARK_ID else DtTheme.SAMSUNG_LIGHT_ID }
            ?: themes.first()
    }
}
