// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import helium314.keyboard.latin.R

/**
 * A button on the dynamic toolbar. New features (clipboard panel, AI, WM Keyboard style tools...)
 * are added by creating an item here and handling its [id] in [DynamicToolbarController.onItemClicked].
 */
data class ToolbarItem(
    val id: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
)

object ToolbarItems {
    const val CLIPBOARD = "clipboard"
    const val AI = "ai"
    const val TRANSLATE = "translate"
    const val MORE = "more"
    const val GIF = "gif"
    const val UNDO = "undo"
    const val REDO = "redo"

    /** Current toolbar content. Placeholders only for now. */
    val defaultItems get() = listOf(
        ToolbarItem(CLIPBOARD, helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_clipboard), R.string.fork_toolbar_clipboard),
        // AI commands (?fix, text replacers ...)
        ToolbarItem(AI, helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_sparkles), R.string.fork_toolbar_ai),
        ToolbarItem(TRANSLATE, helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_translate), R.string.fork_toolbar_translate),
        ToolbarItem(GIF, helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_gif), R.string.fork_toolbar_gif),
        // the settings button (MORE) is gone: the settings open from the app icon
    )

    /** always at the right end of the toolbar, after a divider */
    val endItems get() = listOf(
        ToolbarItem(UNDO, helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_undo), R.string.undo),
        ToolbarItem(REDO, helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_redo), R.string.redo),
    )
}
