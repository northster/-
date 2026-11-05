// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * fork: the "pull" way of opening and closing the toolbar ([PREF] = [PULL]). Opening: the key rows rise by the
 * toolbar's height, following the finger while it stays down, then spring back down when it lets go, the toolbar
 * showing in the room they leave above them. Closing: the toolbar slides away as usual and the rows dip down a little
 * and bounce back. Every row is a spring of its own, the top rows a little quicker than the bottom ones, so the keyboard
 * stretches and settles like something soft instead of moving as one block.
 *
 * Offsets are in px, positive = up. The keyboard view moves each key by [rowOffset] of its row while drawing.
 */
object ToolbarMotion {
    const val PREF = "fork_toolbar_motion"
    const val SLIDE = "slide"
    const val PULL = "pull"

    fun isPull(prefs: SharedPreferences) = prefs.getString(PREF, SLIDE) == PULL

    private enum class Phase { NONE, PULL, RETURN, BOUNCE }
    private var phase = Phase.NONE

    /** the y of each row's keys, ascending (top row first) */
    private var rowYs = IntArray(0)
    /** each row's place in the keyboard, 0 = top row, 1 = bottom row */
    private var rowFrac = FloatArray(0)
    private var pos = FloatArray(0)
    private var vel = FloatArray(0)
    /** closing: when each row gets its push down (s from the start) */
    private var kickAt = FloatArray(0)
    private var kicked = BooleanArray(0)
    private var target = 0f
    private var height = 0f
    private var fingerDown = false
    private var releasedFor = 0f
    private var elapsed = 0f
    private var density = 1f

    val isActive get() = phase != Phase.NONE
    /** the finger still pulls the rows: its moves go to [drag] */
    @JvmStatic val isPulling get() = phase == Phase.PULL && fingerDown
    /** opening: how far the top row is still raised, the toolbar shows in the room above it */
    val opening get() = phase == Phase.PULL || phase == Phase.RETURN
    /** the rows come back down: the toolbar shows above the top row (while they rise it stays hidden) */
    val revealing get() = phase == Phase.RETURN
    val topRaised get() = pos.firstOrNull() ?: 0f
    /** how far the keyboard background has to reach above the view, for the highest row */
    @JvmStatic val maxRaised get() = if (phase == Phase.NONE) 0f else pos.maxOrNull()?.coerceAtLeast(0f) ?: 0f

    /** how far the key at [keyY] is moved, for drawing (negative = up, as canvas y) */
    @JvmStatic
    fun rowOffset(keyY: Int): Float {
        if (phase == Phase.NONE || rowYs.isEmpty()) return 0f
        var i = java.util.Arrays.binarySearch(rowYs, keyY)
        if (i < 0) i = (-i - 2).coerceIn(0, rowYs.size - 1)
        return -pos[i]
    }

    private fun setRows(keyYs: Collection<Int>) {
        rowYs = keyYs.distinct().sorted().toIntArray()
        val n = rowYs.size
        rowFrac = FloatArray(n) { if (n <= 1) 0f else it / (n - 1f) }
        pos = FloatArray(n)
        vel = FloatArray(n)
        kickAt = FloatArray(n)
        kicked = BooleanArray(n) { true }
    }

    /** the swipe up was recognized: the rows start rising to [toolbarHeight] */
    fun startPull(keyYs: Collection<Int>, toolbarHeight: Float, density: Float) {
        if (phase == Phase.NONE || rowYs.size != keyYs.distinct().size) setRows(keyYs)
        this.density = density
        height = toolbarHeight
        target = toolbarHeight
        fingerDown = true
        releasedFor = 0f
        elapsed = 0f
        phase = Phase.PULL
    }

    /** the finger moved [dy] px up since the swipe was recognized */
    @JvmStatic
    fun drag(dy: Float) {
        if (phase != Phase.PULL) return
        // at least the toolbar's height (the swipe is already some way up), past it only a little (rubber band)
        val over = (dy - height * 0.4f).coerceAtLeast(0f)
        target = height + over * 0.25f
        target = target.coerceAtMost(height * 1.35f)
    }

    /** the finger went up: the rows finish rising, then fall back */
    @JvmStatic
    fun release() {
        if (phase != Phase.PULL) return
        fingerDown = false
        target = height
    }

    /** closing: the rows dip by about [dipDp] and bounce back, one after the other from the top */
    fun startBounce(keyYs: Collection<Int>, density: Float, dipDp: Float = 10f) {
        setRows(keyYs)
        this.density = density
        elapsed = 0f
        for (i in rowYs.indices) {
            kickAt[i] = 0.04f + rowFrac[i] * 0.06f
            kicked[i] = false
        }
        target = 0f
        dipPx = dipDp * density
        phase = Phase.BOUNCE
    }
    private var dipPx = 0f

    fun stop() {
        phase = Phase.NONE
        pos.fill(0f)
        vel.fill(0f)
    }

    /** advances the springs by [dt] s; false once everything is back in place */
    fun step(dt: Float): Boolean {
        if (phase == Phase.NONE) return false
        val h = dt.coerceIn(0f, 0.05f)
        elapsed += h
        if (phase == Phase.PULL && !fingerDown) {
            releasedFor += h
            // up far enough (or long enough): back down
            if (pos.firstOrNull() ?: 0f >= height * 0.85f || releasedFor > 0.25f) {
                phase = Phase.RETURN
                target = 0f
            }
        }
        var moving = false
        // small steps keep the springs stable at any frame rate
        val sub = 4
        val s = h / sub
        for (i in pos.indices) {
            val f = rowFrac[i]
            val stiffness: Float
            val damping: Float
            when (phase) {
                Phase.PULL -> { stiffness = 700f * (1.25f - 0.5f * f); damping = 0.82f }
                Phase.RETURN -> { stiffness = 420f * (1.2f - 0.4f * f); damping = 0.62f }
                else -> { stiffness = 620f * (1.15f - 0.3f * f); damping = 0.42f }
            }
            if (phase == Phase.BOUNCE && !kicked[i] && elapsed >= kickAt[i]) {
                kicked[i] = true
                // a push down: with these springs the dip is about dipPx
                val w = sqrt(stiffness)
                vel[i] = -dipPx * w / PEAK_FACTOR
            }
            val c = 2f * damping * sqrt(stiffness)
            repeat(sub) {
                val a = -stiffness * (pos[i] - target) - c * vel[i]
                vel[i] += a * s
                pos[i] += vel[i] * s
            }
            if (abs(pos[i] - target) > 0.5f || abs(vel[i]) > 6f || (phase == Phase.BOUNCE && !kicked[i])) moving = true
        }
        if (phase == Phase.PULL) return true
        if (!moving) {
            stop()
            return false
        }
        return true
    }

    /** peak of an underdamped spring pushed from rest, as a share of v / w (about, for damping ~0.4) */
    private val PEAK_FACTOR = exp(-0.45f)
}
