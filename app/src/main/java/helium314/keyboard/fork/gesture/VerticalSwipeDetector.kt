// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.gesture

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Thresholds for telling a vertical swipe on the key area apart from a tap.
 * All distances are in px (already converted from dp), times in ms.
 */
data class SwipeThresholds(
    /** vertical travel needed before a decision is made */
    val minDistancePx: Float,
    /** average speed (from touch down) needed when [minDistancePx] is reached, px per ms */
    val minVelocityPxPerMs: Float,
    /** maximum deviation from the vertical axis, in degrees (0 = perfectly vertical) */
    val maxAngleDeg: Float,
    /** horizontal travel after which the pointer is treated as a horizontal movement and never becomes a swipe */
    val horizontalRejectPx: Float,
    /** if the vertical threshold is not reached within this time, the pointer is treated as a normal key press */
    val maxDurationMs: Long,
) {
    companion object {
        // defaults, also used by the settings screen
        const val DEFAULT_MIN_DISTANCE_DP = 40
        const val DEFAULT_MIN_VELOCITY_DP_PER_S = 300
        const val DEFAULT_MAX_ANGLE_DEG = 30
        const val DEFAULT_HORIZONTAL_REJECT_DP = 24
        const val DEFAULT_MAX_DURATION_MS = 350

        fun fromDp(
            density: Float,
            minDistanceDp: Int = DEFAULT_MIN_DISTANCE_DP,
            minVelocityDpPerS: Int = DEFAULT_MIN_VELOCITY_DP_PER_S,
            maxAngleDeg: Int = DEFAULT_MAX_ANGLE_DEG,
            horizontalRejectDp: Int = DEFAULT_HORIZONTAL_REJECT_DP,
            maxDurationMs: Int = DEFAULT_MAX_DURATION_MS,
        ) = SwipeThresholds(
            minDistancePx = minDistanceDp * density,
            minVelocityPxPerMs = minVelocityDpPerS * density / 1000f,
            maxAngleDeg = maxAngleDeg.toFloat(),
            horizontalRejectPx = horizontalRejectDp * density,
            maxDurationMs = maxDurationMs.toLong(),
        )
    }
}

/**
 * Decides whether a single pointer, which went down on a key, is a vertical swipe (toolbar gesture)
 * or something else (tap, horizontal slide, long press...).
 *
 * The decision is sticky: once [Result.SWIPE_UP], [Result.SWIPE_DOWN] or [Result.REJECTED] is returned,
 * later moves don't change it. Pure logic without Android dependencies, so it can be unit tested.
 */
class VerticalSwipeDetector {
    enum class Result { UNDECIDED, SWIPE_UP, SWIPE_DOWN, REJECTED }

    private var thresholds: SwipeThresholds? = null
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    var state = Result.REJECTED
        private set

    /** Whether moves should still be fed into [onMove]. */
    val isTracking get() = state == Result.UNDECIDED

    fun onDown(x: Float, y: Float, eventTime: Long, thresholds: SwipeThresholds) {
        this.thresholds = thresholds
        downX = x
        downY = y
        downTime = eventTime
        state = Result.UNDECIDED
    }

    /** Stop tracking, e.g. because a long press popup appeared or a second finger went down. */
    fun abort() {
        state = Result.REJECTED
    }

    fun onMove(x: Float, y: Float, eventTime: Long): Result {
        if (state != Result.UNDECIDED) return state
        val t = thresholds ?: return Result.REJECTED.also { state = it }
        val dx = x - downX
        val dy = y - downY
        val elapsed = eventTime - downTime

        if (abs(dx) >= t.horizontalRejectPx && abs(dx) >= abs(dy)) {
            state = Result.REJECTED // horizontal movement, leave it to normal key handling
            return state
        }
        if (abs(dy) < t.minDistancePx) {
            if (elapsed > t.maxDurationMs) state = Result.REJECTED // too slow, finger is resting or dragging
            return state
        }
        // vertical distance reached: now check direction and speed
        val angleFromVertical = Math.toDegrees(atan2(abs(dx).toDouble(), abs(dy).toDouble()))
        val velocity = abs(dy) / elapsed.coerceAtLeast(1L)
        state = when {
            elapsed > t.maxDurationMs -> Result.REJECTED
            angleFromVertical > t.maxAngleDeg -> Result.REJECTED
            velocity < t.minVelocityPxPerMs -> Result.REJECTED
            dy < 0 -> Result.SWIPE_UP // screen y grows downwards
            else -> Result.SWIPE_DOWN
        }
        return state
    }
}
