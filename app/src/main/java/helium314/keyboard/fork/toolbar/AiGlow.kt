// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.utils.prefs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * fork: pastel glow while an AI command or translation runs. The same shape as the chip glow (dots behind the keys,
 * brightest at the middle of the top or bottom edge, breathing, seen through the key mask), colored with a round
 * gradient of pastel colors picked at random each time. It fades in and out.
 */
class AiGlow(private val host: View, private val keyboardView: () -> KeyboardView?) {
    private var drawable: PastelGlowDrawable? = null
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
            val glow = PastelGlowDrawable(host.resources.displayMetrics.density, GlowPrefs.aiGlow(prefs))
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

    /** the chip glow's dots, colored by their distance from the glow's center: three random pastels, center to edge */
    private class PastelGlowDrawable(private val density: Float, private val params: GlowPrefs.Glow) : Drawable() {
        var time = 0L
        var fade = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stops = COLORS.toList().shuffled().take(3).toIntArray()
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
            while (edgeDistance < ry) {
                val y = if (params.fromBottom) h - edgeDistance else edgeDistance
                var x = spacing / 2
                while (x < w) {
                    val dx = (x - cx) / rx
                    val dy = edgeDistance / ry
                    val d = sqrt(dx * dx + dy * dy)
                    if (d < 1f) {
                        lx += x; ly += y
                        ls += (1f - d).pow(1.2f)
                        lc += gradient(d)
                    }
                    x += spacing
                }
                edgeDistance += spacing
            }
            xs = lx.toFloatArray(); ys = ly.toFloatArray(); shape = ls.toFloatArray(); dotColors = lc.toIntArray()
        }

        /** [d] 0 (center) .. 1 (edge) through the three colors */
        private fun gradient(d: Float): Int {
            val pos = d.coerceIn(0f, 1f) * (stops.size - 1)
            val i = pos.toInt().coerceIn(0, stops.size - 2)
            return mix(stops[i], stops[i + 1], pos - i)
        }

        private fun mix(c1: Int, c2: Int, f: Float): Int {
            fun ch(shift: Int) = (((c1 shr shift) and 0xFF) * (1 - f) + ((c2 shr shift) and 0xFF) * f).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(colorFilter: ColorFilter?) { }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT

        companion object {
            /** pink, peach, butter, mint, sky, periwinkle, lavender, orchid */
            private val COLORS = intArrayOf(
                0xFFFFB3C7.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(), 0xFFCAFFBF.toInt(),
                0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(), 0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(),
            )
        }
    }
}
