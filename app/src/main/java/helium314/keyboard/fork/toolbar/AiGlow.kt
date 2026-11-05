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
 * fork: pastel glow behind the keys and the toolbar while an AI command or translation runs, seen through the key mask
 * like the chip glow; it breathes and fades in and out. It is one glow over both: the keyboard view and the toolbar each
 * draw their part of it. The looks ([GlowPrefs.AI_GLOW_STYLE]):
 * - [GlowPrefs.AI_STYLE_RING]: like the Siri light on a HomePod, soft blobs of random pastel colors in a ring round the
 *   keyboard's middle, turning slowly, smooth over the whole keyboard
 * - [GlowPrefs.AI_STYLE_DOTS]: the chip glow's dots and shape, the HomePod colors blending softly across it
 * - [GlowPrefs.AI_STYLE_EDGE] / [GlowPrefs.AI_STYLE_EDGE_GOOGLE]: like Gemini on Android, a gradient turning round the
 *   edges and fading inwards, in Gemini's or Google's colors
 * - the "light" looks ([LightsDrawable]): like Gemini's pictures, big soft lights at an edge or corner fading into the
 *   dark, from above (the Gemini app), a horizon from below, both ends, or drifting about
 */
class AiGlow(
    private val host: View,
    private val keyboardView: () -> KeyboardView?,
    /** the toolbar when it is (at least partly) shown */
    private val toolbar: () -> DynamicToolbarView?,
) {
    private var drawable: Layer? = null
    private var kv: KeyboardView? = null
    private var tb: DynamicToolbarView? = null
    /** the parts: the glow moved up by where the view is in the whole (toolbar on top, keyboard below) */
    private val kvPart = Part()
    private val tbPart = Part()
    private val loc = IntArray(2)

    private inner class Part : Drawable() {
        var offsetY = 0
        override fun draw(canvas: Canvas) {
            val glow = drawable ?: return
            canvas.save()
            canvas.translate(0f, -offsetY.toFloat())
            glow.draw(canvas)
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(colorFilter: ColorFilter?) { }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** the glow over the keyboard and, when shown, the toolbar above it */
    private fun place(view: KeyboardView, glow: Layer) {
        view.getLocationInWindow(loc)
        val kvTop = loc[1]
        val bar = toolbar()
        var top = kvTop
        if (bar != null) {
            bar.getLocationInWindow(loc)
            if (loc[1] < kvTop) top = loc[1]
            tbPart.offsetY = loc[1] - top
        }
        if (bar !== tb) {
            tb?.setUnderGlow(null)
            tb?.setOverGlow(null)
            tb = bar
            if (over) bar?.setOverGlow(tbPart) else bar?.setUnderGlow(tbPart)
        }
        kvPart.offsetY = kvTop - top
        glow.setBounds(0, 0, view.width, kvTop - top + view.height)
        bar?.invalidate()
    }
    private var ticker: ValueAnimator? = null
    private var fader: ValueAnimator? = null
    private var shown = false
    /** drawn over the keys instead of behind them ([GlowPrefs.AI_GLOW_OVER]) */
    private var over = false

    fun show(on: Boolean) {
        val prefs = host.context.prefs()
        if (on && !prefs.getBoolean(GlowPrefs.AI_GLOW, true)) return
        if (on == shown) return
        shown = on
        fader?.cancel()
        if (on && drawable == null) {
            val view = keyboardView() ?: return
            val params = GlowPrefs.aiGlow(prefs)
            val glow = when (prefs.getString(GlowPrefs.AI_GLOW_STYLE, GlowPrefs.AI_STYLE_RING)) {
                GlowPrefs.AI_STYLE_DOTS -> PastelDotsDrawable(host.resources.displayMetrics.density, params,
                    prefs.getFloat(GlowPrefs.AI_GLOW_BLOOM, GlowPrefs.DEFAULT_AI_GLOW_BLOOM).coerceIn(0f, 1f))
                GlowPrefs.AI_STYLE_EDGE -> EdgeDrawable(GEMINI, 0.11f, params)
                GlowPrefs.AI_STYLE_EDGE_GOOGLE -> EdgeDrawable(GOOGLE, 0.09f, params)
                GlowPrefs.AI_STYLE_LIGHT_TOP -> LightsDrawable(LIGHTS_TOP, params)
                GlowPrefs.AI_STYLE_LIGHT_HORIZON -> LightsDrawable(LIGHTS_HORIZON, params)
                GlowPrefs.AI_STYLE_LIGHT_SIDES -> LightsDrawable(LIGHTS_SIDES, params)
                GlowPrefs.AI_STYLE_LIGHT_FLOW -> LightsDrawable(LIGHTS_FLOW, params)
                else -> PastelRingDrawable(params.maxAlpha, params.minAlpha, params.periodMs)
            }
            drawable = glow
            kv = view
            // the edge looks can be drawn over the keys (and the toolbar's buttons) instead of behind them
            over = glow is EdgeDrawable && prefs.getBoolean(GlowPrefs.AI_GLOW_OVER, GlowPrefs.DEFAULT_AI_GLOW_OVER)
            place(view, glow)
            if (over) view.setForkAiOverlay(kvPart) else view.setForkAiUnderlay(kvPart)
            val start = SystemClock.uptimeMillis()
            ticker = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    glow.time = SystemClock.uptimeMillis() - start
                    place(view, glow)
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
        kv?.setForkAiOverlay(null)
        kv = null
        tb?.setUnderGlow(null)
        tb?.setOverGlow(null)
        tb = null
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
     * shines smooth and faint ([bloom]), added onto the dots and around them like the light of LEDs.
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
        private val layerPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }

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
            if (bloom > 0f) drawBloom(canvas, alpha * bloom, blob)
        }

        /** the blobs smooth, in a layer that the mask keeps to the glow's shape, added onto the dots (light adds up) */
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

    /**
     * a sweep gradient of [colors] turning round the middle, kept to a soft band along the edges by a mask: the light
     * fades inwards with the distance to a rounded rectangle (so it curves round the corners instead of creasing along
     * their diagonals). Made to look smooth to the eye: the colors blend in OKLab (even steps in how they look, not in
     * RGB numbers, which makes muddy or banded middles), the fade is a gaussian (no visible edge where it starts or
     * ends) and both are drawn dithered. [depth]: share of the shorter side the band reaches. Breathes like the others.
     */
    private class EdgeDrawable(colors: IntArray, private val depth: Float, private val params: GlowPrefs.Glow) : Layer() {
        private val sweepColors = oklabRamp(colors + colors[0], 72)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val matrix = android.graphics.Matrix()
        private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        private val layerPaint = Paint()
        private val rect = android.graphics.RectF()
        private var size = 0L
        private var mask: android.graphics.Bitmap? = null
        private var sweep: android.graphics.SweepGradient? = null
        private var sweepCx = Float.NaN
        private var sweepCy = Float.NaN

        override fun draw(canvas: Canvas) {
            val w = bounds.width()
            val h = bounds.height()
            if (w <= 0 || h <= 0 || fade <= 0f) return
            if (size != (w.toLong() shl 32 or h.toLong())) layout(w, h)
            val breath = 0.5f - 0.5f * cos(2 * PI * time / params.periodMs.coerceAtLeast(1)).toFloat()
            layerPaint.alpha = (255 * fade * (params.minAlpha + (params.maxAlpha - params.minAlpha) * breath)).toInt().coerceIn(0, 255)
            if (layerPaint.alpha <= 0) return
            rect.set(bounds)
            val saved = canvas.saveLayer(rect, layerPaint)
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            if (sweep == null || cx != sweepCx || cy != sweepCy) {
                sweep = android.graphics.SweepGradient(cx, cy, sweepColors, null)
                sweepCx = cx
                sweepCy = cy
            }
            matrix.setRotate((time / TURN_EDGE_MS.toFloat() * 360f) % 360f, cx, cy)
            sweep!!.setLocalMatrix(matrix)
            paint.shader = sweep
            canvas.drawRect(rect, paint)
            mask?.let { canvas.drawBitmap(it, null, rect, maskPaint) }
            canvas.restoreToCount(saved)
        }

        /** the fade, at half the size (it is smooth, drawn scaled up with filtering) */
        private fun layout(width: Int, height: Int) {
            size = width.toLong() shl 32 or height.toLong()
            val scale = 2
            val mw = (width / scale).coerceAtLeast(1)
            val mh = (height / scale).coerceAtLeast(1)
            val tau = minOf(mw, mh) * depth
            val sigma = tau * 1.4f
            val r = tau * 3
            val hx = mw / 2f - r
            val hy = mh / 2f - r
            val pixels = IntArray(mw * mh)
            for (y in 0 until mh) for (x in 0 until mw) {
                val qx = kotlin.math.abs(x + 0.5f - mw / 2f) - hx
                val qy = kotlin.math.abs(y + 0.5f - mh / 2f) - hy
                val ox = maxOf(qx, 0f)
                val oy = maxOf(qy, 0f)
                val sdf = sqrt(ox * ox + oy * oy) + minOf(maxOf(qx, qy), 0f) - r
                val d = maxOf(0f, -sdf) / sigma
                val a = (255 * kotlin.math.exp(-0.5f * d * d)).toInt().coerceIn(0, 255)
                pixels[y * mw + x] = a shl 24
            }
            mask?.recycle()
            mask = android.graphics.Bitmap.createBitmap(pixels, mw, mh, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }

    /** a soft light: where (share of the glow's width / height, may be outside), how big, its color, how strong, drift */
    private class Light(val x: Float, val y: Float, val rx: Float, val ry: Float, color: Int, val strength: Float,
                        val dx: Float = 0f, val dy: Float = 0f, val periodS: Float = 20f) {
        val r = toLinear((color shr 16) and 0xFF)
        val g = toLinear((color shr 8) and 0xFF)
        val b = toLinear(color and 0xFF)
    }

    /**
     * big soft lights added up the way light adds up (in linear light), then softly compressed so where they meet
     * stays a deep color instead of going flat white; each light fades as a gaussian, so there is no edge anywhere.
     * Computed at a low resolution and drawn scaled up with filtering and dithering, with a fine film grain over it
     * (like the pictures it is made after, and no banding in the dark). Breathes a little and drifts slowly.
     */
    private class LightsDrawable(private val lights: Array<Light>, private val params: GlowPrefs.Glow) : Layer() {
        private var bitmap: android.graphics.Bitmap? = null
        private var pixels = IntArray(0)
        private var gw = 0
        private var gh = 0
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        private val grainPaint = Paint().apply {
            shader = android.graphics.BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
            alpha = 16
        }
        private val rect = android.graphics.RectF()
        private val lx = FloatArray(lights.size)
        private val ly = FloatArray(lights.size)
        private val lk = FloatArray(lights.size)

        override fun draw(canvas: Canvas) {
            val w = bounds.width()
            val h = bounds.height()
            if (w <= 0 || h <= 0 || fade <= 0f) return
            if (bitmap == null || gw != GLOW_W || gh != (GLOW_W * h / w).coerceIn(16, 200)) {
                gw = GLOW_W
                gh = (GLOW_W * h / w).coerceIn(16, 200)
                bitmap?.recycle()
                bitmap = android.graphics.Bitmap.createBitmap(gw, gh, android.graphics.Bitmap.Config.ARGB_8888)
                pixels = IntArray(gw * gh)
            }
            val t = time / 1000f
            val breath = 0.88f + 0.12f * sin(2 * PI * time / params.periodMs.coerceAtLeast(1)).toFloat()
            // the brightness settings scale it (their defaults give the look as designed)
            val bright = (params.maxAlpha / GlowPrefs.DEFAULT_AI_GLOW_MAX).coerceIn(0f, 1.5f)
            for (i in lights.indices) {
                val l = lights[i]
                val ph = 2 * PI * t / l.periodS
                lx[i] = l.x + l.dx * sin(ph).toFloat()
                ly[i] = l.y + l.dy * cos(ph * 0.8).toFloat()
                lk[i] = l.strength * breath * bright * EXPOSURE
            }
            for (j in 0 until gh) {
                val v = (j + 0.5f) / gh
                for (i in 0 until gw) {
                    val u = (i + 0.5f) / gw
                    var r = 0f; var g = 0f; var b = 0f
                    for (n in lights.indices) {
                        val l = lights[n]
                        val a = (u - lx[n]) / l.rx
                        val e = (v - ly[n]) / l.ry
                        val q = a * a + e * e
                        if (q > 12f) continue // too far to matter
                        val wgt = lk[n] * kotlin.math.exp(-0.5f * q * FALLOFF)
                        r += l.r * wgt; g += l.g * wgt; b += l.b * wgt
                    }
                    // as light over the keyboard: the brightest channel is the alpha (dark = see-through, so it also
                    //  works on light themes), the color is the light at full strength
                    val cr = fromLinear(1f - kotlin.math.exp(-r))
                    val cg = fromLinear(1f - kotlin.math.exp(-g))
                    val cb = fromLinear(1f - kotlin.math.exp(-b))
                    val a = maxOf(cr, cg, cb)
                    pixels[j * gw + i] = if (a == 0) 0
                        else (a shl 24) or ((cr * 255 / a) shl 16) or ((cg * 255 / a) shl 8) or (cb * 255 / a)
                }
            }
            val bmp = bitmap ?: return
            bmp.setPixels(pixels, 0, gw, 0, 0, gw, gh)
            rect.set(bounds)
            // fading in and out with the glow; the grain only where there is light (SRC_ATOP)
            paint.alpha = (255 * fade).toInt().coerceIn(0, 255)
            val saved = canvas.saveLayer(rect, null)
            canvas.drawBitmap(bmp, null, rect, paint)
            canvas.drawRect(rect, grainPaint)
            canvas.restoreToCount(saved)
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
        /** one turn of the edge glow's gradient */
        private const val TURN_EDGE_MS = 16_000L
        /** Gemini's colors: blue, light blue, violet, rose, amber */
        private val GEMINI = intArrayOf(
            0xFF4796E3.toInt(), 0xFF6EC6FF.toInt(), 0xFF9177C7.toInt(), 0xFFCA6673.toInt(), 0xFFF2A45C.toInt(),
        )
        /** [stops] colors going through [colors] in even steps of OKLab (how different colors look), as sRGB */
        private fun oklabRamp(colors: IntArray, stops: Int): IntArray {
            val labs = colors.map { toOklab(it) }
            return IntArray(stops) { k ->
                val t = k / (stops - 1f) * (labs.size - 1)
                val i = t.toInt().coerceAtMost(labs.size - 2)
                val f = t - i
                val a = labs[i]
                val b = labs[i + 1]
                fromOklab(FloatArray(3) { j -> a[j] + (b[j] - a[j]) * f })
            }
        }

        private fun toLinear(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }

        private fun fromLinear(v: Float): Int {
            val c = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.coerceAtLeast(0f).pow(1f / 2.4f) - 0.055f
            return (c * 255f + 0.5f).toInt().coerceIn(0, 255)
        }

        private fun toOklab(color: Int): FloatArray {
            val r = toLinear((color shr 16) and 0xFF)
            val g = toLinear((color shr 8) and 0xFF)
            val b = toLinear(color and 0xFF)
            val l = Math.cbrt((0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b).toDouble()).toFloat()
            val m = Math.cbrt((0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b).toDouble()).toFloat()
            val s = Math.cbrt((0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b).toDouble()).toFloat()
            return floatArrayOf(
                0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
                1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
                0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
            )
        }

        private fun fromOklab(lab: FloatArray): Int {
            val l = lab[0] + 0.3963377774f * lab[1] + 0.2158037573f * lab[2]
            val m = lab[0] - 0.1055613458f * lab[1] - 0.0638541728f * lab[2]
            val s = lab[0] - 0.0894841775f * lab[1] - 1.2914855480f * lab[2]
            val l3 = l * l * l
            val m3 = m * m * m
            val s3 = s * s * s
            val r = 4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
            val g = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
            val b = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
            return (0xFF shl 24) or (fromLinear(r) shl 16) or (fromLinear(g) shl 8) or fromLinear(b)
        }

        /** the light looks: low resolution width, how strong and how wide they shine (as in the preview page) */
        private const val GLOW_W = 160
        private const val EXPOSURE = 0.62f
        private const val FALLOFF = 3.6f

        /** fine film grain: random grays, drawn faintly over the light (lighter and darker specks) */
        private fun grain(): android.graphics.Bitmap {
            val random = java.util.Random(7)
            val px = IntArray(128 * 128) { val v = random.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
            return android.graphics.Bitmap.createBitmap(px, 128, 128, android.graphics.Bitmap.Config.ARGB_8888)
        }

        /** H1, like the Gemini app: teal on the left, blue on the right, from above */
        private val LIGHTS_TOP = arrayOf(
            Light(0.02f, -0.18f, 0.55f, 0.75f, 0xFF0F8A86.toInt(), 1.25f, dx = 0.05f, periodS = 19f),
            Light(0.88f, -0.22f, 0.7f, 0.8f, 0xFF1F66E0.toInt(), 1.5f, dx = -0.05f, periodS = 23f),
            Light(0.5f, -0.35f, 0.9f, 0.6f, 0xFF163F9E.toInt(), 0.9f, dx = 0.04f, periodS = 29f),
            Light(-0.08f, 0.45f, 0.25f, 0.5f, 0xFF0B6B3C.toInt(), 0.55f, dy = 0.06f, periodS = 17f),
        )
        /** H2, a horizon from below: red, orange, warm white, sky, blue */
        private val LIGHTS_HORIZON = arrayOf(
            Light(0.04f, 1.18f, 0.3f, 0.5f, 0xFFE2321E.toInt(), 1.4f, dx = 0.03f, periodS = 21f),
            Light(0.2f, 1.14f, 0.28f, 0.45f, 0xFFF2873A.toInt(), 1.3f, dx = 0.03f, periodS = 25f),
            Light(0.38f, 1.12f, 0.26f, 0.42f, 0xFFFFE6C2.toInt(), 1.6f, dx = 0.04f, periodS = 18f),
            Light(0.57f, 1.06f, 0.3f, 0.5f, 0xFF86D9F7.toInt(), 1.4f, dx = -0.03f, periodS = 27f),
            Light(0.8f, 0.98f, 0.42f, 0.72f, 0xFF1E6FE6.toInt(), 1.5f, dx = -0.03f, periodS = 23f),
            Light(0.98f, 0.6f, 0.36f, 0.75f, 0xFF1A3FAE.toInt(), 1.0f, dy = 0.05f, periodS = 31f),
        )
        /** H3, both ends: orange, violet and indigo from the left, blue in the bottom right corner */
        private val LIGHTS_SIDES = arrayOf(
            Light(-0.1f, -0.05f, 0.3f, 0.7f, 0xFFF6A623.toInt(), 1.6f, dy = 0.05f, periodS = 22f),
            Light(0.08f, 0.65f, 0.36f, 0.8f, 0xFF8B3FD6.toInt(), 1.4f, dx = 0.03f, periodS = 19f),
            Light(0.34f, 0.7f, 0.42f, 0.85f, 0xFF2B1A96.toInt(), 1.1f, dx = 0.04f, periodS = 27f),
            Light(1.06f, 1.08f, 0.34f, 0.55f, 0xFF2A73DF.toInt(), 1.5f, dx = -0.03f, periodS = 24f),
        )
        /** H4, drifting: blue, indigo violet, teal, orange floating about */
        private val LIGHTS_FLOW = arrayOf(
            Light(0.2f, 0.15f, 0.42f, 0.7f, 0xFF1F66E0.toInt(), 1.3f, dx = 0.22f, dy = 0.18f, periodS = 14f),
            Light(0.75f, 0.3f, 0.4f, 0.65f, 0xFF5A2FD0.toInt(), 1.2f, dx = -0.2f, dy = 0.2f, periodS = 17f),
            Light(0.5f, 0.95f, 0.45f, 0.6f, 0xFF0F8A86.toInt(), 1.0f, dx = 0.25f, dy = -0.1f, periodS = 21f),
            Light(0.05f, 0.9f, 0.3f, 0.5f, 0xFFF2873A.toInt(), 1.0f, dx = 0.12f, dy = -0.12f, periodS = 26f),
        )

        /** Google's logo colors: blue, red, yellow, green */
        private val GOOGLE = intArrayOf(0xFF4285F4.toInt(), 0xFFEA4335.toInt(), 0xFFFBBC04.toInt(), 0xFF34A853.toInt())
        private const val DRIFT_MS = 5_000L
        /** pink, peach, butter, mint, sky, periwinkle, lavender, orchid */
        private val COLORS = intArrayOf(
            0xFFFFB3C7.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(), 0xFFCAFFBF.toInt(),
            0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(), 0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(),
        )
    }
}
