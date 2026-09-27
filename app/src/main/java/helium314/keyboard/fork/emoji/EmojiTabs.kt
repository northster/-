// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.emoji

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.keyboard.emoji.EmojiCategory
import helium314.keyboard.latin.R

/** fork: order and visibility of the emoji category tabs, stored as "NAME:true;NAME:false;..." */
internal object EmojiTabs {
    const val PREF = "fork_emoji_tab_order"

    /** all categories in the stored order, with their visibility; categories missing in the pref are appended, shown */
    fun all(prefs: SharedPreferences): List<Pair<EmojiCategory.Category, Boolean>> {
        val stored = prefs.getString(PREF, null)?.split(";")?.mapNotNull { entry ->
            val name = entry.substringBefore(":")
            val category = EmojiCategory.Category.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            category to (entry.substringAfter(":", "true") != "false")
        }.orEmpty()
        return stored + EmojiCategory.Category.entries.filter { c -> stored.none { it.first == c } }.map { it to true }
    }

    fun shown(prefs: SharedPreferences) = all(prefs).filter { it.second }.map { it.first }

    fun save(prefs: SharedPreferences, tabs: List<Pair<EmojiCategory.Category, Boolean>>) =
        prefs.edit { putString(PREF, tabs.joinToString(";") { "${it.first.name}:${it.second}" }) }

    fun reset(prefs: SharedPreferences) = prefs.edit { remove(PREF) }

    /** dot matrix tab icon */
    fun icon(category: EmojiCategory.Category) = when (category) {
        EmojiCategory.Category.RECENTS -> R.drawable.ic_dot_emoji_recents
        EmojiCategory.Category.SMILEYS -> R.drawable.ic_dot_smile
        EmojiCategory.Category.PEOPLE -> R.drawable.ic_dot_emoji_people
        EmojiCategory.Category.NATURE -> R.drawable.ic_dot_emoji_nature
        EmojiCategory.Category.FOOD -> R.drawable.ic_dot_emoji_food
        EmojiCategory.Category.TRAVEL_PLACES -> R.drawable.ic_dot_emoji_travel
        EmojiCategory.Category.ACTIVITIES -> R.drawable.ic_dot_emoji_activities
        EmojiCategory.Category.OBJECTS -> R.drawable.ic_dot_emoji_objects
        EmojiCategory.Category.SYMBOLS -> R.drawable.ic_dot_emoji_symbols
        EmojiCategory.Category.FLAGS -> R.drawable.ic_dot_emoji_flags
        EmojiCategory.Category.EMOTICONS -> R.drawable.ic_dot_emoji_emoticons
    }
}
