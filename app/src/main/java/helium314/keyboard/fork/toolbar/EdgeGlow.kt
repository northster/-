// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** fork: which edge(s) of the keyboard a glow comes from */
enum class GlowSide { TOP, BOTTOM, ALL }

/** fork: colors of an [EdgeGlowPainter]: the AI glow's blue, rainbow colors, or one color (the smart chip's) */
class GlowColors private constructor(val kind: Int, val color: Int) {
    companion object {
        val BLUE = GlowColors(0, 0)
        val RAINBOW = GlowColors(1, 0)
        fun single(color: Int) = GlowColors(2, color)
    }
}

/**
 * fork: the glows of the keyboard (AI running, smart chip waiting), in two looks:
 * - line: each chosen edge as a very thin white light line, with a bright tight glow and a short soft one going inward
 * - soft: a soft light coming in from the chosen edges, fading into the dark
 * Each edge is its own light and they are added up, so corners are round, never a diagonal seam. The light is
 * computed small (in linear light, tone mapped, bright parts going toward white) and drawn scaled up with a faint
 * grain; the thin lines are drawn sharp at full size. [animated]: the light drifts slowly along the edges (recomputed
 * every frame), else it is computed once per size.
 */
class EdgeGlowPainter(
    private val line: Boolean,
    side: GlowSide,
    private val colors: GlowColors,
    private val animated: Boolean,
) {
    private val weight = when (side) {
        GlowSide.TOP -> floatArrayOf(0f, 0f, 0f, 1f)
        GlowSide.BOTTOM -> floatArrayOf(0f, 0f, 1f, 0f)
        GlowSide.ALL -> floatArrayOf(1f, 1f, 1f, 0.55f)
    }
    private val oneSide = side != GlowSide.ALL
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var gw = 0
    private var gh = 0
    private var computedFor = -1L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val grainPaint = Paint().apply {
        shader = BitmapShader(GRAIN, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        alpha = 16
    }
    private val rect = RectF()
    private val single = lin(colors.color)
    private val ramp = RAINBOW.map { lin(it) }
    private val col = FloatArray(3)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val lineColors = IntArray(LINE_STOPS)
    private val linePos = FloatArray(LINE_STOPS) { it / (LINE_STOPS - 1f) }
    /** the thin lines: white, or the single color made pale */
    private val lineRgb = if (colors.kind == 2) mixWhite(colors.color, 0.6f) else 0xEEF2FF

    /** draws the glow into [bounds] at [time] ms, [alpha] 0..1 */
    fun draw(canvas: Canvas, bounds: Rect, time: Long, alpha: Float) {
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0 || alpha <= 0f) return
        val wantH = (GLOW_W * h / w).coerceIn(16, 200)
        if (bitmap == null || gh != wantH) {
            gw = GLOW_W
            gh = wantH
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
            pixels = IntArray(gw * gh)
            computedFor = -1L
        }
        val t = if (animated) time.toFloat() else 0f
        val key = (w.toLong() shl 32) or h.toLong()
        val bmp = bitmap ?: return
        if (animated || computedFor != key) {
            compute(w.toFloat() / h, t)
            bmp.setPixels(pixels, 0, gw, 0, 0, gw, gh)
            computedFor = key
        }
        rect.set(bounds)
        paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        val saved = canvas.saveLayer(rect, null)
        canvas.drawBitmap(bmp, null, rect, paint)
        canvas.drawRect(rect, grainPaint)
        canvas.restoreToCount(saved)
        if (line) drawLines(canvas, bounds, t, alpha)
    }

    private fun compute(aspect: Float, t: Float) {
        for (j in 0 until gh) {
            val v = (j + 0.5f) / gh
            for (i in 0 until gw) {
                val u = (i + 0.5f) / gw
                var r = 0f; var g = 0f; var b = 0f
                for (e in 0 until 4) {
                    if (weight[e] <= 0f) continue
                    val d = when (e) { 0 -> u * aspect; 1 -> (1 - u) * aspect; 2 -> 1 - v; else -> v }
                    if (d > (if (line) 0.6f else 1.6f)) continue // too far to matter
                    val along = if (e < 2) v else u
                    var k = weight[e] * modulation(along, t, EDGE_AT[e])
                    // one edge alone: its light fades out toward the keyboard's sides
                    if (oneSide) k *= rimFade(u * aspect, 0.12f) * rimFade((1 - u) * aspect, 0.12f)
                    if (line) {
                        val q1 = d / 0.018f
                        val near = k * 0.9f / (1 + q1 * q1)
                        val q2 = d / 0.075f
                        val wide = k * 0.55f / (1 + q2 * q2).pow(1.4f)
                        when (colors.kind) {
                            0 -> {
                                r += NEAR[0] * near + WIDE[0] * wide; g += NEAR[1] * near + WIDE[1] * wide; b += NEAR[2] * near + WIDE[2] * wide
                            }
                            1 -> {
                                rampAt(EDGE_AT[e] + along * 0.25f + t / 30000f)
                                val s = near + wide * 0.55f
                                r += col[0] * s; g += col[1] * s; b += col[2] * s
                            }
                            else -> {
                                val s = near + wide * 0.45f
                                r += single[0] * s; g += single[1] * s; b += single[2] * s
                            }
                        }
                    } else {
                        val light = k * (edgeLight(d, 0.32f, 0.02f) * 1.2f + edgeLight(d, 0.75f, 0.05f) * 0.15f)
                        when (colors.kind) {
                            0 -> {
                                val f = smooth((along * 1.1f - 0.05f + 0.06f * sin(t / 9000.0).toFloat()).coerceIn(0f, 1f))
                                r += (SOFT_A[0] + (SOFT_B[0] - SOFT_A[0]) * f) * light
                                g += (SOFT_A[1] + (SOFT_B[1] - SOFT_A[1]) * f) * light
                                b += (SOFT_A[2] + (SOFT_B[2] - SOFT_A[2]) * f) * light
                            }
                            1 -> {
                                rampAt(EDGE_AT[e] + along * 0.25f + t / 30000f)
                                r += col[0] * light; g += col[1] * light; b += col[2] * light
                            }
                            else -> { r += single[0] * light; g += single[1] * light; b += single[2] * light }
                        }
                    }
                }
                // bright light goes toward white, smoothly with how bright it is
                val m = maxOf(r, g, b)
                val wht = 0.38f * smooth(((m - 0.3f) / 0.9f).coerceIn(0f, 1f))
                r += (m - r) * wht; g += (m - g) * wht; b += (m - b) * wht
                val cr = fromLinear(1f - exp(-r * 1.15f))
                val cg = fromLinear(1f - exp(-g * 1.15f))
                val cb = fromLinear(1f - exp(-b * 1.15f))
                val a = maxOf(cr, cg, cb)
                pixels[j * gw + i] = if (a == 0) 0
                    else (a shl 24) or ((cr * 255 / a) shl 16) or ((cg * 255 / a) shl 8) or (cb * 255 / a)
            }
        }
    }

    /** the thin lines on the chosen edges, sharp at full size: brightness along the edge, faded across it (DST_IN) */
    private fun drawLines(canvas: Canvas, bounds: Rect, t: Float, strength: Float) {
        val l = bounds.left.toFloat()
        val tp = bounds.top.toFloat()
        val r = bounds.right.toFloat()
        val bt = bounds.bottom.toFloat()
        val width = (bounds.height() * 0.0045f * 4f).coerceAtLeast(2f)
        for (e in 0 until 4) {
            if (weight[e] <= 0f) continue
            for (k in 0 until LINE_STOPS) {
                var s = weight[e] * modulation(linePos[k], t, EDGE_AT[e]) * strength * 1.2f
                if (oneSide) s *= rimFade(linePos[k], 0.08f) * rimFade(1 - linePos[k], 0.08f)
                lineColors[k] = ((255 * s.coerceIn(0f, 1f)).toInt() shl 24) or lineRgb
            }
            val strip = when (e) {
                0 -> RectF(l, tp, l + width, bt)
                1 -> RectF(r - width, tp, r, bt)
                2 -> RectF(l, bt - width, r, bt)
                else -> RectF(l, tp, r, tp + width)
            }
            val along = if (e < 2) LinearGradient(0f, tp, 0f, bt, lineColors, linePos, Shader.TileMode.CLAMP)
                else LinearGradient(l, 0f, r, 0f, lineColors, linePos, Shader.TileMode.CLAMP)
            val across = when (e) {
                0 -> LinearGradient(l, 0f, l + width, 0f, ACROSS_COLORS, ACROSS_POS, Shader.TileMode.CLAMP)
                1 -> LinearGradient(r, 0f, r - width, 0f, ACROSS_COLORS, ACROSS_POS, Shader.TileMode.CLAMP)
                2 -> LinearGradient(0f, bt, 0f, bt - width, ACROSS_COLORS, ACROSS_POS, Shader.TileMode.CLAMP)
                else -> LinearGradient(0f, tp, 0f, tp + width, ACROSS_COLORS, ACROSS_POS, Shader.TileMode.CLAMP)
            }
            val saved = canvas.saveLayer(strip, null)
            linePaint.shader = along
            canvas.drawRect(strip, linePaint)
            fadePaint.shader = across
            canvas.drawRect(strip, fadePaint)
            canvas.restoreToCount(saved)
        }
    }

    /** brightness along an edge ([s] 0..1), one long gentle wave drifting slowly, never below 60 % */
    private fun modulation(s: Float, t: Float, at: Float): Float {
        val a = sin(2 * PI * (s * 0.6f + t / 14000f + at * 3)).toFloat()
        return 0.6f + 0.4f * smooth(0.5f + 0.5f * a)
    }

    private fun rampAt(pos: Float) {
        val f = ((pos % 1f) + 1f) % 1f
        val x = f * (ramp.size - 1)
        val i = x.toInt().coerceAtMost(ramp.size - 2)
        val k = x - i
        val a = ramp[i]
        val b = ramp[i + 1]
        col[0] = a[0] + (b[0] - a[0]) * k; col[1] = a[1] + (b[1] - a[1]) * k; col[2] = a[2] + (b[2] - a[2]) * k
    }

    companion object {
        private const val GLOW_W = 160
        /** left, right, bottom, top: where each starts in the drift and the colors */
        private val EDGE_AT = floatArrayOf(0f, 0.5f, 0.25f, 0.75f)
        private const val LINE_STOPS = 24
        private val ACROSS_POS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        private val ACROSS_COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0x5EFFFFFF, 0x23FFFFFF, 0x0DFFFFFF, 0x00FFFFFF)
        private val NEAR = lin(0xFF5B7CFF.toInt())
        private val WIDE = lin(0xFF1D2FB8.toInt())
        private val SOFT_A = lin(0xFF14A6A0.toInt())
        private val SOFT_B = lin(0xFF2B72F0.toInt())
        /** red, orange, yellow, green, blue, violet, round again */
        private val RAINBOW = intArrayOf(0xFFFF5A5A.toInt(), 0xFFFF9F43.toInt(), 0xFFFFE15A.toInt(), 0xFF5FD97A.toInt(),
            0xFF4C8DFF.toInt(), 0xFF9B5CFF.toInt(), 0xFFFF5A5A.toInt())
        /** fine film grain: random grays, drawn faintly over the light */
        private val GRAIN: Bitmap by lazy {
            val random = java.util.Random(7)
            val px = IntArray(128 * 128) { val v = random.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
            Bitmap.createBitmap(px, 128, 128, Bitmap.Config.ARGB_8888)
        }

        private fun smooth(x: Float) = x * x * (3 - 2 * x)
        /** light from an edge [d] away: dark right at it ([rim]), brightest just inside, a long tail ([reach]) */
        private fun edgeLight(d: Float, reach: Float, rim: Float): Float {
            val q = d / reach
            return (1 - exp(-d / rim)) / (1 + q * q).pow(1.8f)
        }
        private fun rimFade(d: Float, reach: Float) = 1 - exp(-d / reach)
        private fun toLinear(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }
        private fun fromLinear(v: Float): Int {
            val c = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.coerceAtLeast(0f).pow(1f / 2.4f) - 0.055f
            return (c * 255f + 0.5f).toInt().coerceIn(0, 255)
        }
        private fun lin(c: Int) = floatArrayOf(toLinear((c shr 16) and 0xFF), toLinear((c shr 8) and 0xFF), toLinear(c and 0xFF))
        private fun mixWhite(c: Int, f: Float): Int {
            fun ch(x: Int) = (x + (255 - x) * f).toInt().coerceIn(0, 255)
            return (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
        }
    }
}

/**
 * fork: the smart chip's glow behind the keys, in the chip's color: computed once, the breathing ([setIntensity]) and
 * fading ([setFade]) only change how strongly it is drawn.
 */
class ChipGlowDrawable(line: Boolean, side: GlowSide, color: Int, private val params: GlowPrefs.Glow) : Drawable() {
    private val painter = EdgeGlowPainter(line, side, GlowColors.single(color), animated = false)
    private var intensity = 0f
    private var fade = 1f
    private var level = 0f

    /** 0..1, set by the breathing animation; returns whether what is drawn changed */
    fun setIntensity(value: Float): Boolean {
        intensity = value
        return update()
    }

    /** 0..1 on top of the breathing, for fading the glow in and out; returns whether what is drawn changed */
    fun setFade(value: Float): Boolean {
        fade = value
        return update()
    }

    private fun update(): Boolean {
        val l = fade * (params.minAlpha + (params.maxAlpha - params.minAlpha) * intensity)
        if ((l * 255).toInt() == (level * 255).toInt()) return false
        level = l
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) = painter.draw(canvas, bounds, 0L, level)
    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(colorFilter: ColorFilter?) { }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
