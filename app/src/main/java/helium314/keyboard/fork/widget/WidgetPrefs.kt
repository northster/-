// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

/** fork: the popup widgets on the toolbar (right of the tool buttons), switched with a sideways swipe */
object WidgetPrefs {
    const val USAGE = "fork_widget_usage"
    const val EMOJI = "fork_widget_emoji"
    /** where emoji ideas come from: [EMOJI_LOCAL] (built-in dictionary), [EMOJI_LOCAL_AI] (dictionary, ✨ asks
     *  Gemini about the whole text), [EMOJI_AI] (Gemini whenever typing pauses) */
    const val EMOJI_SOURCE = "fork_widget_emoji_source"
    const val EMOJI_LOCAL = "local"
    const val EMOJI_LOCAL_AI = "local_ai"
    const val EMOJI_AI = "ai"

    /** the widget shown last, it comes back */
    const val PAGE = "fork_widget_page"

    const val ID_USAGE = "usage"
    const val ID_EMOJI = "emoji"
    /** only on the spacebar: today's date and the weather (DateWeather) */
    const val ID_DATE = "date"
}
