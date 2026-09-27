// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.typo

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * fork: fewer typos by learning where the fingers really land, like Samsung's keyboard.
 *
 * - Every letter typed adds its touch point, relative to the key, to that key's running average. A key's learned
 *   center is where it is actually pressed.
 * - A letter, deleted right away and replaced by a neighbouring letter, is a typo: it is counted (for the statistics
 *   screen), and its touch point is learned for the key that was meant.
 * - Near the border between two letters, the key whose learned center is closer to the touch wins ([adjust]), instead
 *   of the one whose drawn outline contains it. Only letters, only neighbours, and only once a key has enough samples.
 */
object TouchLearning {
    const val PREF_ENABLED = "fork_touch_learning"
    const val PREF_DATA = "fork_touch_data"
    /** samples a key needs before its learned center is used */
    private const val MIN_SAMPLES = 15
    /** the average follows the last ~this many presses */
    private const val WINDOW = 60
    /** touch points kept per key for the statistics screen */
    private const val KEPT_POINTS = 40
    /** a touch this close (key widths) to another letter's outline lets the learned centers decide */
    private const val BORDER = 0.4f

    class KeyStats(var n: Int = 0, var dx: Float = 0f, var dy: Float = 0f, val points: ArrayDeque<FloatArray> = ArrayDeque())

    /** key outline on the keyboard as fractions of its size (for the statistics screen), and its label */
    class KeyShape(val x: Float, val y: Float, val w: Float, val h: Float, val label: String)

    private val stats = HashMap<Int, KeyStats>()
    private val typos = HashMap<String, Int>()
    private val shapes = HashMap<Int, KeyShape>()
    /** width / height of the keyboard the shapes were taken from */
    var aspect = 2.2f
        private set
    private var prefs: SharedPreferences? = null
    @Volatile private var enabled = true
    private val handler = Handler(Looper.getMainLooper())
    private val save = Runnable { persist() }

