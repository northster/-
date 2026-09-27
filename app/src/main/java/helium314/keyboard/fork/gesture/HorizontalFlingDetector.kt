// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.gesture

import kotlin.math.abs
import kotlin.math.atan2

/**
 * fork: a quick, long sideways fling over the keys, which switches the one-handed keyboard. Much longer and faster
 * than sliding to a neighbour key, so typing is not affected. Pure logic, like [VerticalSwipeDetector].
 */
class HorizontalFlingDetector {
    enum class Result { UNDECIDED, LEFT, RIGHT, REJECTED }

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var minDistance = 0f
    var state = Result.REJECTED
        private set

    val isTracking get() = state == Result.UNDECIDED

    /** [minDistancePx]: how far the finger has to travel sideways */
    fun onDown(x: Float, y: Float, eventTime: Long, minDistancePx: Float) {
        downX = x
        downY = y
        downTime = eventTime
        minDistance = minDistancePx
        state = Result.UNDECIDED
    }

    fun abort() {
        state = Result.REJECTED
    }

    fun onMove(x: Float, y: Float, eventTime: Long): Result {
        if (state != Result.UNDECIDED) return state
        val dx = x - downX
        val dy = y - downY
        val elapsed = eventTime - downTime
        if (elapsed > MAX_DURATION_MS) return Result.REJECTED.also { state = it }
        if (abs(dx) < minDistance) return state
        val angle = Math.toDegrees(atan2(abs(dy).toDouble(), abs(dx).toDouble()))
        state = if (angle > MAX_ANGLE_DEG) Result.REJECTED else if (dx < 0) Result.LEFT else Result.RIGHT
        return state
    }

    companion object {
        const val MAX_DURATION_MS = 300L
        const val MAX_ANGLE_DEG = 22.0
        /** of the keyboard width */
        const val MIN_DISTANCE_FRACTION = 0.4f
    }
}
