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

// fork: dot matrix drawables for the paste chip, its glow and the toolbar wave, to go with the dot matrix icons and fonts

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
 * Dot grid glowing from the top (or bottom) center of the keyboard, drawn behind the keys (and on their surfaces).
 * [intensity] 0..1 is animated for a slow breathing between [GlowPrefs.Glow.minAlpha] and [GlowPrefs.Glow.maxAlpha].
 */
class DotGlowDrawable(color: Int, private val density: Float, private val params: GlowPrefs.Glow) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color } // not apply: inside it "color" would be the paint's own (black)
    var intensity = 1f
        set(value) { field = value; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val spacing = params.spacingDp * density
        val r = params.dotDp * density
        val cx = bounds.left + w / 2
        val top = bounds.top.toFloat()
        val bottom = bounds.bottom.toFloat()
        val rx = w * 0.5f * params.width
        val ry = h * params.height
        val peak = 255 * (params.minAlpha + (params.maxAlpha - params.minAlpha) * intensity)
        // rows counted from the edge the glow comes from
        var edgeDistance = spacing / 2
        while (edgeDistance < ry) {
            val y = if (params.fromBottom) bottom - edgeDistance else top + edgeDistance
            var x = bounds.left + spacing / 2
            while (x < bounds.right) {
                val dx = (x - cx) / rx
                val dy = edgeDistance / ry
                val d = sqrt(dx * dx + dy * dy)
                if (d < 1f) {
                    paint.alpha = (peak * (1f - d).pow(1.2f)).toInt().coerceIn(0, 255)
                    if (paint.alpha > 0) canvas.drawCircle(x, y, r, paint)
                }
                x += spacing
            }
            edgeDistance += spacing
        }
    }

    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * A band of white dots sweeping over a view: the brightest dots are the front, the band fades out behind it over
 * the wave's thickness. The front is straight, or an arc whose middle leads (arc radius set). [up] = moving up, so the tail is below the front.
 * Shared by the keyboard (as an overlay drawable) and the toolbar, so the wave runs on from one into the other.
 */
class DotWave(private val density: Float, private val params: GlowPrefs.Wave, val up: Boolean) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = android.graphics.Color.WHITE }
    val thicknessPx get() = params.thicknessDp * density

    /** how far the front's ends trail behind its middle (arc shape), 0 for a straight band */
    fun maxLag(width: Float): Float {
        val r = params.arcRadiusDp * density
        if (r <= 0f) return 0f
        val half = width / 2
        return if (half >= r) r else r - sqrt(r * r - half * half)
    }

    /** trailing distance of the front at [dx] from the middle: a circle of the arc radius, flat beyond it */
    private fun lag(dx: Float): Float {
        val r = params.arcRadiusDp * density
        if (r <= 0f) return 0f
        val d = kotlin.math.abs(dx)
        return if (d >= r) r else r - sqrt(r * r - d * d)
    }

    /** [front] is where the middle of the front is, [centerX] the horizontal middle, both in canvas coordinates */
    fun draw(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, front: Float, centerX: Float) {
        val spacing = params.spacingDp * density
        val r = params.dotDp * density
        val thickness = thicknessPx
        val reach = thickness + maxLag(right - left)
        // rows that can hold a part of the band
        val from = maxOf(top, if (up) front else front - reach)
        val to = minOf(bottom, if (up) front + reach else front)
        if (from >= to) return
        // rows on the same grid as the glow, so the dots line up
        var y = top + spacing / 2 + (((from - top - spacing / 2) / spacing).toInt().coerceAtLeast(0)) * spacing
        while (y <= to) {
            var x = left + spacing / 2
            while (x < right) {
                // the front at this column trails behind the middle by lag(), the band fades out behind the front
                val localFront = if (up) front + lag(x - centerX) else front - lag(x - centerX)
                val behind = (if (up) y - localFront else localFront - y) / thickness
                if (behind in 0f..1f) {
                    paint.alpha = (255 * params.brightness * (1f - behind).pow(1.6f)).toInt().coerceIn(0, 255)
                    if (paint.alpha > 0) canvas.drawCircle(x, y, r, paint)
                }
                x += spacing
            }
            y += spacing
        }
    }
}

/** [DotWave] as a drawable, for the keyboard view's overlay. [front] is in the drawable's coordinates. */
class DotWaveDrawable(private val wave: DotWave) : Drawable() {
    var front = 0f

    override fun draw(canvas: Canvas) {
        wave.draw(canvas, bounds.left.toFloat(), bounds.right.toFloat(), bounds.top.toFloat(), bounds.bottom.toFloat(), front,
            bounds.exactCenterX())
    }

    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(colorFilter: ColorFilter?) { }
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
