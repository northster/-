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
 * White dots sweeping over the keyboard and the toolbar when the toolbar opens ([up]) or closes.
 * - Straight: a band whose front row is the brightest, fading out behind it.
 * - Ripple ([GlowPrefs.Wave.rippleDepthDp] > 0): a ring around a center hidden below the keyboard, like a ripple coming
 *   from under the screen edge. Opening, it spreads out and runs up into the toolbar; closing, it draws back into
 *   its center, so it runs down the keyboard.
 * Positions are in screen coordinates and shared by all views, so the wave runs on from the keyboard into the toolbar.
 * Every view draws it with its own screen position as origin.
 */
class DotWave(private val density: Float, private val params: GlowPrefs.Wave, val up: Boolean) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = android.graphics.Color.WHITE }
    val thicknessPx get() = params.thicknessDp * density
    val isRipple get() = params.rippleDepthDp > 0f
    val rippleDepthPx get() = params.rippleDepthDp * density
    /** straight: y of the front, ripple: radius of the ring (screen coordinates) */
    var position = 0f
    /** ripple center, screen coordinates */
    var centerX = 0f
    var centerY = 0f

    /** draw into a view whose top left is at [originX], [originY] on screen, [width] x [height] */
    fun draw(canvas: Canvas, originX: Float, originY: Float, width: Float, height: Float) {
        val spacing = params.spacingDp * density
        val r = params.dotDp * density
        val thickness = thicknessPx
        // the dot grid is the glow's, so the dots line up
        var y = spacing / 2
        while (y < height) {
            val sy = originY + y
            var x = spacing / 2
            while (x < width) {
                // how far behind the front this dot is, in band thicknesses: 0 = front (brightest), 1 = end of the band
                val behind = if (isRipple) {
                    val d = kotlin.math.hypot(originX + x - centerX, sy - centerY)
                    // spreading: the band is inside the ring; drawing back: outside
                    (if (up) position - d else d - position) / thickness
                } else {
                    (if (up) sy - position else position - sy) / thickness
                }
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

/** [DotWave] as a drawable, for the keyboard view's overlay. [originX], [originY]: the view's position on screen. */
class DotWaveDrawable(private val wave: DotWave) : Drawable() {
    var originX = 0f
    var originY = 0f

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        wave.draw(canvas, originX, originY, bounds.width().toFloat(), bounds.height().toFloat())
        canvas.restore()
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
