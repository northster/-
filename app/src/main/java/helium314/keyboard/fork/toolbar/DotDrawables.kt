// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.pow
import kotlin.math.sqrt

// fork: dot matrix drawables for the paste chip and its hints, to go with the dot matrix icons and fonts

/** Rounded pill filled with [fill], outlined by a row of dots in [dotColor] instead of a line. */
class DotBorderDrawable(
    private val fill: Int,
    private val dotColor: Int,
    private val density: Float,
    private val cornerDp: Float = 15f,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val dotR = 1.1f * density
        rect.set(bounds)
        canvas.drawRoundRect(rect, cornerDp * density, cornerDp * density, fillPaint)
        // dots on a line inset by their radius, evenly spread over the whole outline
        rect.inset(dotR, dotR)
        path.reset()
        val corner = (cornerDp * density - dotR).coerceAtLeast(0f)
        path.addRoundRect(rect, corner, corner, Path.Direction.CW)
        val measure = PathMeasure(path, true)
        val length = measure.length
        if (length <= 0f) return
        val count = (length / (4f * density)).toInt().coerceAtLeast(4)
        val step = length / count
        val pos = FloatArray(2)
        for (i in 0 until count) {
            measure.getPosTan(i * step, pos, null)
            canvas.drawCircle(pos[0], pos[1], dotR, dotPaint)
        }
    }

    override fun setAlpha(alpha: Int) { fillPaint.alpha = alpha; dotPaint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fillPaint.colorFilter = colorFilter; dotPaint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Dot grid glowing from the top center of the keyboard, drawn behind the keys (only visible between them).
 * [intensity] 0..1 is animated for a slow breathing.
 */
class DotGlowDrawable(private val color: Int, private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color } // not apply: inside it "color" would be the paint's own (black)
    var intensity = 1f
        set(value) { field = value; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val spacing = 4.5f * density
        val r = 1.1f * density
        val cx = bounds.left + w / 2
        val top = bounds.top.toFloat()
        val rx = w * 0.5f
        val ry = h * 0.8f
        var y = top + spacing / 2
        while (y < top + ry) {
            var x = bounds.left + spacing / 2
            while (x < bounds.right) {
                val dx = (x - cx) / rx
                val dy = (y - top) / ry
                val d = sqrt(dx * dx + dy * dy)
                if (d < 1f) {
                    paint.alpha = (220 * intensity * (1f - d).pow(1.2f)).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x, y, r, paint)
                }
                x += spacing
            }
            y += spacing
        }
    }

    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A single dot in the top left corner of the keyboard, drawn over the keys. */
class CornerDotDrawable(private val color: Int, private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color } // not apply: inside it "color" would be the paint's own (black)
    var intensity = 1f
        set(value) { field = value; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        paint.alpha = (255 * intensity).toInt().coerceIn(0, 255)
        canvas.drawCircle(bounds.left + 10f * density, bounds.top + 8f * density, 3.5f * density, paint)
    }

    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Vertical scrollbar made of dots on the right edge of a list: faint track, bright thumb. */
class DotScrollbarDecoration(private val color: Int, private val density: Float) :
    androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color } // not apply: inside it "color" would be the paint's own (black)

    override fun onDrawOver(c: Canvas, parent: androidx.recyclerview.widget.RecyclerView,
                            state: androidx.recyclerview.widget.RecyclerView.State) {
        val range = parent.computeVerticalScrollRange()
        val extent = parent.computeVerticalScrollExtent()
        if (range <= extent || extent <= 0) return
        val offset = parent.computeVerticalScrollOffset()
        val spacing = 4f * density
        val r = 1f * density
        val x = parent.width - 3f * density
        val top = 4f * density
        val length = parent.height - 8f * density
        val count = (length / spacing).toInt() + 1
        val thumbDots = (count * extent.toFloat() / range).toInt().coerceIn(2, count)
        val first = ((count - thumbDots) * offset.toFloat() / (range - extent)).toInt().coerceIn(0, count - thumbDots)
        for (i in 0 until count) {
            paint.alpha = if (i in first until first + thumbDots) 230 else 50
            c.drawCircle(x, top + i * spacing, r, paint)
        }
    }
}
