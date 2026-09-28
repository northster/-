// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.translate

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * fork: a scroll wheel like the alarm clock's time picker: the item in the middle is the choice, big and bright,
 * the others fade out above and below. Drag or fling to turn it, tap an item to move it to the middle.
 */
@SuppressLint("ViewConstructor")
class WheelPicker(
    context: Context,
    private val items: List<String>,
    initial: Int,
    private val onSelect: (Int) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val itemHeight = 38 * density
    private val scroller = OverScroller(context)
    private var tracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    /** scroll position in px: item i is in the middle at i * itemHeight */
    private var position = initial.coerceIn(0, (items.size - 1).coerceAtLeast(0)) * itemHeight
    private var lastY = 0f
    private var downY = 0f
    private var dragging = false
    private var settling = false
    private var reported = initial

    private val maxPosition get() = (items.size - 1) * itemHeight
    val selected get() = (position / itemHeight).roundToInt().coerceIn(0, (items.size - 1).coerceAtLeast(0))

    private val colors = Settings.getValues().mColors
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val typefaces = items.map { KeyboardTypeface.resolve(it, Typeface.DEFAULT) }

    override fun onDraw(canvas: Canvas) {
        val centerY = height / 2f
        val first = ((position - centerY) / itemHeight).toInt() - 1
        val last = ((position + centerY) / itemHeight).toInt() + 1
        for (i in first.coerceAtLeast(0)..last.coerceAtMost(items.size - 1)) {
            val y = centerY + i * itemHeight - position
            val distance = abs(y - centerY) / itemHeight
            val near = (1f - distance).coerceIn(0f, 1f)
            paint.typeface = typefaces[i]
            paint.isFakeBoldText = distance < 0.5f
            paint.textSize = (15f + 7f * near) * density
            // the others in the key text color too, only a little dimmer, so they can be read
            paint.color = colors.get(ColorType.KEY_TEXT)
            paint.alpha = (255 * (0.6f + 0.4f * near).coerceAtMost(1f)).toInt()
            val text = fit(items[i], width - 8 * density)
            canvas.drawText(text, width / 2f, y - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }

    /** cut long labels with … so they stay inside the column */
    private fun fit(text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tracker == null) tracker = VelocityTracker.obtain()
        tracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                settling = false
                parent?.requestDisallowInterceptTouchEvent(true)
                lastY = event.y
                downY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.y - downY) > touchSlop) dragging = true
                if (dragging) {
                    position = (position - (event.y - lastY)).coerceIn(-itemHeight / 2, maxPosition + itemHeight / 2)
                    invalidate()
                }
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    // tap: that item comes to the middle
                    val tapped = ((position + event.y - height / 2f) / itemHeight).roundToInt().coerceIn(0, items.size - 1)
                    scrollTo(tapped)
                } else {
                    tracker?.computeCurrentVelocity(1000)
                    val velocity = tracker?.yVelocity ?: 0f
                    if (abs(velocity) > minFling) {
                        scroller.fling(0, position.toInt(), 0, -velocity.toInt(), 0, 0, 0, maxPosition.toInt())
                        settling = true
                        postInvalidateOnAnimation()
                    } else snap()
                }
                tracker?.recycle()
                tracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                snap()
                tracker?.recycle()
                tracker = null
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            position = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        } else if (settling) {
            settling = false
            snap()
        }
    }

    private fun snap() = scrollTo(selected)

    private fun scrollTo(index: Int) {
        val target = index * itemHeight
        if (abs(target - position) < 1f) {
            position = target
            invalidate()
            report()
            return
        }
        scroller.startScroll(0, position.toInt(), 0, (target - position).toInt(), 220)
        settling = true
        postInvalidateOnAnimation()
        report(index)
    }

    private fun report(index: Int = selected) {
        if (index == reported) return
        reported = index
        onSelect(index)
    }
}
