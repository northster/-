// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils

/** fork: the clipboard panel has no bottom key row, see ClipboardHistoryView */
const val FORK_NO_BOTTOM_ROW = true

class ClipboardLayoutParams(ctx: Context) {

    private val keyVerticalGap: Int
    private val keyHorizontalGap: Int
    private val listHeight: Int
    val bottomRowKeyboardHeight: Int

    init {
        val res = ctx.resources
        val sv = Settings.getValues()
        val defaultKeyboardHeight = ResourceUtils.getSecondaryKeyboardHeight(res, sv)
        val defaultKeyboardWidth = ResourceUtils.getKeyboardWidth(ctx, sv)

        val density = res.displayMetrics.density
        // fork: same vertical sizes as the main keyboard (ResourceUtils.getForkVerticalMetrics), gap in dp if set
        val forkV = if (sv.mIsFloatingKeyboard) null else ResourceUtils.getForkVerticalMetrics(res, sv)
        keyVerticalGap = forkV?.get(2) ?: (res.getFraction(R.fraction.config_key_vertical_gap_holo,
                defaultKeyboardHeight, defaultKeyboardHeight) * sv.mKeyGapScale).toInt()
        keyHorizontalGap = if (sv.mForkKeyGapHDp >= 0) (sv.mForkKeyGapHDp * density).toInt()
            else (res.getFraction(R.fraction.config_key_horizontal_gap_holo,
                defaultKeyboardWidth, defaultKeyboardWidth) * sv.mKeyGapScale).toInt()
        val bottomPadding = forkV?.get(1) ?: (res.getFraction(R.fraction.config_keyboard_bottom_padding_holo,
                defaultKeyboardHeight, defaultKeyboardHeight) * sv.mBottomPaddingScale).toInt()
        val topPadding = forkV?.get(0) ?: res.getFraction(R.fraction.config_keyboard_top_padding_holo,
                defaultKeyboardHeight, defaultKeyboardHeight).toInt()

        val rowCount = KeyboardParams.DEFAULT_KEYBOARD_ROWS + if (sv.mShowsNumberRow) 1 else 0
        bottomRowKeyboardHeight = (defaultKeyboardHeight - bottomPadding - topPadding) / rowCount - keyVerticalGap / 2
        // height calculation is not good enough, probably also because keyboard top padding might be off by a pixel (see KeyboardParser)
        val offset = 1.25f * res.displayMetrics.density * sv.mKeyboardHeightScale
        // fork: no bottom row (ABC / space / delete), back to the letters is on the toolbar header
        listHeight = if (FORK_NO_BOTTOM_ROW) defaultKeyboardHeight - bottomPadding
            else defaultKeyboardHeight - bottomRowKeyboardHeight - bottomPadding + offset.toInt()
    }

    fun setListProperties(recycler: RecyclerView) {
        (recycler.layoutParams as FrameLayout.LayoutParams).apply {
            height = listHeight
            recycler.layoutParams = this
        }
    }

    fun setItemProperties(view: View) {
        (view.layoutParams as RecyclerView.LayoutParams).apply {
            topMargin = keyHorizontalGap / 2
            bottomMargin = keyVerticalGap / 2
            marginStart = keyHorizontalGap / 2
            marginEnd = keyHorizontalGap / 2
            view.layoutParams = this
        }
    }
}
