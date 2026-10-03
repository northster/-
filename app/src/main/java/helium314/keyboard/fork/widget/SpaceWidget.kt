// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import helium314.keyboard.keyboard.KeyboardTypeface

/**
 * fork: the popup widget shown on the spacebar instead of its icon (Settings > popup widgets, off by default). Only
 * shown, the spacebar keeps working as a spacebar: the widget is switched and tapped on the toolbar.
 */
object SpaceWidget {
    const val PREF = "fork_widget_on_space"

    /** [text] in up to two lines, centered in a key of [width] x [height], small and dimmed */
    fun draw(canvas: Canvas, text: String, width: Int, height: Int, paint: Paint, color: Int, textSize: Float) {
        val margin = height * 0.25f
        val maxWidth = width - 2 * margin
        if (maxWidth <= 0) return
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = textSize
        paint.typeface = KeyboardTypeface.resolve(text, Typeface.DEFAULT)
        paint.color = color
        paint.textScaleX = 1f
        paint.clearShadowLayer()
        val lines = twoLines(text.replace('\n', ' ').trim(), paint, maxWidth)
        val lineHeight = paint.fontSpacing
        val top = height / 2f - lineHeight * lines.size / 2f
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, width / 2f, top + lineHeight * i - paint.ascent(), paint)
        }
    }

    /** the text broken at a space (or anywhere) into at most two lines that fit, the second ends in … if cut */
    private fun twoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        var count = paint.breakText(text, true, maxWidth, null)
        val space = text.lastIndexOf(' ', count)
        if (space > count / 2) count = space
        val first = text.substring(0, count).trim()
        var rest = text.substring(count).trim()
        if (paint.measureText(rest) > maxWidth) {
            val fit = paint.breakText(rest, true, maxWidth - paint.measureText("…"), null)
            rest = rest.substring(0, fit).trimEnd() + "…"
        }
        return listOf(first, rest)
    }
}
