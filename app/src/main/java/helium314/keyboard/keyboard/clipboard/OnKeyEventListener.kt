// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

interface OnKeyEventListener {

    fun onKeyDown(clipId: Long)

    fun onKeyUp(clipId: Long)

    // fork: card buttons and long press
    fun onLongPressClip(clipId: Long)

    fun onTogglePin(clipId: Long)

    fun onDeleteClip(clipId: Long)

}