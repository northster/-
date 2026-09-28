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
import kotlin.math.sin

/**
 * fork: pastel light behind the keys while an AI command or translation runs, like the Siri light on a HomePod: soft
 * blobs of random pastel colors in a ring around the keyboard's middle, turning slowly, over the whole keyboard
 * background. Seen through the key mask like the chip glow; it breathes and fades in and out.
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
            val glow = PastelRingDrawable(max,
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

    /** soft round blobs of pastel light on an ellipse round the middle, turning; a faint light in the middle */
    private class PastelRingDrawable(private val maxAlpha: Float, private val minAlpha: Float, private val breathMs: Long) : Drawable() {
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
        private var shaders = arrayOfNulls<RadialGradient>(0)
        private var center: RadialGradient? = null
        private var radius = 0f

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
            for (i in 0 until BLOBS) {
                val angle = 2 * PI * (offsets[i] + turn)
                val drift = 1f + 0.18f * sin(2 * PI * (time / DRIFT_MS.toFloat() + wobble[i])).toFloat()
                val x = cx + (w * 0.34f * drift * cos(angle)).toFloat()
                val y = cy + (h * 0.30f * drift * sin(angle)).toFloat()
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
