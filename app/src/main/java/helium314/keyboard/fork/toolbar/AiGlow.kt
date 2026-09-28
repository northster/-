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
import kotlin.math.sin

/**
 * fork: pastel light behind the keys while an AI command or translation runs, like the Siri light on a HomePod: soft
 * blobs of random pastel colors in a ring around the keyboard's middle, turning slowly, over the whole keyboard
 * background, shown on the chip glow's dot grid (dot matrix). Seen through the key mask like the chip glow; it breathes
 * and fades in and out.
 */
class AiGlow(private val host: View, private val keyboardView: () -> KeyboardView?) {
    private var drawable: PastelRingDrawable? = null
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
            val max = prefs.getFloat(GlowPrefs.AI_GLOW_MAX, GlowPrefs.DEFAULT_AI_GLOW_MAX).coerceIn(0f, 1f)
            val grid = GlowPrefs.glow(prefs)
            val glow = PastelRingDrawable(host.resources.displayMetrics.density * grid.spacingDp,
                host.resources.displayMetrics.density * grid.dotDp, max,
                prefs.getFloat(GlowPrefs.AI_GLOW_MIN, GlowPrefs.DEFAULT_AI_GLOW_MIN).coerceIn(0f, max),
                prefs.getFloat(GlowPrefs.AI_GLOW_BREATH, GlowPrefs.DEFAULT_AI_GLOW_BREATH).toLong().coerceIn(200, 20_000))
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

    /**
     * soft round blobs of pastel light on an ellipse round the middle, turning, and a faint light in the middle; each
     * dot of the grid takes the mix of the blobs' colors where it is
     */
    private class PastelRingDrawable(
        private val spacing: Float, private val dotRadius: Float,
        private val maxAlpha: Float, private val minAlpha: Float, private val breathMs: Long,
    ) : Drawable() {
        var time = 0L
        var fade = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val random = java.util.Random()
        /** a different color for each blob */
        private val colors = COLORS.toList().shuffled(random).take(BLOBS)
        /** where on the ring each blob sits (turns) and how it wobbles */
        private val offsets = FloatArray(BLOBS) { (it + random.nextFloat() * 0.5f) / BLOBS }
        private val wobble = FloatArray(BLOBS) { random.nextFloat() }
        private var size = 0L
        private var xs = FloatArray(0)
        private var ys = FloatArray(0)
        private val bx = FloatArray(BLOBS + 1)
        private val by = FloatArray(BLOBS + 1)

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f || fade <= 0f) return
            if (size != (w.toLong() shl 32 or h.toLong())) layout(w, h)
            val breath = 0.5f - 0.5f * cos(2 * PI * time / breathMs).toFloat()
            val alpha = fade * (minAlpha + (maxAlpha - minAlpha) * breath)
            val cx = w / 2
            val cy = h / 2
            // the whole ring turns once in TURN_MS, each blob also drifts in and out a little
            val turn = time / TURN_MS.toFloat()
            for (i in 0 until BLOBS) {
                val angle = 2 * PI * (offsets[i] + turn)
                val drift = 1f + 0.18f * sin(2 * PI * (time / DRIFT_MS.toFloat() + wobble[i])).toFloat()
                bx[i] = cx + (w * 0.34f * drift * cos(angle)).toFloat()
                by[i] = cy + (h * 0.30f * drift * sin(angle)).toFloat()
            }
            bx[BLOBS] = cx
            by[BLOBS] = cy
            val radius = maxOf(h * 0.75f, w * 0.28f)
            val inv = 1f / (radius * radius)
            for (d in xs.indices) {
                var r = 0f; var g = 0f; var b = 0f; var weight = 0f
                for (i in 0..BLOBS) {
                    val dx = xs[d] - bx[i]
                    val dy = ys[d] - by[i]
                    val q = 1f - (dx * dx + dy * dy) * inv
                    if (q <= 0f) continue
                    // soft falloff; the white light in the middle is fainter
                    val f = q * q * (if (i == BLOBS) 0.35f else 1f)
                    val c = if (i == BLOBS) 0xFFFFFFFF.toInt() else colors[i]
                    r += ((c shr 16) and 0xFF) * f
                    g += ((c shr 8) and 0xFF) * f
                    b += (c and 0xFF) * f
                    weight += f
                }
                if (weight <= 0.01f) continue
                val a = (255 * alpha * weight.coerceAtMost(1f)).toInt()
                if (a <= 0) continue
                paint.color = (a shl 24) or ((r / weight).toInt() shl 16) or ((g / weight).toInt() shl 8) or (b / weight).toInt()
                canvas.drawCircle(bounds.left + xs[d], bounds.top + ys[d], dotRadius, paint)
            }
        }

        /** the dot grid over the whole keyboard, centered */
        private fun layout(w: Float, h: Float) {
            size = w.toLong() shl 32 or h.toLong()
            val cols = (w / spacing).toInt().coerceAtLeast(1)
            val rows = (h / spacing).toInt().coerceAtLeast(1)
            val left = (w - (cols - 1) * spacing) / 2
            val top = (h - (rows - 1) * spacing) / 2
            xs = FloatArray(cols * rows) { left + (it % cols) * spacing }
            ys = FloatArray(cols * rows) { top + (it / cols) * spacing }
        }

        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(colorFilter: ColorFilter?) { }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT

        companion object {
            private const val BLOBS = 5
            private const val TURN_MS = 16_000L
            private const val DRIFT_MS = 5_000L
            /** pink, peach, butter, mint, sky, periwinkle, lavender, orchid */
            private val COLORS = intArrayOf(
                0xFFFFB3C7.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(), 0xFFCAFFBF.toInt(),
                0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(), 0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(),
            )
        }
    }
}
