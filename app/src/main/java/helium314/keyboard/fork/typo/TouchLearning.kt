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
 * - Hands and keys differ between the cover and the inner screen of a foldable, upright and sideways, and in one-handed
 *   mode (left or right hand): each of these ([PROFILES]) learns on its own, the one in use is taken from the current
 *   screen and mode ([current]).
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

    /** what one screen situation learned */
    class Profile {
        val stats = HashMap<Int, KeyStats>()
        val typos = HashMap<String, Int>()
        val shapes = HashMap<Int, KeyShape>()
        /** width / height of the keyboard the shapes were taken from */
        var aspect = 2.2f
    }

    /** cover / inner screen (smallest width 600 dp and more), upright / sideways */
    const val OUTER_PORTRAIT = "outer_p"
    const val OUTER_LANDSCAPE = "outer_l"
    const val INNER_PORTRAIT = "inner_p"
    const val INNER_LANDSCAPE = "inner_l"
    /** one-handed mode, keyboard on the left / right side (whatever the screen) */
    const val ONE_HANDED_LEFT = "one_left"
    const val ONE_HANDED_RIGHT = "one_right"
    val PROFILES = listOf(OUTER_PORTRAIT, OUTER_LANDSCAPE, INNER_PORTRAIT, INNER_LANDSCAPE, ONE_HANDED_LEFT, ONE_HANDED_RIGHT)

    private val profiles = HashMap<String, Profile>()
    private fun profile(name: String) = profiles.getOrPut(name) { Profile() }
    private var appContext: Context? = null
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
        appContext = context.applicationContext
        prefs = p
        enabled = p.getBoolean(PREF_ENABLED, true)
        p.registerOnSharedPreferenceChangeListener(listener)
        load(p)
    }

    /** the profile for the screen as it is now */
    @JvmStatic
    fun current(): String {
        val sv = helium314.keyboard.latin.settings.Settings.getValues()
        if (sv != null && sv.mOneHandedModeEnabled)
            return if (sv.mOneHandedModeGravity == android.view.Gravity.RIGHT) ONE_HANDED_RIGHT else ONE_HANDED_LEFT
        val conf = appContext?.resources?.configuration ?: return OUTER_PORTRAIT
        val inner = conf.smallestScreenWidthDp >= helium314.keyboard.fork.ForkSettings.SPLIT_AUTO_MIN_WIDTH_DP
        val landscape = conf.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return when {
            inner && landscape -> INNER_LANDSCAPE
            inner -> INNER_PORTRAIT
            landscape -> OUTER_LANDSCAPE
            else -> OUTER_PORTRAIT
        }
    }

    private fun isLetter(code: Int) = code > 0 && Character.isLetter(code)

    /** shifted Hangul (ㅃ ㅉ ㄸ ㄲ ㅆ ㅒ ㅖ) is the same key as its base letter */
    private val SHIFTED = mapOf('ㅃ' to 'ㅂ', 'ㅉ' to 'ㅈ', 'ㄸ' to 'ㄷ', 'ㄲ' to 'ㄱ', 'ㅆ' to 'ㅅ', 'ㅒ' to 'ㅐ', 'ㅖ' to 'ㅔ')
        .entries.associate { it.key.code to it.value.code }
    private fun base(code: Int) = SHIFTED[code] ?: code

    /** Hangul compatibility jamo: the statistics screen shows only the Korean keyboard */
    private fun isHangul(code: Int) = code in 0x3131..0x318E

    /** a key was typed at [x], [y] (keyboard coordinates) */
    @JvmStatic
    fun onKeyInput(key: Key, typedCode: Int, x: Int, y: Int, keyboardWidth: Int, keyboardHeight: Int, time: Long) {
        if (!enabled || prefs == null) return
        val code = base(typedCode)
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
        val prof = profile(current())
        remember(prof, key, code, keyboardWidth, keyboardHeight)
        val w = key.width.toFloat().coerceAtLeast(1f)
        val h = key.height.toFloat().coerceAtLeast(1f)
        // a neighbour typed right after deleting a letter: the deleted touch was meant for this key
        if (deletedCode != 0 && deletedCode != code && time - deletedTime < 2000) {
            val cx = key.x + w / 2
            val cy = key.y + h / 2
            if (kotlin.math.abs(deletedX - cx) < w * 1.6f && kotlin.math.abs(deletedY - cy) < h * 1.6f) {
                val pair = "$deletedCode>$code"
                prof.typos[pair] = (prof.typos[pair] ?: 0) + 1
                learn(prof, code, (deletedX - cx) / w, (deletedY - cy) / h)
            }
        }
        deletedCode = 0
        learn(prof, code, (x - key.x - w / 2) / w, (y - key.y - h / 2) / h)
        lastCode = code; lastX = x; lastY = y; lastTime = time
        handler.removeCallbacks(save)
        handler.postDelayed(save, 5000)
    }

    private fun learn(prof: Profile, code: Int, dx: Float, dy: Float) {
        if (kotlin.math.abs(dx) > 1.5f || kotlin.math.abs(dy) > 1.5f) return // not a press on or next to this key
        val s = prof.stats.getOrPut(code) { KeyStats() }
        s.n++
        val weight = 1f / minOf(s.n, WINDOW)
        s.dx += (dx - s.dx) * weight
        s.dy += (dy - s.dy) * weight
        s.points.addLast(floatArrayOf(dx, dy))
        if (s.points.size > KEPT_POINTS) s.points.removeFirst()
    }

    private fun remember(prof: Profile, key: Key, code: Int, keyboardWidth: Int, keyboardHeight: Int) {
        if (keyboardWidth <= 0 || keyboardHeight <= 0) return
        val aspect = keyboardWidth.toFloat() / keyboardHeight
        // the layout changed (split on or off, other size): the key outlines from now on
        if (kotlin.math.abs(aspect - prof.aspect) > 0.05f) prof.shapes.clear()
        if (prof.shapes.containsKey(code)) return
        prof.aspect = aspect
        prof.shapes[code] = KeyShape(key.x.toFloat() / keyboardWidth, key.y.toFloat() / keyboardHeight,
            key.width.toFloat() / keyboardWidth, key.height.toFloat() / keyboardHeight,
            if (isHangul(code)) String(Character.toChars(code)) else key.label ?: String(Character.toChars(code)))
    }

    /**
     * The key for a touch at [x], [y]: [primary] (the one whose outline contains it), unless the touch is near the
     * border to another letter whose learned center is closer.
     */
    @JvmStatic
    fun adjust(primary: Key?, x: Int, y: Int, nearest: List<Key>): Key? {
        if (!enabled || primary == null || !isLetter(primary.code)) return primary
        var best = primary
        val prof = profiles[current()] ?: return primary
        var bestScore = score(prof, primary, x, y) ?: return primary
        for (key in nearest) {
            if (key === primary || !isLetter(key.code)) continue
            val border = BORDER * key.width
            if (key.squaredDistanceToEdge(x, y) > border * border) continue
            val s = score(prof, key, x, y) ?: continue
            if (s < bestScore) {
                best = key
                bestScore = s
            }
        }
        return best
    }

    /** squared distance from the touch to the key's learned center, in key sizes; null while it has too few samples */
    private fun score(prof: Profile, key: Key, x: Int, y: Int): Float? {
        val s = prof.stats[base(key.code)]?.takeIf { it.n >= MIN_SAMPLES } ?: return null
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
    fun typoRanking(context: Context, name: String): List<Triple<String, String, Int>> {
        init(context)
        val prof = profile(name)
        val merged = HashMap<Pair<Int, Int>, Int>()
        for ((pair, count) in prof.typos) {
            val (from, to) = pair.split('>').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: continue
            if (!isHangul(from) || !isHangul(to)) continue
            val key = base(from) to base(to)
            if (key.first == key.second) continue
            merged[key] = (merged[key] ?: 0) + count
        }
        return merged.entries.sortedByDescending { it.value }.map { (pair, count) ->
            Triple(label(prof, pair.first), label(prof, pair.second), count)
        }
    }

    /** the Korean keys only, shifted letters folded into their base key */
    fun keys(context: Context, name: String): Map<Int, KeyShape> {
        init(context)
        val result = HashMap<Int, KeyShape>()
        for ((code, shape) in profile(name).shapes) {
            if (!isHangul(code)) continue
            val b = base(code)
            if (b == code || !result.containsKey(b))
                result[b] = KeyShape(shape.x, shape.y, shape.w, shape.h, String(Character.toChars(b)))
        }
        return result
    }

    fun points(name: String, code: Int): List<FloatArray> {
        val stats = profile(name).stats
        return stats[code]?.points?.toList().orEmpty() + SHIFTED.filterValues { it == code }.keys.flatMap { stats[it]?.points?.toList().orEmpty() }
    }
    fun aspect(name: String) = profile(name).aspect
    /** letters typed in the profile, to show which have data */
    fun sampleCount(context: Context, name: String): Int { init(context); return profile(name).stats.values.sumOf { it.n } }

    private fun label(prof: Profile, code: Int) = prof.shapes[code]?.label ?: String(Character.toChars(code))

    /** forgets what [name] learned, or all profiles when null */
    fun reset(context: Context, name: String? = null) {
        init(context)
        if (name == null) profiles.clear() else profiles.remove(name)
        persist()
    }

    // ---- storage

    private fun persist() {
        val p = prefs ?: return
        val all = JSONObject()
        for ((name, prof) in profiles) {
            val keys = JSONObject()
            for ((code, s) in prof.stats) {
                val pts = JSONArray()
                s.points.forEach { pts.put(it[0].toDouble()).put(it[1].toDouble()) }
                keys.put(code.toString(), JSONObject().put("n", s.n).put("dx", s.dx.toDouble()).put("dy", s.dy.toDouble()).put("p", pts))
            }
            val shapeJson = JSONObject()
            for ((code, k) in prof.shapes) shapeJson.put(code.toString(),
                JSONArray().put(k.x.toDouble()).put(k.y.toDouble()).put(k.w.toDouble()).put(k.h.toDouble()).put(k.label))
            all.put(name, JSONObject().put("aspect", prof.aspect.toDouble()).put("keys", keys)
                .put("typos", JSONObject(prof.typos as Map<*, *>)).put("shapes", shapeJson))
        }
        p.edit { putString(PREF_DATA, JSONObject().put("profiles", all).toString()) }
    }

    private fun load(p: SharedPreferences) {
        val json = runCatching { JSONObject(p.getString(PREF_DATA, null) ?: return) }.getOrNull() ?: return
        val all = json.optJSONObject("profiles")
        if (all == null) {
            // data from before the profiles: it was learned on the cover screen, upright (the usual case)
            loadProfile(profile(OUTER_PORTRAIT), json)
            return
        }
        for (name in all.keys()) all.optJSONObject(name)?.let { loadProfile(profile(name), it) }
    }

    private fun loadProfile(prof: Profile, json: JSONObject) {
        json.optJSONObject("keys")?.let { keys ->
            for (codeStr in keys.keys()) {
                val o = keys.optJSONObject(codeStr) ?: continue
                val s = KeyStats(o.optInt("n"), o.optDouble("dx").toFloat(), o.optDouble("dy").toFloat())
                val pts = o.optJSONArray("p")
                if (pts != null) for (i in 0 until pts.length() / 2)
                    s.points.addLast(floatArrayOf(pts.optDouble(2 * i).toFloat(), pts.optDouble(2 * i + 1).toFloat()))
                codeStr.toIntOrNull()?.let { prof.stats[it] = s }
            }
        }
        prof.aspect = json.optDouble("aspect", 2.2).toFloat()
        json.optJSONObject("typos")?.let { t -> for (k in t.keys()) prof.typos[k] = t.optInt(k) }
        json.optJSONObject("shapes")?.let { sh ->
            for (codeStr in sh.keys()) {
                val a = sh.optJSONArray(codeStr) ?: continue
                val code = codeStr.toIntOrNull() ?: continue
                prof.shapes[code] = KeyShape(a.optDouble(0).toFloat(), a.optDouble(1).toFloat(), a.optDouble(2).toFloat(),
                    a.optDouble(3).toFloat(), a.optString(4))
            }
        }
    }
}