    // the last letter typed, and one that was deleted right after typing it
    private var lastCode = 0
    private var lastX = 0
    private var lastY = 0
    private var lastTime = 0L
    private var deletedCode = 0
    private var deletedX = 0
    private var deletedY = 0
    private var deletedTime = 0L

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == PREF_ENABLED) enabled = p.getBoolean(PREF_ENABLED, true)
    }

    @JvmStatic
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.prefs()
        prefs = p
        enabled = p.getBoolean(PREF_ENABLED, true)
        p.registerOnSharedPreferenceChangeListener(listener)
        load(p)
    }

    private fun isLetter(code: Int) = code > 0 && Character.isLetter(code)

    /** a key was typed at [x], [y] (keyboard coordinates) */
    @JvmStatic
    fun onKeyInput(key: Key, code: Int, x: Int, y: Int, keyboardWidth: Int, keyboardHeight: Int, time: Long) {
        if (!enabled || prefs == null) return
        if (code == KeyCode.DELETE) {
            if (lastCode != 0 && time - lastTime < 1500) {
                deletedCode = lastCode; deletedX = lastX; deletedY = lastY; deletedTime = time
            } else deletedCode = 0
            lastCode = 0
            return
        }
        if (!isLetter(code)) {
            lastCode = 0
            deletedCode = 0
            return
        }
        remember(key, code, keyboardWidth, keyboardHeight)
        val w = key.width.toFloat().coerceAtLeast(1f)
        val h = key.height.toFloat().coerceAtLeast(1f)
        // a neighbour typed right after deleting a letter: the deleted touch was meant for this key
        if (deletedCode != 0 && deletedCode != code && time - deletedTime < 2000) {
            val cx = key.x + w / 2
            val cy = key.y + h / 2
            if (kotlin.math.abs(deletedX - cx) < w * 1.6f && kotlin.math.abs(deletedY - cy) < h * 1.6f) {
                val pair = "$deletedCode>$code"
                typos[pair] = (typos[pair] ?: 0) + 1
                learn(code, (deletedX - cx) / w, (deletedY - cy) / h)
            }
        }
        deletedCode = 0
        learn(code, (x - key.x - w / 2) / w, (y - key.y - h / 2) / h)
        lastCode = code; lastX = x; lastY = y; lastTime = time
        handler.removeCallbacks(save)
        handler.postDelayed(save, 5000)
    }

    private fun learn(code: Int, dx: Float, dy: Float) {
        if (kotlin.math.abs(dx) > 1.5f || kotlin.math.abs(dy) > 1.5f) return // not a press on or next to this key
        val s = stats.getOrPut(code) { KeyStats() }
        s.n++
        val weight = 1f / minOf(s.n, WINDOW)
        s.dx += (dx - s.dx) * weight
        s.dy += (dy - s.dy) * weight
        s.points.addLast(floatArrayOf(dx, dy))
        if (s.points.size > KEPT_POINTS) s.points.removeFirst()
    }

    private fun remember(key: Key, code: Int, keyboardWidth: Int, keyboardHeight: Int) {
        if (keyboardWidth <= 0 || keyboardHeight <= 0 || shapes.containsKey(code)) return
        aspect = keyboardWidth.toFloat() / keyboardHeight
        shapes[code] = KeyShape(key.x.toFloat() / keyboardWidth, key.y.toFloat() / keyboardHeight,
            key.width.toFloat() / keyboardWidth, key.height.toFloat() / keyboardHeight,
            key.label ?: String(Character.toChars(code)))
    }

    /**
     * The key for a touch at [x], [y]: [primary] (the one whose outline contains it), unless the touch is near the
     * border to another letter whose learned center is closer.
     */
    @JvmStatic
    fun adjust(primary: Key?, x: Int, y: Int, nearest: List<Key>): Key? {
        if (!enabled || primary == null || !isLetter(primary.code)) return primary
        var best = primary
        var bestScore = score(primary, x, y) ?: return primary
        for (key in nearest) {
            if (key === primary || !isLetter(key.code)) continue
            val border = BORDER * key.width
            if (key.squaredDistanceToEdge(x, y) > border * border) continue
            val s = score(key, x, y) ?: continue
            if (s < bestScore) {
                best = key
                bestScore = s
            }
        }
        return best
    }

    /** squared distance from the touch to the key's learned center, in key sizes; null while it has too few samples */
    private fun score(key: Key, x: Int, y: Int): Float? {
        val s = stats[key.code]?.takeIf { it.n >= MIN_SAMPLES } ?: return null
        val w = key.width.toFloat().coerceAtLeast(1f)
        val h = key.height.toFloat().coerceAtLeast(1f)
        val cx = key.x + w / 2 + s.dx * w
        val cy = key.y + h / 2 + s.dy * h
        val nx = (x - cx) / w
        val ny = (y - cy) / h
        return nx * nx + ny * ny
    }

    // ---- statistics screen

    /** typo pairs (typed, meant) with how often, most frequent first */
    fun typoRanking(context: Context): List<Triple<String, String, Int>> {
        init(context)
        return typos.entries.sortedByDescending { it.value }.mapNotNull { (pair, count) ->
            val (from, to) = pair.split('>').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: return@mapNotNull null
            Triple(label(from), label(to), count)
        }
    }

    fun keys(context: Context): Map<Int, KeyShape> { init(context); return shapes.toMap() }
    fun points(code: Int): List<FloatArray> = stats[code]?.points?.toList().orEmpty()
    fun samples(code: Int): Int = stats[code]?.n ?: 0

    private fun label(code: Int) = shapes[code]?.label ?: String(Character.toChars(code))

    fun reset(context: Context) {
        init(context)
        stats.clear(); typos.clear(); shapes.clear()
        context.prefs().edit { remove(PREF_DATA) }
    }

    // ---- storage

    private fun persist() {
        val p = prefs ?: return
        val keys = JSONObject()
        for ((code, s) in stats) {
            val pts = JSONArray()
            s.points.forEach { pts.put(it[0].toDouble()).put(it[1].toDouble()) }
            keys.put(code.toString(), JSONObject().put("n", s.n).put("dx", s.dx.toDouble()).put("dy", s.dy.toDouble()).put("p", pts))
        }
        val shapeJson = JSONObject()
        for ((code, k) in shapes) shapeJson.put(code.toString(),
            JSONArray().put(k.x.toDouble()).put(k.y.toDouble()).put(k.w.toDouble()).put(k.h.toDouble()).put(k.label))
        val json = JSONObject().put("aspect", aspect.toDouble()).put("keys", keys).put("typos", JSONObject(typos as Map<*, *>)).put("shapes", shapeJson)
        p.edit { putString(PREF_DATA, json.toString()) }
    }

    private fun load(p: SharedPreferences) {
        val json = runCatching { JSONObject(p.getString(PREF_DATA, null) ?: return) }.getOrNull() ?: return
        json.optJSONObject("keys")?.let { keys ->
            for (codeStr in keys.keys()) {
                val o = keys.optJSONObject(codeStr) ?: continue
                val s = KeyStats(o.optInt("n"), o.optDouble("dx").toFloat(), o.optDouble("dy").toFloat())
                val pts = o.optJSONArray("p")
                if (pts != null) for (i in 0 until pts.length() / 2)
                    s.points.addLast(floatArrayOf(pts.optDouble(2 * i).toFloat(), pts.optDouble(2 * i + 1).toFloat()))
                codeStr.toIntOrNull()?.let { stats[it] = s }
            }
        }
        aspect = json.optDouble("aspect", 2.2).toFloat()
        json.optJSONObject("typos")?.let { t -> for (k in t.keys()) typos[k] = t.optInt(k) }
        json.optJSONObject("shapes")?.let { sh ->
            for (codeStr in sh.keys()) {
                val a = sh.optJSONArray(codeStr) ?: continue
                val code = codeStr.toIntOrNull() ?: continue
                shapes[code] = KeyShape(a.optDouble(0).toFloat(), a.optDouble(1).toFloat(), a.optDouble(2).toFloat(),
                    a.optDouble(3).toFloat(), a.optString(4))
            }
        }
    }
}
