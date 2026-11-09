// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.utils.prefs
import kotlin.math.PI
import kotlin.math.sin

/**
 * fork: glow behind the keys and the toolbar while an AI command or translation runs, seen through the key mask like the
 * smart chip's glow; it breathes and fades in and out. It is one glow over both: the keyboard view and the toolbar each
 * draw their part of it. Its look (line / soft), edges and colors are chosen in the settings ([GlowPrefs.aiLook],
 * [GlowPrefs.aiSide], [GlowPrefs.aiRainbow]) and drawn by [EdgeGlowPainter].
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
            val glow = EdgeLayer(GlowPrefs.aiLook(prefs) == GlowPrefs.LOOK_LINE, GlowPrefs.aiSide(prefs),
                GlowPrefs.aiRainbow(prefs), params)
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

    /** the glow as [EdgeGlowPainter] draws it, drifting slowly and breathing a little */
    private class EdgeLayer(line: Boolean, side: GlowSide, rainbow: Boolean, private val params: GlowPrefs.Glow) : Layer() {
        private val painter = EdgeGlowPainter(line, side, if (rainbow) GlowColors.RAINBOW else GlowColors.BLUE, animated = true)
        override fun draw(canvas: Canvas) {
            val breath = 0.9f + 0.1f * sin(2 * PI * time / params.periodMs.coerceAtLeast(1)).toFloat()
            val bright = (params.maxAlpha / GlowPrefs.DEFAULT_AI_GLOW_MAX).coerceIn(0f, 1.4f) * breath
            painter.draw(canvas, bounds, time, (fade * bright).coerceIn(0f, 1f))
        }
    }
}
