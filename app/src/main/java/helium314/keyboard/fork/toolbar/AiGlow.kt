// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.utils.prefs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * fork: pastel glow behind the keys while an AI command or translation runs, seen through the key mask like the chip
 * glow; it breathes and fades in and out. Two looks ([GlowPrefs.AI_GLOW_STYLE]):
 * - [GlowPrefs.AI_STYLE_RING]: like the Siri light on a HomePod, soft blobs of random pastel colors in a ring round the
 *   keyboard's middle, turning slowly, smooth over the whole keyboard
 * - [GlowPrefs.AI_STYLE_DOTS]: the chip glow's dots and shape, in a round gradient of three random pastel colors
 */
class AiGlow(private val host: View, private val keyboardView: () -> KeyboardView?) {
    private var drawable: Layer? = null
    private var kv: KeyboardView? = null
    private var ticker: ValueAnimator? = null
    private var fader: ValueAnimator? = null
    private var shown = false

    fun show(on: Boolean) {
        val prefs = host.context.prefs()
        if (on && !prefs.getBoolean(GlowPrefs.AI_GLOW, true)) return
        if (on == shown) return
        shown = on
        fader?.cancel()
        if (on && drawable == null) {
            val view = keyboardView() ?: return
            val params = GlowPrefs.aiGlow(prefs)
            val glow = if (prefs.getString(GlowPrefs.AI_GLOW_STYLE, GlowPrefs.AI_STYLE_RING) == GlowPrefs.AI_STYLE_DOTS)
                    PastelDotsDrawable(host.resources.displayMetrics.density, params)
                else PastelRingDrawable(params.maxAlpha, params.minAlpha, params.periodMs,
                    prefs.getBoolean(GlowPrefs.AI_GLOW_RING_DOTS, false),
                    params.spacingDp * host.resources.displayMetrics.density, params.dotDp * host.resources.displayMetrics.density)
            drawable = glow
            kv = view
            view.setForkAiUnderlay(glow)
            val start = SystemClock.uptimeMillis()
            ticker = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    glow.time = SystemClock.uptimeMillis() - start
                    view.invalidateAllKeys()
                }
                start()
            }
        }
        val glow = drawable ?: return
        val inMs = prefs.getFloat(GlowPrefs.AI_GLOW_IN, GlowPrefs.DEFAULT_AI_GLOW_IN).toLong().coerceIn(100, 5000)
        fader = ValueAnimator.ofFloat(glow.fade, if (on) 1f else 0f).apply {
            duration = inMs
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { glow.fade = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!on && !cancelled) remove()
                }
            })
            start()
        }
    }

    private fun remove() {
        ticker?.cancel()
        ticker = null
        kv?.setForkAiUnderlay(null)
        kv = null
        drawable = null
    }

    /** a look of the glow: [time] since it started (ms), [fade] 0..1 */
    private abstract class Layer : Drawable() {
        var time = 0L
        var fade = 0f
        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(colorFilter: ColorFilter?) { }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /**
     * the chip glow's dots and shape in bright candy colors (pastels look white as small dots): four random ones fanned
     * out round the glow's center and shifting a little outwards
     */
    private class PastelDotsDrawable(private val density: Float, private val params: GlowPrefs.Glow) : Layer() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stops = VIVID.toList().shuffled().take(4).toIntArray()
        // the dots for the current size: position, brightness from the shape, color
        private var size = 0L
        private var xs = FloatArray(0)
        private var ys = FloatArray(0)
        private var shape = FloatArray(0)
        private var dotColors = IntArray(0)

        override fun draw(canvas: Canvas) {
            val w = bounds.width()
            val h = bounds.height()
            if (w <= 0 || h <= 0 || fade <= 0f) return
            if (size != (w.toLong() shl 32 or h.toLong())) layout(w, h)
            // breathing like the chip glow
            val breath = 0.5f - 0.5f * cos(2 * PI * time / params.periodMs.coerceAtLeast(1)).toFloat()
            val alpha = fade * (params.minAlpha + (params.maxAlpha - params.minAlpha) * breath)
            val r = params.dotDp * density
            for (i in xs.indices) {
                paint.color = dotColors[i]
                paint.alpha = (255 * alpha * shape[i]).toInt().coerceIn(0, 255)
                if (paint.alpha > 0) canvas.drawCircle(bounds.left + xs[i], bounds.top + ys[i], r, paint)
            }
        }

        /** the same dot grid and falloff as [DotGlowDrawable] */
        private fun layout(width: Int, height: Int) {
            size = width.toLong() shl 32 or height.toLong()
            val w = width.toFloat()
            val h = height.toFloat()
            val spacing = params.spacingDp * density
            val cx = w / 2
            val rx = w * 0.5f * params.width
            val ry = h * params.height
            val lx = ArrayList<Float>(); val ly = ArrayList<Float>(); val ls = ArrayList<Float>(); val lc = ArrayList<Int>()
            var edgeDistance = spacing / 2
            // taller than the keyboard (height over 100 %): the shape goes on beyond the far edge, unseen
            while (edgeDistance < minOf(ry, h)) {
                val y = if (params.fromBottom) h - edgeDistance else edgeDistance
                var x = spacing / 2
                while (x < w) {
                    val dx = (x - cx) / rx
                    val dy = edgeDistance / ry
                    val d = sqrt(dx * dx + dy * dy)
                    if (d < 1f) {
                        lx += x; ly += y
                        // flatter than the chip glow, so the colored outer dots show too
                        ls += (1f - d).pow(0.8f)
                        // around the center (0 left .. 1 right), shifted a little with the distance
                        val around = (kotlin.math.atan2(dy, -dx) / PI).toFloat()
                        lc += gradient((around * 0.75f + d * 0.25f).coerceIn(0f, 1f))
                    }
                    x += spacing
                }
                edgeDistance += spacing
            }
            xs = lx.toFloatArray(); ys = ly.toFloatArray(); shape = ls.toFloatArray(); dotColors = lc.toIntArray()
        }

        /** [d] 0 .. 1 through the colors */
        private fun gradient(d: Float): Int {
            val pos = d.coerceIn(0f, 1f) * (stops.size - 1)
            val i = pos.toInt().coerceIn(0, stops.size - 2)
            return mix(stops[i], stops[i + 1], pos - i)
        }

        private fun mix(c1: Int, c2: Int, f: Float): Int {
            fun ch(shift: Int) = (((c1 shr shift) and 0xFF) * (1 - f) + ((c2 shr shift) and 0xFF) * f).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    /** soft round blobs of pastel light on an ellipse round the middle, turning; a faint light in the middle */
    private class PastelRingDrawable(
        private val maxAlpha: Float, private val minAlpha: Float, private val breathMs: Long,
        /** towards the edges the light turns into dots that shrink and fade out */
        private val edgeDots: Boolean, private val spacing: Float, private val dotRadius: Float,
    ) : Layer() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val random = java.util.Random()
        /** a different color for each blob */
        private val colors = COLORS.toList().shuffled(random).take(BLOBS)
        /** where on the ring each blob sits (turns) and how it wobbles */
        private val offsets = FloatArray(BLOBS) { (it + random.nextFloat() * 0.5f) / BLOBS }
        private val wobble = FloatArray(BLOBS) { random.nextFloat() }
        private var size = 0L
        private var shaders = arrayOfNulls<RadialGradient>(0)
        private var center: RadialGradient? = null
        private var radius = 0f
        // edge dots: blob centers this frame (the last one the middle light), the grid, the mask for the smooth part
        private val bx = FloatArray(BLOBS + 1)
        private val by = FloatArray(BLOBS + 1)
        private var xs = FloatArray(0)
        private var ys = FloatArray(0)
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val maskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f || fade <= 0f) return
            if (size != (w.toLong() shl 32 or h.toLong())) layout(w, h)
            val breath = 0.5f - 0.5f * cos(2 * PI * time / breathMs).toFloat()
            val alpha = fade * (minAlpha + (maxAlpha - minAlpha) * breath)
            val cx = bounds.left + w / 2
            val cy = bounds.top + h / 2
            // the whole ring turns once in TURN_MS, each blob also drifts in and out a little
            val turn = time / TURN_MS.toFloat()
            // edge dots: the smooth light in a layer, faded out towards the edges by a mask
            val saved = if (edgeDots) canvas.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(), null) else -1
            for (i in 0 until BLOBS) {
                val angle = 2 * PI * (offsets[i] + turn)
                val drift = 1f + 0.18f * sin(2 * PI * (time / DRIFT_MS.toFloat() + wobble[i])).toFloat()
                val x = cx + (w * 0.34f * drift * cos(angle)).toFloat()
                val y = cy + (h * 0.30f * drift * sin(angle)).toFloat()
                bx[i] = x
                by[i] = y
                paint.shader = shaders[i]
                paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(x, y)
                canvas.drawCircle(0f, 0f, radius, paint)
                canvas.restore()
            }
            paint.shader = center
            paint.alpha = (255 * alpha * 0.35f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, radius * 0.9f, paint)
            canvas.restore()
            if (!edgeDots) return
            bx[BLOBS] = cx
            by[BLOBS] = cy
            val mask = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, EDGE_SMOOTH_FROM, EDGE_SMOOTH_TO), Shader.TileMode.CLAMP)
            mask.setLocalMatrix(android.graphics.Matrix().apply { setScale(w / 2, h / 2); postTranslate(cx, cy) })
            maskPaint.shader = mask
            canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), maskPaint)
            canvas.restoreToCount(saved)
            drawEdgeDots(canvas, cx, cy, w, h, alpha)
        }

        /** the light's colors on the dot grid where the smooth part fades, the dots shrinking and fading towards the edge */
        private fun drawEdgeDots(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, alpha: Float) {
            val inv = 1f / (radius * radius)
            for (d in xs.indices) {
                val x = bounds.left + xs[d]
                val y = bounds.top + ys[d]
                val nx = (x - cx) / (w / 2)
                val ny = (y - cy) / (h / 2)
                val e = sqrt(nx * nx + ny * ny)
                val show = smooth(EDGE_DOTS_FROM, EDGE_SMOOTH_TO, e) * (1f - smooth(EDGE_SMOOTH_TO, EDGE_DOTS_TO, e))
                if (show <= 0.01f) continue
                var r = 0f; var g = 0f; var b = 0f; var weight = 0f
                for (i in 0..BLOBS) {
                    val dx = x - bx[i]
                    val dy = y - by[i]
                    val q = 1f - (dx * dx + dy * dy) * inv
                    if (q <= 0f) continue
                    val f = q * q * (if (i == BLOBS) 0.35f else 1f)
                    val c = if (i == BLOBS) 0xFFFFFFFF.toInt() else colors[i]
                    r += ((c shr 16) and 0xFF) * f
                    g += ((c shr 8) and 0xFF) * f
                    b += (c and 0xFF) * f
                    weight += f
                }
                if (weight <= 0.01f) continue
                val a = (255 * alpha * weight.coerceAtMost(1f) * show).toInt()
                if (a <= 0) continue
                dotPaint.color = (a shl 24) or ((r / weight).toInt() shl 16) or ((g / weight).toInt() shl 8) or (b / weight).toInt()
                // halftone: smaller dots further out
                canvas.drawCircle(x, y, dotRadius * (1f - 0.5f * smooth(EDGE_SMOOTH_TO, EDGE_DOTS_TO, e)), dotPaint)
            }
        }

        private fun smooth(from: Float, to: Float, v: Float): Float {
            val t = ((v - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }

        private fun layout(w: Float, h: Float) {
            size = w.toLong() shl 32 or h.toLong()
            radius = maxOf(h * 0.75f, w * 0.28f)
            val stops = floatArrayOf(0f, 0.45f, 1f)
            shaders = Array(BLOBS) { i ->
                val c = colors[i]
                RadialGradient(0f, 0f, radius, intArrayOf(c, (c and 0xFFFFFF) or 0x88000000.toInt(), c and 0xFFFFFF),
                    stops, Shader.TileMode.CLAMP)
            }
            center = RadialGradient(0f, 0f, radius * 0.9f, intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            if (edgeDots && spacing > 0f) {
                val cols = (w / spacing).toInt().coerceAtLeast(1)
                val rows = (h / spacing).toInt().coerceAtLeast(1)
                val left = (w - (cols - 1) * spacing) / 2
                val top = (h - (rows - 1) * spacing) / 2
                xs = FloatArray(cols * rows) { left + (it % cols) * spacing }
                ys = FloatArray(cols * rows) { top + (it / cols) * spacing }
            }
        }
    }

    companion object {
        private const val BLOBS = 5
        /** bright, cheerful colors for the dot glow: bubblegum, tangerine, lemon, lime, turquoise, sky, lavender, candy pink */
        private val VIVID = intArrayOf(
            0xFFFF6FB5.toInt(), 0xFFFF9A62.toInt(), 0xFFFFD84D.toInt(), 0xFF8CF06A.toInt(),
            0xFF4FE3D0.toInt(), 0xFF5CC8FF.toInt(), 0xFFB9A2FF.toInt(), 0xFFFF86E8.toInt(),
        )
        private const val TURN_MS = 16_000L
        /** edge dots, as distances from the middle (1 = the edge of the ellipse in the keyboard): the smooth light
         *  fades out from ..FROM to ..TO, the dots come in from DOTS_FROM and are gone at DOTS_TO */
        private const val EDGE_SMOOTH_FROM = 0.5f
        private const val EDGE_SMOOTH_TO = 0.78f
        private const val EDGE_DOTS_FROM = 0.45f
        private const val EDGE_DOTS_TO = 1.1f
        private const val DRIFT_MS = 5_000L
        /** pink, peach, butter, mint, sky, periwinkle, lavender, orchid */
        private val COLORS = intArrayOf(
            0xFFFFB3C7.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(), 0xFFCAFFBF.toInt(),
            0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(), 0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(),
        )
    }
}
