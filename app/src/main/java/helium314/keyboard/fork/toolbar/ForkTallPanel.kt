// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

/** fork: a panel in place of the letters (clipboard, emoji) that can be made taller from the toolbar header */
interface ForkTallPanel {
    /** height without the extension, in px */
    fun forkNormalHeight(): Int

    /** current height, in px */
    fun forkCurrentHeight(): Int

    /** total height in px, 0 for the normal height */
    fun setForkExpandedHeight(height: Int)
}
