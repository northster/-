// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs

/**
 * fork: dot matrix icons (ic_dot_*) or plain ones (ic_plain_*, Material Symbols, scripts/plain_icons.py). The text
 * font is not affected. Every ic_dot_ icon is shown through [of].
 */
object DotIcons {
    const val PREF = "fork_dot_icons"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.prefs()
    }

    fun enabled() = prefs?.getBoolean(PREF, true) ?: true

    /** [dot] (an ic_dot_ drawable) or its plain counterpart */
    @JvmStatic
    @DrawableRes
    fun of(@DrawableRes dot: Int): Int = if (enabled()) dot else PLAIN[dot] ?: dot

    private val PLAIN = mapOf(
        R.drawable.ic_dot_backspace to R.drawable.ic_plain_backspace,
        R.drawable.ic_dot_check to R.drawable.ic_plain_check,
        R.drawable.ic_dot_clipboard to R.drawable.ic_plain_clipboard,
        R.drawable.ic_dot_close to R.drawable.ic_plain_close,
        R.drawable.ic_dot_copy to R.drawable.ic_plain_copy,
        R.drawable.ic_dot_cut to R.drawable.ic_plain_cut,
        R.drawable.ic_dot_emoji_activities to R.drawable.ic_plain_emoji_activities,
        R.drawable.ic_dot_emoji_emoticons to R.drawable.ic_plain_emoji_emoticons,
        R.drawable.ic_dot_emoji_flags to R.drawable.ic_plain_emoji_flags,
        R.drawable.ic_dot_emoji_food to R.drawable.ic_plain_emoji_food,
        R.drawable.ic_dot_emoji_nature to R.drawable.ic_plain_emoji_nature,
        R.drawable.ic_dot_emoji_objects to R.drawable.ic_plain_emoji_objects,
        R.drawable.ic_dot_emoji_people to R.drawable.ic_plain_emoji_people,
        R.drawable.ic_dot_emoji_recents to R.drawable.ic_plain_emoji_recents,
        R.drawable.ic_dot_emoji_symbols to R.drawable.ic_plain_emoji_symbols,
        R.drawable.ic_dot_emoji_travel to R.drawable.ic_plain_emoji_travel,
        R.drawable.ic_dot_enter to R.drawable.ic_plain_enter,
        R.drawable.ic_dot_gif to R.drawable.ic_plain_gif,
        R.drawable.ic_dot_globe to R.drawable.ic_plain_globe,
        R.drawable.ic_dot_keyboard to R.drawable.ic_plain_keyboard,
        R.drawable.ic_dot_left to R.drawable.ic_plain_left,
        R.drawable.ic_dot_more to R.drawable.ic_plain_more,
        R.drawable.ic_dot_paste to R.drawable.ic_plain_paste,
        R.drawable.ic_dot_pin to R.drawable.ic_plain_pin,
        R.drawable.ic_dot_pin_card to R.drawable.ic_plain_pin_card,
        R.drawable.ic_dot_pin_card_filled to R.drawable.ic_plain_pin_card_filled,
        R.drawable.ic_dot_pin_filled to R.drawable.ic_plain_pin_filled,
        R.drawable.ic_dot_question to R.drawable.ic_plain_question,
        R.drawable.ic_dot_redo to R.drawable.ic_plain_redo,
        R.drawable.ic_dot_right to R.drawable.ic_plain_right,
        R.drawable.ic_dot_search to R.drawable.ic_plain_search,
        R.drawable.ic_dot_select_all to R.drawable.ic_plain_select_all,
        R.drawable.ic_dot_select_word to R.drawable.ic_plain_select_word,
        R.drawable.ic_dot_settings to R.drawable.ic_plain_settings,
        R.drawable.ic_dot_shift to R.drawable.ic_plain_shift,
        R.drawable.ic_dot_shift_filled to R.drawable.ic_plain_shift_filled,
        R.drawable.ic_dot_shift_locked to R.drawable.ic_plain_shift_locked,
        R.drawable.ic_dot_smile to R.drawable.ic_plain_smile,
        R.drawable.ic_dot_space to R.drawable.ic_plain_space,
        R.drawable.ic_dot_sparkles to R.drawable.ic_plain_sparkles,
        R.drawable.ic_dot_star to R.drawable.ic_plain_star,
        R.drawable.ic_dot_star_filled to R.drawable.ic_plain_star_filled,
        R.drawable.ic_dot_translate to R.drawable.ic_plain_translate,
        R.drawable.ic_dot_trash to R.drawable.ic_plain_trash,
        R.drawable.ic_dot_undo to R.drawable.ic_plain_undo,
        R.drawable.ic_dot_zap to R.drawable.ic_plain_zap,
    )
}
