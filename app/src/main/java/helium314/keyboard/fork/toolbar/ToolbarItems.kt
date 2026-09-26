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

    /** Current toolbar content. Placeholders only for now. */
    val defaultItems = listOf(
        ToolbarItem(CLIPBOARD, R.drawable.ic_fork_toolbar_clipboard, R.string.fork_toolbar_clipboard),
        ToolbarItem(AI, R.drawable.ic_fork_toolbar_ai, R.string.fork_toolbar_ai),
        ToolbarItem(TRANSLATE, R.drawable.ic_fork_toolbar_translate, R.string.fork_toolbar_translate),
        ToolbarItem(MORE, R.drawable.ic_fork_toolbar_more, R.string.fork_toolbar_more),
    )
}
