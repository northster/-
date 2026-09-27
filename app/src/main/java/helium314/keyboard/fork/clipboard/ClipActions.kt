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
    /** show only the pinned clips (toggle) */
    PINNED(null, true),
    CLEAR_CLIPBOARD(ToolbarKey.CLEAR_CLIPBOARD, true),
    SELECT_ALL(ToolbarKey.SELECT_ALL, false),
    SELECT_WORD(ToolbarKey.SELECT_WORD, false),
    COPY(ToolbarKey.COPY, false),
    CUT(ToolbarKey.CUT, false),
    PASTE(ToolbarKey.PASTE, false),
    UNDO(ToolbarKey.UNDO, false),
    LEFT(ToolbarKey.LEFT, false),
    RIGHT(ToolbarKey.RIGHT, false);

    fun icon(context: Context): Drawable? = when (this) {
        SEARCH -> ContextCompat.getDrawable(context, R.drawable.ic_dot_search)
        PINNED -> ContextCompat.getDrawable(context, R.drawable.ic_dot_pin)
        else -> KeyboardIconsSet.instance.getNewDrawable(toolbarKey!!.name, context)
    }

    fun label(context: Context): String = when (this) {
        SEARCH -> context.getString(R.string.fork_clip_search)
        PINNED -> context.getString(R.string.fork_clip_pinned_only)
        else -> toolbarKey!!.name.lowercase().getStringResourceOrName("", context)
    }

    /** key code sent for this action, null for [SEARCH] and [PINNED] which are handled by the toolbar */
    fun code(): Int? = toolbarKey?.let { getCodeForToolbarKey(it) }

    companion object {
        const val PREF = "fork_clip_actions"
        private const val PINNED_ADDED = "fork_clip_actions_pinned_added"

        fun enabled(prefs: SharedPreferences): List<ClipAction> {
            var stored = prefs.getString(PREF, null) ?: return entries.filter { it.defaultOn }
            // the pinned clips button came later: add it once to a list chosen before
            if (!prefs.getBoolean(PINNED_ADDED, false)) {
                if (PINNED.name !in stored.split(';')) stored = "$stored;${PINNED.name}"
                prefs.edit { putString(PREF, stored); putBoolean(PINNED_ADDED, true) }
            }
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
