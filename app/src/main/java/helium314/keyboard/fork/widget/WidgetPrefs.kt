// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

/** fork: the popup widgets on the toolbar (right of the tool buttons), switched with a sideways swipe */
object WidgetPrefs {
    const val USAGE = "fork_widget_usage"
    const val TRIVIA = "fork_widget_trivia"
    const val EMOJI = "fork_widget_emoji"
    /** experiment: half of the trivia written by Gemini (checked with a web search) instead of all from Wikipedia */
    const val TRIVIA_AI = "fork_widget_trivia_ai"
    /** settings entry: get new trivia now (nothing stored) */
    const val TRIVIA_NOW = "fork_widget_trivia_now"
    /** where emoji ideas come from: [EMOJI_LOCAL] (built-in dictionary), [EMOJI_LOCAL_AI] (dictionary, ✨ asks
     *  Gemini about the whole text), [EMOJI_AI] (Gemini whenever typing pauses) */
    const val EMOJI_SOURCE = "fork_widget_emoji_source"
    const val EMOJI_LOCAL = "local"
    const val EMOJI_LOCAL_AI = "local_ai"
    const val EMOJI_AI = "ai"

    /** the widget shown last, it comes back */
    const val PAGE = "fork_widget_page"

    const val ID_USAGE = "usage"
    const val ID_TRIVIA = "trivia"
    const val ID_EMOJI = "emoji"
}
