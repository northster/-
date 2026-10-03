// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * fork: the popup area of the toolbar, right of the tool buttons: one widget at a time (Claude usage, trivia, emoji
 * suggestions ...), the next one with a sideways swipe.
 */
@SuppressLint("ViewConstructor")
class WidgetArea(context: Context) : FrameLayout(context) {
    class Page(val id: String, val view: View)

    private var pages: List<Page> = emptyList()
    var current = 0
        private set
    /** the widget now shown changed (id) */
    var onPageChanged: ((String) -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var swiping = false

    fun setPages(list: List<Page>, selectId: String?) {
        removeAllViews()
        pages = list
        for (p in list) {
            (p.view.parent as? android.view.ViewGroup)?.removeView(p.view)
            addView(p.view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        current = list.indexOfFirst { it.id == selectId }.coerceAtLeast(0)
        show(current, 0)
    }

    val currentId get() = pages.getOrNull(current)?.id

    private fun show(index: Int, direction: Int) {
        pages.forEachIndexed { i, p ->
            val v = p.view
            v.animate().cancel()
            if (i == index) {
                v.visibility = VISIBLE
                if (direction != 0) {
                    v.translationX = direction * width * 0.4f
                    v.alpha = 0f
                    v.animate().translationX(0f).alpha(1f).setDuration(180).start()
                } else {
                    v.translationX = 0f
                    v.alpha = 1f
                }
            } else v.visibility = GONE
        }
    }

    /** the next ([by] 1) or previous (-1) widget */
    fun step(by: Int) {
        if (pages.size < 2) return
        current = (current + by + pages.size) % pages.size
        show(current, by)
        onPageChanged?.invoke(pages[current].id)
    }

    // sideways swipes switch the widget, taps go to the widget
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; swiping = false }
            MotionEvent.ACTION_MOVE -> if (!swiping && pages.size > 1 && abs(ev.x - downX) > slop * 2
                    && abs(ev.x - downX) > abs(ev.y - downY)) {
                swiping = true
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; swiping = false }
            MotionEvent.ACTION_MOVE -> if (!swiping && pages.size > 1 && abs(event.x - downX) > slop * 2
                    && abs(event.x - downX) > abs(event.y - downY)) swiping = true
            MotionEvent.ACTION_UP -> if (swiping) {
                step(if (event.x < downX) 1 else -1)
                swiping = false
            }
            MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return true
    }
}
