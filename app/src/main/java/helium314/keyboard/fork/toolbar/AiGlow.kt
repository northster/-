// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import helium314.keyboard.latin.utils.prefs

/**
 * fork: pastel gradient glow along the whole keyboard border (toolbar included) while an AI command or translation
 * runs. Drawn in the input view's overlay, so it takes no space and the keys are not redrawn for it; the colors turn
 * slowly around the border, and it fades in and out.
 */
class AiGlow(private val host: View, private val bounds: () -> RectF?) {
    private val drawable = GlowPrefs.glow(host.context.prefs()).let {
        // the same dot grid as the other glows
        GlowDrawable(host.resources.displayMetrics.density, it.spacingDp, it.dotDp)
    }
    private var spin: ValueAnimator? = null
    private var fade: ValueAnimator? = null
    private var shown = false

    fun show(on: Boolean) {
        val prefs = host.context.prefs()
        if (on && !prefs.getBoolean(GlowPrefs.AI_GLOW, true)) return
        if (on == shown) return
        shown = on
        fade?.cancel()
        val style = prefs.getString(GlowPrefs.AI_GLOW_STYLE, GlowPrefs.AI_STYLE_SWEEP) ?: GlowPrefs.AI_STYLE_SWEEP
        // sweep / rise draw themselves in and out, fade / bottom fade
        val drawsIn = style == GlowPrefs.AI_STYLE_SWEEP || style == GlowPrefs.AI_STYLE_RISE
        if (on) {
            drawable.style = style
            if (drawsIn) { drawable.fade = 1f; drawable.reveal = 0f; drawable.erase = 0f }
            else { drawable.reveal = 1f; drawable.erase = 0f }
            drawable.brightness = prefs.getFloat(GlowPrefs.AI_GLOW_BRIGHTNESS, GlowPrefs.DEFAULT_AI_GLOW_BRIGHTNESS).coerceIn(0.1f, 1f)
            drawable.depth = if (style == GlowPrefs.AI_STYLE_BOTTOM)
                    prefs.getFloat(GlowPrefs.AI_GLOW_BOTTOM_DEPTH, GlowPrefs.DEFAULT_AI_GLOW_BOTTOM_DEPTH).toInt().coerceIn(1, 60)
                else prefs.getFloat(GlowPrefs.AI_GLOW_DEPTH, GlowPrefs.DEFAULT_AI_GLOW_DEPTH).toInt().coerceIn(1, 8)
            val period = prefs.getFloat(GlowPrefs.AI_GLOW_PERIOD, GlowPrefs.DEFAULT_AI_GLOW_PERIOD).toLong().coerceIn(500, 20000)
            spin?.duration = period
            host.overlay.remove(drawable)
            host.overlay.add(drawable)
            if (spin == null) spin = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = period
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    drawable.angle = it.animatedValue as Float
                    update()
                }
                start()
            }
        }
        val (from, to) = when {
            !drawsIn -> drawable.fade to (if (on) 1f else 0f)
            on -> drawable.reveal to 1f
            else -> drawable.erase to 1f
        }
        fade = ValueAnimator.ofFloat(from, to).apply {
            // slow and eased, so it doesn't pop in or out
            val inMs = prefs.getFloat(GlowPrefs.AI_GLOW_IN, GlowPrefs.DEFAULT_AI_GLOW_IN).toLong().coerceIn(100, 5000)
            duration = if (drawsIn || !on) inMs else inMs * 2 / 3
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                when {
                    !drawsIn -> drawable.fade = v
                    on -> drawable.reveal = v
                    else -> drawable.erase = v
                }
                update()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!on && !cancelled) {
                        spin?.cancel()
                        spin = null
                        host.overlay.remove(drawable)
                    }
                }
            })
            start()
        }
    }

    private fun update() {
        val r = bounds() ?: return
        drawable.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
        drawable.invalidateSelf()
        host.invalidate()
    }

    /** dots of the glow's grid along the square border, pastel colors turning around it, fading inwards */
    private class GlowDrawable(private val density: Float, private val spacingDp: Float, private val dotDp: Float) : Drawable() {
        var angle = 0f
        var fade = 0f
        var brightness = 1f
        var depth = 4
        var style = GlowPrefs.AI_STYLE_SWEEP
        /** sweep / rise: how much has been drawn in (0..1) and taken away again (0..1) */
        var reveal = 1f
        var erase = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            if (fade <= 0f || bounds.isEmpty) return
            val spacing = spacingDp * density
            val r = dotDp * density
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val cols = (w / spacing).toInt()
            val rows = (h / spacing).toInt()
            // the grid centered in the bounds, so the outer dots sit the same distance from every edge
            val left = bounds.left + (w - (cols - 1) * spacing) / 2
            val top = bounds.top + (h - (rows - 1) * spacing) / 2
            for (row in 0 until rows) {
                val fromTop = row
                val fromBottom = rows - 1 - row
                for (col in 0 until cols) {
                    val d = if (style == GlowPrefs.AI_STYLE_BOTTOM) fromBottom
                        else minOf(fromTop, fromBottom, col, cols - 1 - col)
                    if (d >= depth) continue
                    val x = left + col * spacing
                    val y = top + row * spacing
                    // color from the direction around the center, turning with [angle]
                    val a = ((Math.toDegrees(kotlin.math.atan2((y - cy).toDouble(), (x - cx).toDouble())) + 360 + angle) % 360) / 360.0
                    // along the bottom the colors flow sideways, elsewhere they turn around the center
                    paint.color = colorAt(if (style == GlowPrefs.AI_STYLE_BOTTOM)
                        ((col / cols.toFloat()) + angle / 360f) % 1f else a.toFloat())
                    // brightest at the edge, fading inwards
                    val falloff = (1f - d / depth.toFloat()).let { it * it }
                    val shown = when (style) {
                        // around the border clockwise from the bottom middle; it goes away the same way round
                        GlowPrefs.AI_STYLE_SWEEP -> {
                            val deg = Math.toDegrees(kotlin.math.atan2((cx - x).toDouble(), (y - cy).toDouble()))
                            val f = (((deg + 360) % 360) / 360).toFloat()
                            edge(reveal - f) * edge(f - erase)
                        }
                        // from the bottom up; it goes away from the top down
                        GlowPrefs.AI_STYLE_RISE -> {
                            val f = 1f - (y - bounds.top) / h
                            edge(reveal - f) * edge(1f - erase - f)
                        }
                        else -> 1f
                    }
                    if (shown <= 0f) continue
                    paint.alpha = (255 * fade * brightness * falloff * shown).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x, y, r, paint)
                }
            }
        }

        /** a soft front instead of a hard one: 0 behind it, 1 a little after it */
        private fun edge(v: Float) = (v / 0.06f).coerceIn(0f, 1f)

        private fun colorAt(t: Float): Int {
            val pos = t * (COLORS.size - 1)
            val i = pos.toInt().coerceIn(0, COLORS.size - 2)
            val f = pos - i
            val c1 = COLORS[i]
            val c2 = COLORS[i + 1]
            fun ch(shift: Int) = (((c1 shr shift) and 0xFF) * (1 - f) + ((c2 shr shift) and 0xFF) * f).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(colorFilter: ColorFilter?) { }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT

        companion object {
            /** pink, peach, butter, mint, sky, periwinkle, lavender, back to pink */
            private val COLORS = intArrayOf(
                0xFFFFB3C7.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(), 0xFFCAFFBF.toInt(),
                0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(), 0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(), 0xFFFFB3C7.toInt(),
            )
        }
    }
}
