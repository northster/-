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
        if (on == shown) return
        shown = on
        fade?.cancel()
        if (on) {
            host.overlay.remove(drawable)
            host.overlay.add(drawable)
            if (spin == null) spin = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3200
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    drawable.angle = it.animatedValue as Float
                    update()
                }
                start()
            }
        }
        fade = ValueAnimator.ofFloat(drawable.fade, if (on) 1f else 0f).apply {
            duration = 350
            addUpdateListener { drawable.fade = it.animatedValue as Float; update() }
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
                    val depth = minOf(fromTop, fromBottom, col, cols - 1 - col)
                    if (depth >= DEPTH) continue
                    val x = left + col * spacing
                    val y = top + row * spacing
                    // color from the direction around the center, turning with [angle]
                    val a = ((Math.toDegrees(kotlin.math.atan2((y - cy).toDouble(), (x - cx).toDouble())) + 360 + angle) % 360) / 360.0
                    paint.color = colorAt(a.toFloat())
                    paint.alpha = (255 * fade * FALLOFF[depth]).toInt()
                    canvas.drawCircle(x, y, r, paint)
                }
            }
        }

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
            /** rows of dots from the edge inwards, and how bright each is */
            private const val DEPTH = 4
            private val FALLOFF = floatArrayOf(1f, 0.55f, 0.25f, 0.08f)
            /** pink, peach, butter, mint, sky, periwinkle, lavender, back to pink */
            private val COLORS = intArrayOf(
                0xFFFFB3C7.toInt(), 0xFFFFD6A5.toInt(), 0xFFFDFFB6.toInt(), 0xFFCAFFBF.toInt(),
                0xFF9BF6FF.toInt(), 0xFFA0C4FF.toInt(), 0xFFBDB2FF.toInt(), 0xFFFFC6FF.toInt(), 0xFFFFB3C7.toInt(),
            )
        }
    }
}
