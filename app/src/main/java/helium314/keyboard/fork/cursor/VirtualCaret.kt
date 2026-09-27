// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.cursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.TextBoundsInfo
import android.view.inputmethod.TextBoundsInfoResult
import androidx.annotation.RequiresApi
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import kotlin.math.abs

/**
 * fork: iPhone style cursor movement on a held spacebar. A floating caret follows the finger over the text (not a
 * character per step), and the real cursor jumps to the character boundary closest to it.
 *
 * Needs the app to tell where its characters are: the caret position from [CursorAnchorInfo] and the character bounds
 * from [TextBoundsInfo] (Android 14+). Apps that answer neither get the old step-by-step movement (the caller falls
 * back when [drag] returns false).
 */
class VirtualCaret(private val ime: LatinIME) {
    private var active = false
    private var startTime = 0L
    /** caret position at the start, screen coordinates */
    private var caretX = 0f
    private var caretTop = 0f
    private var caretBottom = 0f
    private var haveCaret = false
    private var bounds: Any? = null // TextBoundsInfo, typed loosely so this class loads before Android 14
    private var lastOffset = -1
    private var overlay: CaretOverlay? = null

    /** the spacebar went into cursor mode */
    fun start() {
        end()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val ic = ime.currentInputConnection ?: return
        active = true
        haveCaret = false
        bounds = null
        lastOffset = -1
        startTime = SystemClock.uptimeMillis()
        // the answer comes through onUpdateCursorAnchorInfo
        if (!ic.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE)) active = false
    }

    fun onCursorAnchorInfo(info: CursorAnchorInfo) {
        if (!active || haveCaret || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val x = info.insertionMarkerHorizontal
        if (x.isNaN()) return
        val points = floatArrayOf(x, info.insertionMarkerTop, x, info.insertionMarkerBottom)
        info.matrix.mapPoints(points)
        caretX = points[0]
        caretTop = points[1]
        caretBottom = points[3]
        haveCaret = true
        requestBounds(info)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestBounds(info: CursorAnchorInfo) {
        val ic = ime.currentInputConnection ?: return
        // the whole editor if the app says where it is, else the screen around the caret
        val area = info.editorBoundsInfo?.editorBounds?.let { RectF(it).also { r -> info.matrix.mapRect(r) } }
            ?: RectF(0f, caretTop - 2000f, 10000f, caretBottom + 2000f)
        ic.requestTextBoundsInfo(area, ime.mainExecutor) { result: TextBoundsInfoResult ->
            if (active && result.resultCode == TextBoundsInfoResult.CODE_SUCCESS) bounds = result.textBoundsInfo
            else Log.i(TAG, "no text bounds from the app (${result.resultCode})")
        }
    }

    /**
     * The finger moved by [dx], [dy] px since cursor mode started.
     * @return false when this app can't do it, the caller moves the cursor step by step then
     */
    fun drag(dx: Int, dy: Int): Boolean {
        if (!active) return false
        if (!haveCaret) {
            // give the app a moment to answer, then fall back
            if (SystemClock.uptimeMillis() - startTime > WAIT_MILLIS) end()
            return active
        }
        val x = caretX + dx
        val y = (caretTop + caretBottom) / 2 + dy
        showCaret(x, y, caretBottom - caretTop)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val info = bounds as? TextBoundsInfo ?: return true
        val offset = nearestOffset(info, x, y)
        if (offset >= 0 && offset != lastOffset) {
            lastOffset = offset
            ime.forkSetCursor(offset)
        }
        return true
    }

    /** the character boundary (cursor position) closest to the screen point: the nearest line first, then along it */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun nearestOffset(info: TextBoundsInfo, screenX: Float, screenY: Float): Int {
        val toLocal = Matrix().also { m -> info.getMatrix(m) }.let { m -> Matrix().also { m.invert(it) } }
        val p = floatArrayOf(screenX, screenY)
        toLocal.mapPoints(p)
        val (x, y) = p
        val rect = RectF()
        var best = -1
        var bestCost = Float.MAX_VALUE
        for (i in info.startIndex until info.endIndex) {
            info.getCharacterBounds(i, rect)
            if (rect.isEmpty && rect.width() == 0f && rect.height() == 0f) continue
            val lineDistance = when {
                y < rect.top -> rect.top - y
                y > rect.bottom -> y - rect.bottom
                else -> 0f
            }
            // before the character, and after it
            for ((edge, offset) in listOf(rect.left to i, rect.right to i + 1)) {
                val cost = lineDistance * 1000f + abs(x - edge)
                if (cost < bestCost) {
                    bestCost = cost
                    best = offset
                }
            }
        }
        return best
    }

    /** the finger went up or the gesture was cancelled */
    fun end() {
        active = false
        haveCaret = false
        bounds = null
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = null
    }

    private fun showCaret(screenX: Float, screenY: Float, height: Float) {
        val root = ime.window?.window?.decorView as? ViewGroup ?: return
        val view = overlay ?: CaretOverlay(root.context).also {
            root.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            overlay = it
        }
        view.moveTo(screenX, screenY, height.coerceAtLeast(20f))
    }

    /** the floating caret, drawn in the keyboard window over the app (the window covers the screen) */
    private class CaretOverlay(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = Settings.getValues().mColors.get(ColorType.ACTION_KEY_BACKGROUND)
            it.setShadowLayer(4 * density, 0f, 1 * density, 0x66000000)
        }
        private val location = IntArray(2)
        private var x = 0f
        private var y = 0f
        private var height = 0f

        init {
            isClickable = false
            isFocusable = false
        }

        fun moveTo(screenX: Float, screenY: Float, h: Float) {
            getLocationOnScreen(location)
            x = screenX - location[0]
            y = screenY - location[1]
            height = h
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = 2.5f * density
            canvas.drawRoundRect(x - w / 2, y - height / 2, x + w / 2, y + height / 2, w, w, paint)
        }
    }

    companion object {
        private const val TAG = "VirtualCaret"
        /** how long to wait for the app's caret position before moving step by step */
        private const val WAIT_MILLIS = 250L
    }
}
