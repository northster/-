// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * The toolbar shown above the keyboard. It only draws buttons, state and animation are handled
 * by [DynamicToolbarController].
 */
class DynamicToolbarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setItems(items: List<ToolbarItem>, onClick: (ToolbarItem) -> Unit) {
        row.removeAllViews()
        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.MAIN_BACKGROUND) // same as keyboard, looks like the keyboard grows
        val ripple = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }.resourceId
        for (item in items) {
            val button = ImageButton(context).apply {
                setImageResource(item.icon)
                contentDescription = context.getString(item.label)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                if (ripple != 0) setBackgroundResource(ripple) else background = null
                setOnClickListener { onClick(item) }
                tag = item.id
            }
            colors.setColor(button, ColorType.TOOL_BAR_KEY)
            row.addView(button, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }
}
