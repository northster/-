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
                else PastelRingDrawable(params.maxAlpha, params.minAlpha, params.periodMs)
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
     * the chip glow's dots and shape in vivid colors (pastels look white as small dots): four random ones fanned out
     * round the glow's center and shifting a little outwards, each dot twinkling softly on its own
     */
    private class PastelDotsDrawable(private val density: Float, private val params: GlowPrefs.Glow) : Layer() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stops = VIVID.toList().shuffled().take(4).toIntArray()
        private val random = java.util.Random()
        private var phases = FloatArray(0)
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
                val twinkle = 0.7f + 0.3f * sin(time / TWINKLE_MS.toFloat() + phases[i])
                paint.alpha = (255 * alpha * shape[i] * twinkle).toInt().coerceIn(0, 255)
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
            phases = FloatArray(xs.size) { random.nextFloat() * 2 * PI.toFloat() }
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
    private class PastelRingDrawable(private val maxAlpha: Float, private val minAlpha: Float, private val breathMs: Long) : Layer() {
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
    }

    companion object {
        private const val BLOBS = 5
        /** twinkling of the dot glow's dots (radians per ms: about 4 s a cycle) */
        private const val TWINKLE_MS = 650L
        /** strong colors for the dot glow: pink, coral, amber, lime, aqua, azure, violet, magenta */
        private val VIVID = intArrayOf(
            0xFFFF4FA3.toInt(), 0xFFFF7A59.toInt(), 0xFFFFC23D.toInt(), 0xFF7CE35A.toInt(),
            0xFF2FD8E8.toInt(), 0xFF4F8BFF.toInt(), 0xFF9B6BFF.toInt(), 0xFFE15CFF.toInt(),
        )
        private const val TURN_MS = 16_000L
        private const val DRIFT_MS = 5_000L
        /** pink, peach, butter, mint, sky, periwinkle, lavender, orchid */
        private val COLORS = intArrayOf(
            0xFFFFB3C7.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(), 0xFFCAFFBF.toInt(),
            0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(), 0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(),
        )
    }
}
