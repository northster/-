// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * fork: pastel gradient glow along the whole keyboard border (toolbar included) while an AI command or translation
 * runs. Drawn in the input view's overlay, so it takes no space and the keys are not redrawn for it; the colors turn
 * slowly around the border, and it fades in and out.
 */
class AiGlow(private val host: View, private val bounds: () -> RectF?) {
    private val drawable = GlowDrawable(host.resources.displayMetrics.density)
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

    private class GlowDrawable(private val density: Float) : Drawable() {
        var angle = 0f
        var fade = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val matrix = Matrix()
        private val rect = RectF()
        private var shader: SweepGradient? = null
        private var shaderCenter = Pair(Float.NaN, Float.NaN)

        override fun draw(canvas: Canvas) {
            if (fade <= 0f || bounds.isEmpty) return
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            if (shaderCenter != Pair(cx, cy)) {
                shader = SweepGradient(cx, cy, COLORS, null)
                shaderCenter = Pair(cx, cy)
            }
            matrix.setRotate(angle, cx, cy)
            shader!!.setLocalMatrix(matrix)
            paint.shader = shader
            // a bright line at the edge, fading inwards
            val steps = 7
            val step = 2.2f * density
            for (i in 0 until steps) {
                val inset = step * i + step / 2
                rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
                paint.strokeWidth = step
                paint.alpha = (255 * fade * (1f - i / steps.toFloat()).let { it * it }).toInt()
                val radius = (14 * density - inset).coerceAtLeast(0f)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
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
