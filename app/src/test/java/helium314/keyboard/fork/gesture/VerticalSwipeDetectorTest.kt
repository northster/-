// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.gesture

import helium314.keyboard.fork.gesture.VerticalSwipeDetector.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalSwipeDetectorTest {
    // density 1 -> px == dp
    private val t = SwipeThresholds.fromDp(density = 1f)

    private fun run(vararg points: Triple<Float, Float, Long>): Result {
        val d = VerticalSwipeDetector()
        d.onDown(100f, 100f, 0L, t)
        var r = Result.UNDECIDED
        for ((x, y, time) in points) r = d.onMove(x, y, time)
        return r
    }

    @Test fun tapWithJitterStaysUndecided() {
        assertEquals(Result.UNDECIDED, run(Triple(103f, 96f, 40L), Triple(101f, 98f, 80L)))
    }

    @Test fun fastUpwardFlickIsSwipeUp() {
        assertEquals(Result.SWIPE_UP, run(Triple(102f, 80f, 30L), Triple(104f, 50f, 80L)))
    }

    @Test fun fastDownwardFlickIsSwipeDown() {
        assertEquals(Result.SWIPE_DOWN, run(Triple(98f, 130f, 50L), Triple(97f, 150f, 90L)))
    }

    @Test fun slowVerticalDragIsRejected() {
        // 50 px in 300 ms = 167 px/s < 300
        assertEquals(Result.REJECTED, run(Triple(100f, 80f, 150L), Triple(100f, 50f, 300L)))
    }

    @Test fun diagonalMoveIsRejected() {
        // ~34 degrees from vertical: fast and long enough, but not vertical enough
        assertEquals(Result.REJECTED, run(Triple(115f, 80f, 30L), Triple(130f, 55f, 60L)))
    }

    @Test fun horizontalSlideIsRejectedAndSticky() {
        val d = VerticalSwipeDetector()
        d.onDown(100f, 100f, 0L, t)
        assertEquals(Result.REJECTED, d.onMove(130f, 100f, 40L))
        // a later fast vertical move must not turn it into a swipe
        assertEquals(Result.REJECTED, d.onMove(130f, 40f, 60L))
    }

    @Test fun holdThenFlickIsRejected() {
        // finger rests longer than maxDuration (e.g. long press), then moves
        assertEquals(Result.REJECTED, run(Triple(100f, 100f, 400L), Triple(100f, 40f, 420L)))
    }

    @Test fun abortStopsTracking() {
        val d = VerticalSwipeDetector()
        d.onDown(100f, 100f, 0L, t)
        d.abort()
        assertEquals(Result.REJECTED, d.onMove(100f, 20f, 50L))
    }
}
