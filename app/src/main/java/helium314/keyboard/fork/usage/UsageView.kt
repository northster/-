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
 * used part is filled with orange dots; after each line the used percentage (white, turning orange and then red as
 * the limit gets close) and the time until it resets
 * ("3h", "2d", white). It asks for a fixed width (less if the toolbar has no room), so it doesn't stretch on wide
 * screens.
 */
class UsageView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val colors = Settings.getValues().mColors
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    // the times left and the percentages: bold, a little bigger, so they can be read at a glance
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12 * density
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(KeyboardTypeface.resolve("0h", Typeface.DEFAULT), Typeface.BOLD)
        color = colors.get(ColorType.KEY_TEXT)
    }
    private val percent = Paint(text)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // bar, percentage and time; never wider, and narrower when the toolbar has less room
        val wanted = (4 * density + DOTS * 4.5f * density + percentWidth() + timeWidth()).toInt()
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(wanted, MeasureSpec.getSize(widthMeasureSpec))
            else -> wanted
        }
        setMeasuredDimension(width, getDefaultSize(suggestedMinimumHeight, heightMeasureSpec))
    }

    private fun percentWidth() = percent.measureText("100%") + 6 * density
    private fun timeWidth() = text.measureText("00h") + 6 * density

    override fun onDraw(canvas: Canvas) {
        val u = usage
        val spacing = 4.5f * density
        val r = 1.1f * density
        val timeWidth = timeWidth()
        val left = 4 * density
        val right = width - timeWidth - percentWidth()
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
            // the two text rows further apart than the dot lines, with a gap between them
            val textY = height / 2f + (if (y < height / 2f) -1 else 1) * textGap / 2
            val baseline = textY - (text.ascent() + text.descent()) / 2
            val used = limit?.let { "${(it.used * 100).roundToInt()}%" } ?: "–"
            percent.color = warningColor(limit?.used ?: 0f)
            canvas.drawText(used, width.toFloat() - timeWidth - 2 * density, baseline, percent)
            canvas.drawText(label, width.toFloat() - 2 * density, baseline, text)
        }
    }

    /** white up to half used, then towards orange (80 %) and red (100 %) */
    private fun warningColor(used: Float): Int {
        val white = colors.get(ColorType.KEY_TEXT)
        return when {
            used <= 0.5f -> white
            used <= 0.8f -> mix(white, WARN_ORANGE, (used - 0.5f) / 0.3f)
            else -> mix(WARN_ORANGE, RED, ((used - 0.8f) / 0.2f).coerceAtMost(1f))
        }
    }

    private fun mix(c1: Int, c2: Int, f: Float): Int {
        fun ch(shift: Int) = (((c1 shr shift) and 0xFF) * (1 - f) + ((c2 shr shift) and 0xFF) * f).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    companion object {
        private const val WARN_ORANGE = 0xFFFB923C.toInt()
        private const val RED = 0xFFEF4444.toInt()
        /** Claude's orange */
        private const val ORANGE = 0xFFD97757.toInt()
        /** dots in a full line */
        private const val DOTS = 18
    }
}
