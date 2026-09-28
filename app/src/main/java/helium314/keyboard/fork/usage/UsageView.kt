// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.usage

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import kotlin.math.roundToInt

/**
 * fork: Claude usage on the toolbar: two dotted lines, the 5-hour session limit above and the weekly one below. The
 * used part is filled with orange dots, and the time until each resets is written after its line ("3h", "2d").
 */
class UsageView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val colors = Settings.getValues().mColors
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    // the times left: bold yellow, a little bigger, so they can be read at a glance
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * density
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(KeyboardTypeface.resolve("0h", Typeface.DEFAULT), Typeface.BOLD)
        color = YELLOW
    }
    private var usage: ClaudeUsage.Usage? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setUsage(value: ClaudeUsage.Usage?) {
        usage = value
        contentDescription = value?.let {
            "Claude ${((it.session?.used ?: 0f) * 100).roundToInt()}% · ${((it.week?.used ?: 0f) * 100).roundToInt()}%"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val u = usage
        val spacing = 4.5f * density
        val r = 1.1f * density
        val textWidth = text.measureText("00h") + 6 * density
        val left = 4 * density
        val right = width - textWidth
        val count = ((right - left) / spacing).toInt().coerceAtLeast(2)
        val rowGap = 9 * density
        val textGap = 15 * density
        val y1 = height / 2f - rowGap / 2
        val y2 = height / 2f + rowGap / 2
        for ((y, limit) in listOf(y1 to u?.session, y2 to u?.week)) {
            val filled = ((limit?.used ?: 0f) * count).roundToInt()
            for (i in 0 until count) {
                if (i < filled) {
                    dot.color = ORANGE
                    dot.alpha = 255
                } else {
                    dot.color = colors.get(ColorType.KEY_HINT_TEXT)
                    dot.alpha = 90
                }
                canvas.drawCircle(left + i * spacing + r, y, r, dot)
            }
            val label = limit?.let { ClaudeUsage.remaining(it.resetsAt) } ?: "–"
            // the two times further apart than the dot lines, with a gap between them
            val textY = height / 2f + (if (y < height / 2f) -1 else 1) * textGap / 2
            canvas.drawText(label, width.toFloat() - 2 * density, textY - (text.ascent() + text.descent()) / 2, text)
        }
    }

    companion object {
        /** Claude's orange */
        private const val ORANGE = 0xFFD97757.toInt()
        private const val YELLOW = 0xFFFACC15.toInt()
    }
}
