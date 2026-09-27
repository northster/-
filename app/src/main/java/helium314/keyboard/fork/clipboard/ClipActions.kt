// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.clipboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getStringResourceOrName

/**
 * fork: buttons on the right of the toolbar header while the clipboard panel is open.
 * Only a few are on by default, the rest can be switched on in the clipboard settings.
 */
enum class ClipAction(val toolbarKey: ToolbarKey?, val defaultOn: Boolean) {
    SEARCH(null, true),
    CLEAR_CLIPBOARD(ToolbarKey.CLEAR_CLIPBOARD, true),
    SELECT_ALL(ToolbarKey.SELECT_ALL, false),
    SELECT_WORD(ToolbarKey.SELECT_WORD, false),
    COPY(ToolbarKey.COPY, false),
    CUT(ToolbarKey.CUT, false),
    PASTE(ToolbarKey.PASTE, false),
    UNDO(ToolbarKey.UNDO, false),
    LEFT(ToolbarKey.LEFT, false),
    RIGHT(ToolbarKey.RIGHT, false);

    fun icon(context: Context): Drawable? =
        if (toolbarKey == null) ContextCompat.getDrawable(context, R.drawable.sym_keyboard_search_lxx)
        else KeyboardIconsSet.instance.getNewDrawable(toolbarKey.name, context)

    fun label(context: Context): String =
        if (toolbarKey == null) context.getString(R.string.fork_clip_search)
        else toolbarKey.name.lowercase().getStringResourceOrName("", context)

    /** key code sent for this action, null for [SEARCH] which is handled by the toolbar */
    fun code(): Int? = toolbarKey?.let { getCodeForToolbarKey(it) }

    companion object {
        const val PREF = "fork_clip_actions"

        fun enabled(prefs: SharedPreferences): List<ClipAction> {
            val stored = prefs.getString(PREF, null) ?: return entries.filter { it.defaultOn }
            val names = stored.split(';').toSet()
            return entries.filter { it.name in names }
        }

        fun setEnabled(prefs: SharedPreferences, action: ClipAction, on: Boolean) {
            val set = enabled(prefs).toMutableSet()
            if (on) set.add(action) else set.remove(action)
            prefs.edit { putString(PREF, entries.filter { it in set }.joinToString(";") { it.name }) }
        }
    }
}
