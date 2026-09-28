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
 * - [GlowPrefs.AI_STYLE_DOTS]: the chip glow's dots and shape, the HomePod colors blending softly across it
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
                    PastelDotsDrawable(host.resources.displayMetrics.density, params,
                        prefs.getFloat(GlowPrefs.AI_GLOW_BLOOM, GlowPrefs.DEFAULT_AI_GLOW_BLOOM).coerceIn(0f, 1f))
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
     * the chip glow's dots and shape, colored like the HomePod light: soft blobs of HomePod colors (random each time)
     * drift slowly round inside the glow, with a pale light at its center; every dot takes the blend of the colors where
     * it is, so the colors flow into each other across the glow instead of bunching up. Under the dots the same light
     * shines smooth and faint ([bloom]), like LEDs lighting up what is around them.
     */
    private class PastelDotsDrawable(private val density: Float, private val params: GlowPrefs.Glow, private val bloom: Float) : Layer() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val random = java.util.Random()
        private val colors = HOMEPOD.toList().shuffled(random).take(BLOBS)
        private val offsets = FloatArray(BLOBS) { (it + random.nextFloat() * 0.5f) / BLOBS }
        private val wobble = FloatArray(BLOBS) { random.nextFloat() }
        // the dots for the current size: position, brightness from the shape
        private var size = 0L
        private var xs = FloatArray(0)
        private var ys = FloatArray(0)
        private var shape = FloatArray(0)
        private val bx = FloatArray(BLOBS + 1)
        private val by = FloatArray(BLOBS + 1)
        // the smooth light: a soft round gradient per blob, kept to the glow's shape by a mask
        private var blobShaders = arrayOfNulls<RadialGradient>(0)
        private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val maskPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
        private var mask: RadialGradient? = null
        private var layerPaint = Paint()

        override fun draw(canvas: Canvas) {
            val w = bounds.width()
            val h = bounds.height()
            if (w <= 0 || h <= 0 || fade <= 0f) return
            if (size != (w.toLong() shl 32 or h.toLong())) layout(w, h)
            // breathing like the chip glow
            val breath = 0.5f - 0.5f * cos(2 * PI * time / params.periodMs.coerceAtLeast(1)).toFloat()
            val alpha = fade * (params.minAlpha + (params.maxAlpha - params.minAlpha) * breath)
            val r = params.dotDp * density
            // the glow's center on its edge and its size, the blobs on a half ellipse inside it, turning slowly
            val cx = w / 2f
            val cy = if (params.fromBottom) h.toFloat() else 0f
            val rx = w * 0.5f * params.width
            val ry = h * params.height
            val inward = if (params.fromBottom) -1f else 1f
            val turn = time / TURN_MS.toFloat()
            for (i in 0 until BLOBS) {
                // back and forth along the half ellipse (a full turn would take them outside)
                val t = 0.5f + 0.5f * sin(2 * PI * (offsets[i] + turn)).toFloat()
                val angle = PI * (0.1f + 0.8f * t)
                val drift = 0.45f + 0.15f * sin(2 * PI * (time / DRIFT_MS.toFloat() + wobble[i])).toFloat()
                bx[i] = cx + (rx * drift * cos(angle)).toFloat()
                by[i] = cy + inward * (ry * drift * sin(angle)).toFloat()
            }
            bx[BLOBS] = cx
            by[BLOBS] = cy
            val blob = maxOf(rx, ry) * 0.55f
            val inv = 1f / (blob * blob)
            if (bloom > 0f) drawBloom(canvas, alpha * bloom, blob)
            for (d in xs.indices) {
                var red = 0f; var green = 0f; var blue = 0f; var weight = 0f
                for (i in 0..BLOBS) {
                    val dx = xs[d] - bx[i]
                    val dy = ys[d] - by[i]
                    // never zero: every dot gets a clear blend, no dark gaps between the blobs
                    val q = 1f / (1f + (dx * dx + dy * dy) * inv)
                    val f = q * q * (if (i == BLOBS) 0.5f else 1f)
                    val c = if (i == BLOBS) CENTER_LIGHT else colors[i]
                    red += ((c shr 16) and 0xFF) * f
                    green += ((c shr 8) and 0xFF) * f
                    blue += (c and 0xFF) * f
                    weight += f
                }
                val a = (255 * alpha * shape[d]).toInt().coerceIn(0, 255)
                if (a <= 0) continue
                paint.color = (a shl 24) or ((red / weight).toInt() shl 16) or ((green / weight).toInt() shl 8) or (blue / weight).toInt()
                canvas.drawCircle(bounds.left + xs[d], bounds.top + ys[d], r, paint)
            }
        }

        /** the blobs smooth, in a layer that the mask keeps to the glow's shape, faint */
        private fun drawBloom(canvas: Canvas, alpha: Float, blob: Float) {
            layerPaint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
            if (layerPaint.alpha <= 0) return
            val l = bounds.left.toFloat()
            val t = bounds.top.toFloat()
            val saved = canvas.saveLayer(l, t, bounds.right.toFloat(), bounds.bottom.toFloat(), layerPaint)
            for (i in 0..BLOBS) {
                bloomPaint.shader = blobShaders.getOrNull(i) ?: continue
                canvas.save()
                canvas.translate(l + bx[i], t + by[i])
                canvas.drawCircle(0f, 0f, blob * 1.8f, bloomPaint)
                canvas.restore()
            }
            maskPaint.shader = mask
            canvas.drawRect(l, t, bounds.right.toFloat(), bounds.bottom.toFloat(), maskPaint)
            canvas.restoreToCount(saved)
        }

        /** the same dot grid and shape as [DotGlowDrawable], a little flatter so the colored outer dots show */
        private fun layout(width: Int, height: Int) {
            size = width.toLong() shl 32 or height.toLong()
            val w = width.toFloat()
            val h = height.toFloat()
            val spacing = params.spacingDp * density
            val cx = w / 2
            val rx = w * 0.5f * params.width
            val ry = h * params.height
            val lx = ArrayList<Float>(); val ly = ArrayList<Float>(); val ls = ArrayList<Float>()
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
                        ls += (1f - d).pow(0.7f)
                    }
                    x += spacing
                }
                edgeDistance += spacing
            }
            xs = lx.toFloatArray(); ys = ly.toFloatArray(); shape = ls.toFloatArray()
            // the smooth light's gradients and the glow's shape as a mask (as bright as the dots' falloff)
            val blob = maxOf(rx, ry) * 0.55f
            blobShaders = Array(BLOBS + 1) { i ->
                val c = if (i == BLOBS) CENTER_LIGHT else colors[i]
                RadialGradient(0f, 0f, blob * 1.8f, intArrayOf(c, (c and 0xFFFFFF) or 0x80000000.toInt(), c and 0xFFFFFF),
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            }
            mask = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFFFFF.toInt(), 0x9EFFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP).apply {
                setLocalMatrix(android.graphics.Matrix().apply {
                    setScale(rx, ry)
                    postTranslate(bounds.left + cx, bounds.top + if (params.fromBottom) h else 0f)
                })
            }
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
        /** HomePod light colors, bright: pink, magenta, violet, periwinkle, cyan, aqua, peach, coral */
        private val HOMEPOD = intArrayOf(
            0xFFFF8FD8.toInt(), 0xFFF26BFF.toInt(), 0xFFB98CFF.toInt(), 0xFF8FA8FF.toInt(),
            0xFF62E6FF.toInt(), 0xFF7FF5E4.toInt(), 0xFFFFB38A.toInt(), 0xFFFF9AA8.toInt(),
        )
        /** the pale light in the middle of the dot glow */
        private const val CENTER_LIGHT = 0xFFFFF2FB.toInt()
        private const val TURN_MS = 16_000L
        private const val DRIFT_MS = 5_000L
        /** pink, peach, butter, mint, sky, periwinkle, lavender, orchid */
        private val COLORS = intArrayOf(
            0xFFFFB3C7.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(), 0xFFCAFFBF.toInt(),
            0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(), 0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(),
        )
    }
}
