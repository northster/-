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
 * - [GlowPrefs.AI_STYLE_EDGE_LINES] (default) / [GlowPrefs.AI_STYLE_EDGE_LINES_RAINBOW]: the keyboard's edges as thin
 *   white light lines glowing a little way inward, blue or in rainbow colors
 * - [GlowPrefs.AI_STYLE_LIGHT_TOP] / [GlowPrefs.AI_STYLE_LIGHT_SIDES]: light coming in along the top edge, or the left
 *   edge and the bottom right corner, fading into the dark
 * - [GlowPrefs.AI_STYLE_DOTS]: the chip glow's dots and shape, the HomePod colors blending softly across it
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
            tb = bar
            bar?.setUnderGlow(tbPart)
        }
        kvPart.offsetY = kvTop - top
        glow.setBounds(0, 0, view.width, kvTop - top + view.height)
        bar?.invalidate()
    }
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
            val glow = when (GlowPrefs.aiStyle(prefs)) {
                GlowPrefs.AI_STYLE_DOTS -> PastelDotsDrawable(host.resources.displayMetrics.density, params,
                    prefs.getFloat(GlowPrefs.AI_GLOW_BLOOM, GlowPrefs.DEFAULT_AI_GLOW_BLOOM).coerceIn(0f, 1f))
                GlowPrefs.AI_STYLE_LIGHT_TOP -> EdgeWashDrawable(top = true, params)
                GlowPrefs.AI_STYLE_LIGHT_SIDES -> EdgeWashDrawable(top = false, params)
                GlowPrefs.AI_STYLE_EDGE_LINES_RAINBOW -> EdgeLinesDrawable(rainbow = true, params)
                else -> EdgeLinesDrawable(rainbow = false, params)
            }
            drawable = glow
            kv = view
            place(view, glow)
            view.setForkAiUnderlay(kvPart)
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
        kv = null
        tb?.setUnderGlow(null)
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

    /**
     * light coming in along a whole edge (and from just outside a corner), like Gemini's pictures: brightest a little
     * inside the edge, a long smooth tail, no round blobs that bunch up. [top]: from the top edge, teal on the left to
     * blue on the right (the Gemini app); else from the left edge, orange above to violet and indigo below, with blue from
     * the bottom right corner. Every edge fades gently, multiplied so the corners curve evenly (no diagonal seams), and
     * bright light goes smoothly toward white. Computed in a small picture each frame and drawn scaled up, filtered and
     * dithered, with a fine film grain over it; dark is see-through.
     */
    private class EdgeWashDrawable(private val top: Boolean, private val params: GlowPrefs.Glow) : Layer() {
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
        private val c1 = lin(if (top) 0xFF14A6A0.toInt() else 0xFFFFA22A.toInt())
        private val c2 = lin(if (top) 0xFF2B72F0.toInt() else 0xFF8D44DC.toInt())
        private val c3 = lin(if (top) 0xFF1B3FA8.toInt() else 0xFF2C1FA0.toInt())
        private val c4 = lin(if (top) 0xFF0D7A48.toInt() else 0xFF2F7CF0.toInt())
        private val out = FloatArray(3)

        override fun draw(canvas: Canvas) {
            val w = bounds.width()
            val h = bounds.height()
            if (w <= 0 || h <= 0 || fade <= 0f) return
            val wantH = (GLOW_W * h / w).coerceIn(16, 200)
            if (bitmap == null || gh != wantH) {
                gw = GLOW_W
                gh = wantH
                bitmap?.recycle()
                bitmap = android.graphics.Bitmap.createBitmap(gw, gh, android.graphics.Bitmap.Config.ARGB_8888)
                pixels = IntArray(gw * gh)
            }
            val aspect = w.toFloat() / h
            val t = time.toFloat()
            val breath = 0.9f + 0.1f * sin(2 * PI * time / params.periodMs.coerceAtLeast(1)).toFloat()
            val bright = (params.maxAlpha / GlowPrefs.DEFAULT_AI_GLOW_MAX).coerceIn(0f, 1.5f) * breath
            val drift1 = sin(t / 9000.0).toFloat()
            val drift2 = sin(t / 5000.0).toFloat()
            val drift3 = sin(t / 8000.0).toFloat()
            val drift4 = sin(t / 6000.0).toFloat()
            for (j in 0 until gh) {
                val v = (j + 0.5f) / gh
                for (i in 0 until gw) {
                    val u = (i + 0.5f) / gw
                    var r: Float; var g: Float; var b: Float
                    if (top) {
                        val f = smooth((u * 1.1f - 0.05f + 0.06f * drift1).coerceIn(0f, 1f))
                        val topL = edgeLight(v, 0.3f + 0.03f * drift2, 0.02f) * (0.85f + 0.35f * f) * 1.2f
                        val wide = edgeLight(v, 0.7f, 0.05f) * 0.12f
                        val dv = (v - 0.45f) / 0.35f
                        val left = edgeLight(u * aspect, 0.22f, 0.02f) * kotlin.math.exp(-dv * dv) * 0.35f
                        r = (c1[0] + (c2[0] - c1[0]) * f) * topL + c3[0] * wide + c4[0] * left
                        g = (c1[1] + (c2[1] - c1[1]) * f) * topL + c3[1] * wide + c4[1] * left
                        b = (c1[2] + (c2[2] - c1[2]) * f) * topL + c3[2] * wide + c4[2] * left
                    } else {
                        val gg = smooth((v * 1.15f + 0.05f * drift3).coerceIn(0f, 1f))
                        val lower = gg >= 0.5f
                        val ca = if (lower) c2 else c1
                        val cb = if (lower) c3 else c2
                        val ff = if (lower) gg * 2 - 1 else gg * 2
                        val leftL = edgeLight(u * aspect, 0.36f + 0.03f * drift4, 0.02f) * (1.2f - 0.45f * gg) * 1.2f
                        val dx = (1 - u) * aspect
                        val dy = 1 - v
                        val dc2 = (dx * dx + dy * dy) / (0.4f * 0.4f)
                        val corner = 1.2f / (1 + dc2).pow(1.5f)
                        r = (ca[0] + (cb[0] - ca[0]) * ff) * leftL + c4[0] * corner
                        g = (ca[1] + (cb[1] - ca[1]) * ff) * leftL + c4[1] * corner
                        b = (ca[2] + (cb[2] - ca[2]) * ff) * leftL + c4[2] * corner
                    }
                    val rim = rimFade(u * aspect) * rimFade((1 - u) * aspect) * rimFade(v) * rimFade(1 - v) * bright
                    r *= rim; g *= rim; b *= rim
                    // bright light goes toward white, smoothly with how bright it is
                    val m = maxOf(r, g, b)
                    val wht = 0.38f * smooth(((m - 0.3f) / 0.9f).coerceIn(0f, 1f))
                    r += (m - r) * wht; g += (m - g) * wht; b += (m - b) * wht
                    val cr = fromLinear(1f - kotlin.math.exp(-r * 1.15f))
                    val cg = fromLinear(1f - kotlin.math.exp(-g * 1.15f))
                    val cb2 = fromLinear(1f - kotlin.math.exp(-b * 1.15f))
                    val a = maxOf(cr, cg, cb2)
                    pixels[j * gw + i] = if (a == 0) 0
                        else (a shl 24) or ((cr * 255 / a) shl 16) or ((cg * 255 / a) shl 8) or (cb2 * 255 / a)
                }
            }
            val bmp = bitmap ?: return
            bmp.setPixels(pixels, 0, gw, 0, 0, gw, gh)
            rect.set(bounds)
            paint.alpha = (255 * fade).toInt().coerceIn(0, 255)
            val saved = canvas.saveLayer(rect, null)
            canvas.drawBitmap(bmp, null, rect, paint)
            canvas.drawRect(rect, grainPaint)
            canvas.restoreToCount(saved)
        }

        private fun lin(c: Int) = floatArrayOf(toLinear((c shr 16) and 0xFF), toLinear((c shr 8) and 0xFF), toLinear(c and 0xFF))
        private fun smooth(x: Float) = x * x * (3 - 2 * x)
        /** light from an edge [d] away: dark right at it ([rim]), brightest just inside, a long tail ([reach]) */
        private fun edgeLight(d: Float, reach: Float, rim: Float): Float {
            val q = d / reach
            return (1 - kotlin.math.exp(-d / rim)) / (1 + q * q).pow(1.8f)
        }
        private fun rimFade(d: Float) = 1 - kotlin.math.exp(-d / 0.035f)
    }

    /**
     * the keyboard's edges as light lines: a very thin white line right on each edge, a bright tight glow and a short
     * soft one going inward, the middle staying dark. Each edge is its own light and they are added up, so a corner is
     * brighter and round, never a diagonal seam. Along each edge the brightness drifts slowly; the top edge (the
     * toolbar's) is weaker. Colors: blue, or ([rainbow]) rainbow colors changing slowly round the keyboard.
     * The glows are computed small and drawn scaled up like [EdgeWashDrawable]; the thin white lines are drawn sharp at
     * full size, each in its own layer: brightness along the edge, faded across it (DST_IN).
     */
    private class EdgeLinesDrawable(private val rainbow: Boolean, private val params: GlowPrefs.Glow) : Layer() {
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
        private val near = lin(0xFF5B7CFF.toInt())
        private val wide = lin(0xFF1D2FB8.toInt())
        private val rampColors = RAINBOW.map { lin(it) }
        private val col = FloatArray(3)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val fadePaint = Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        private val lineColors = IntArray(LINE_STOPS)
        private val linePos = FloatArray(LINE_STOPS) { it / (LINE_STOPS - 1f) }

        override fun draw(canvas: Canvas) {
            val w = bounds.width()
            val h = bounds.height()
            if (w <= 0 || h <= 0 || fade <= 0f) return
            val wantH = (GLOW_W * h / w).coerceIn(16, 200)
            if (bitmap == null || gh != wantH) {
                gw = GLOW_W
                gh = wantH
                bitmap?.recycle()
                bitmap = android.graphics.Bitmap.createBitmap(gw, gh, android.graphics.Bitmap.Config.ARGB_8888)
                pixels = IntArray(gw * gh)
            }
            val aspect = w.toFloat() / h
            val t = time.toFloat()
            val breath = 0.9f + 0.1f * sin(2 * PI * time / params.periodMs.coerceAtLeast(1)).toFloat()
            val bright = (params.maxAlpha / GlowPrefs.DEFAULT_AI_GLOW_MAX).coerceIn(0f, 1.5f) * breath
            for (j in 0 until gh) {
                val v = (j + 0.5f) / gh
                for (i in 0 until gw) {
                    val u = (i + 0.5f) / gw
                    var r = 0f; var g = 0f; var b = 0f
                    for (e in 0 until 4) {
                        val d = when (e) { 0 -> u * aspect; 1 -> (1 - u) * aspect; 2 -> 1 - v; else -> v }
                        if (d > 0.6f) continue // too far to matter
                        val along = if (e < 2) v else u
                        val k = EDGE_WEIGHT[e] * modulation(along, t, EDGE_AT[e])
                        val q1 = d / 0.018f
                        val glow = 1f / (1 + q1 * q1)
                        val q2 = d / 0.075f
                        val bloom = 1f / (1 + q2 * q2).pow(1.4f)
                        val wNear = k * glow * 0.9f
                        val wWide = k * bloom * 0.55f
                        if (rainbow) {
                            rampAt(EDGE_AT[e] + along * 0.25f + t / 30000f)
                            r += col[0] * (wNear + wWide * 0.55f); g += col[1] * (wNear + wWide * 0.55f); b += col[2] * (wNear + wWide * 0.55f)
                        } else {
                            r += near[0] * wNear + wide[0] * wWide; g += near[1] * wNear + wide[1] * wWide; b += near[2] * wNear + wide[2] * wWide
                        }
                    }
                    r *= bright; g *= bright; b *= bright
                    val m = maxOf(r, g, b)
                    val wht = 0.38f * smooth(((m - 0.3f) / 0.9f).coerceIn(0f, 1f))
                    r += (m - r) * wht; g += (m - g) * wht; b += (m - b) * wht
                    val cr = fromLinear(1f - kotlin.math.exp(-r * 1.15f))
                    val cg = fromLinear(1f - kotlin.math.exp(-g * 1.15f))
                    val cb = fromLinear(1f - kotlin.math.exp(-b * 1.15f))
                    val a = maxOf(cr, cg, cb)
                    pixels[j * gw + i] = if (a == 0) 0
                        else (a shl 24) or ((cr * 255 / a) shl 16) or ((cg * 255 / a) shl 8) or (cb * 255 / a)
                }
            }
            val bmp = bitmap ?: return
            bmp.setPixels(pixels, 0, gw, 0, 0, gw, gh)
            rect.set(bounds)
            paint.alpha = (255 * fade).toInt().coerceIn(0, 255)
            val saved = canvas.saveLayer(rect, null)
            canvas.drawBitmap(bmp, null, rect, paint)
            canvas.drawRect(rect, grainPaint)
            canvas.restoreToCount(saved)
            drawLines(canvas, t, (bright * fade).coerceIn(0f, 1.5f))
        }

        /** the thin white lines on the edges, sharp at full size */
        private fun drawLines(canvas: Canvas, t: Float, strength: Float) {
            val l = bounds.left.toFloat()
            val tp = bounds.top.toFloat()
            val r = bounds.right.toFloat()
            val bt = bounds.bottom.toFloat()
            // about exp(-d / 0.0045 of the height), drawn 4 times that wide
            val width = (bounds.height() * 0.0045f * 4f).coerceAtLeast(2f)
            for (e in 0 until 4) {
                for (k in 0 until LINE_STOPS) {
                    val a = (255 * (EDGE_WEIGHT[e] * modulation(linePos[k], t, EDGE_AT[e]) * strength * 1.2f).coerceIn(0f, 1f)).toInt()
                    lineColors[k] = (a shl 24) or 0xEEF2FF
                }
                val strip = when (e) {
                    0 -> android.graphics.RectF(l, tp, l + width, bt)
                    1 -> android.graphics.RectF(r - width, tp, r, bt)
                    2 -> android.graphics.RectF(l, bt - width, r, bt)
                    else -> android.graphics.RectF(l, tp, r, tp + width)
                }
                val along = if (e < 2) android.graphics.LinearGradient(0f, tp, 0f, bt, lineColors, linePos, Shader.TileMode.CLAMP)
                    else android.graphics.LinearGradient(l, 0f, r, 0f, lineColors, linePos, Shader.TileMode.CLAMP)
                // across: full at the edge, fading inward (exp: 1, 1/e, 1/e^2, ...)
                val across = when (e) {
                    0 -> android.graphics.LinearGradient(l, 0f, l + width, 0f, ACROSS_COLORS, ACROSS_POS, Shader.TileMode.CLAMP)
                    1 -> android.graphics.LinearGradient(r, 0f, r - width, 0f, ACROSS_COLORS, ACROSS_POS, Shader.TileMode.CLAMP)
                    2 -> android.graphics.LinearGradient(0f, bt, 0f, bt - width, ACROSS_COLORS, ACROSS_POS, Shader.TileMode.CLAMP)
                    else -> android.graphics.LinearGradient(0f, tp, 0f, tp + width, ACROSS_COLORS, ACROSS_POS, Shader.TileMode.CLAMP)
                }
                val saved = canvas.saveLayer(strip, null)
                linePaint.shader = along
                canvas.drawRect(strip, linePaint)
                fadePaint.shader = across
                canvas.drawRect(strip, fadePaint)
                canvas.restoreToCount(saved)
            }
        }

        /**
         * brightness along an edge ([s] 0..1), drifting slowly; [at] makes each edge different. One long, gentle wave
         * and never below 60 %, so the light looks even, not blotchy
         */
        private fun modulation(s: Float, t: Float, at: Float): Float {
            val a = sin(2 * PI * (s * 0.6f + t / 14000f + at * 3)).toFloat()
            return 0.6f + 0.4f * smooth(0.5f + 0.5f * a)
        }

        /** rainbow colors round the keyboard, into [col] (linear) */
        private fun rampAt(pos: Float) {
            val f = ((pos % 1f) + 1f) % 1f
            val x = f * (rampColors.size - 1)
            val i = x.toInt().coerceAtMost(rampColors.size - 2)
            val k = x - i
            val a = rampColors[i]
            val b = rampColors[i + 1]
            col[0] = a[0] + (b[0] - a[0]) * k; col[1] = a[1] + (b[1] - a[1]) * k; col[2] = a[2] + (b[2] - a[2]) * k
        }

        private fun lin(c: Int) = floatArrayOf(toLinear((c shr 16) and 0xFF), toLinear((c shr 8) and 0xFF), toLinear(c and 0xFF))
        private fun smooth(x: Float) = x * x * (3 - 2 * x)
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
        private fun toLinear(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }

        private fun fromLinear(v: Float): Int {
            val c = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.coerceAtLeast(0f).pow(1f / 2.4f) - 0.055f
            return (c * 255f + 0.5f).toInt().coerceIn(0, 255)
        }

        /** the light looks: width of the small picture they are computed in */
        private const val GLOW_W = 160

        /** the edge lines: left, right, bottom, top; how strong, and where each starts in the drift and the colors */
        private val EDGE_WEIGHT = floatArrayOf(1f, 1f, 1f, 0.4f)
        private val EDGE_AT = floatArrayOf(0f, 0.5f, 0.25f, 0.75f)
        private const val LINE_STOPS = 24
        private val ACROSS_POS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        private val ACROSS_COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0x5EFFFFFF, 0x23FFFFFF, 0x0DFFFFFF, 0x00FFFFFF)
        /** rainbow round the keyboard: red, orange, yellow, green, blue, violet */
        private val RAINBOW = intArrayOf(0xFFFF5A5A.toInt(), 0xFFFF9F43.toInt(), 0xFFFFE15A.toInt(), 0xFF5FD97A.toInt(),
            0xFF4C8DFF.toInt(), 0xFF9B5CFF.toInt(), 0xFFFF5A5A.toInt())

        /** fine film grain: random grays, drawn faintly over the light (lighter and darker specks) */
        private fun grain(): android.graphics.Bitmap {
            val random = java.util.Random(7)
            val px = IntArray(128 * 128) { val v = random.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
            return android.graphics.Bitmap.createBitmap(px, 128, 128, android.graphics.Bitmap.Config.ARGB_8888)
        }

        private const val DRIFT_MS = 5_000L
    }
}
