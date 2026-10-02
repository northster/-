// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

/** fork: the popup widgets on the toolbar (right of the tool buttons), switched with a sideways swipe */
object WidgetPrefs {
    const val USAGE = "fork_widget_usage"
    const val TRIVIA = "fork_widget_trivia"
    const val EMOJI = "fork_widget_emoji"
    /** hours between two batches of trivia */
    const val TRIVIA_HOURS = "fork_widget_trivia_hours"
    const val DEFAULT_TRIVIA_HOURS = 6f
    /** settings entry: get new trivia now (nothing stored) */
    const val TRIVIA_NOW = "fork_widget_trivia_now"
    /** the widget shown last, it comes back */
    const val PAGE = "fork_widget_page"

    const val ID_USAGE = "usage"
    const val ID_TRIVIA = "trivia"
    const val ID_EMOJI = "emoji"
}
