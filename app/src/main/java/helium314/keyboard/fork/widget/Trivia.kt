// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import helium314.keyboard.fork.slate.GeminiClient
import helium314.keyboard.fork.slate.SlateKeys
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * fork: useless but true trivia for the toolbar ("A day on Venus is longer than its year."). Every few hours Gemini
 * writes a batch of short facts; each time the toolbar opens one is shown, those not seen yet first.
 */
object Trivia {
    private const val PREF_FACTS = "fork_trivia_facts"
    private const val PREF_FETCHED = "fork_trivia_fetched"
    private const val TAG = "Trivia"
    private const val BATCH = 25
    private const val KEEP = 150
    /** characters a fact may have, so it fits the widget in two lines */
    const val MAX_CHARS = 40

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var fetching = false

    private class Fact(val text: String, var seen: Boolean)

    private fun read(prefs: SharedPreferences): MutableList<Fact> {
        val arr = runCatching { JSONArray(prefs.getString(PREF_FACTS, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o -> o.optString("t").takeIf { it.isNotBlank() }?.let { Fact(it, o.optBoolean("s")) } }
        }.toMutableList()
    }

    private fun write(prefs: SharedPreferences, facts: List<Fact>) {
        val arr = JSONArray()
        facts.takeLast(KEEP).forEach { arr.put(JSONObject().put("t", it.text).put("s", it.seen)) }
        prefs.edit { putString(PREF_FACTS, arr.toString()) }
    }

    /** a fact to show now, not seen before if there is one (it counts as seen from now on); null if there are none */
    fun next(context: Context): String? {
        val prefs = context.prefs()
        val facts = read(prefs)
        if (facts.isEmpty()) return null
        val fact = facts.filter { !it.seen }.randomOrNull() ?: facts.random()
        fact.seen = true
        write(prefs, facts)
        return fact.text
    }

    fun count(context: Context) = read(context.prefs()).size

    /** a new batch if the last one is older than the setting (or [force]); [onDone] on the main thread */
    fun refreshIfDue(context: Context, force: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        val prefs = context.prefs()
        if (fetching || SlateKeys.keys(prefs).isEmpty()) return
        val hours = prefs.getFloat(WidgetPrefs.TRIVIA_HOURS, WidgetPrefs.DEFAULT_TRIVIA_HOURS).coerceIn(1f, 72f)
        val unseen = read(prefs).count { !it.seen }
        if (!force && unseen > 3 && System.currentTimeMillis() - prefs.getLong(PREF_FETCHED, 0) < hours * 3_600_000) return
        fetching = true
        val app = context.applicationContext
        val korean = java.util.Locale.getDefault().language == "ko"
        Thread {
            val prompt = "You write short, true and delightfully useless trivia facts. Every fact must be correct. " +
                "Output only the facts, one per line, no numbering, no bullets, no quotes."
            val request = if (korean)
                "쓸데없지만 사실인 잡학 상식을 ${BATCH}개 써 줘. 한 줄에 하나, 각각 ${MAX_CHARS - 5}자 이내의 짧은 한 문장. " +
                    "동물, 우주, 역사, 음식, 사람 몸, 언어, 일상 사물 등 주제를 섞고 서로 겹치지 않게."
            else "Write $BATCH useless but true trivia facts, one per line, each one short sentence under ${MAX_CHARS - 5} " +
                "characters. Mix topics: animals, space, history, food, the human body, language, everyday things."
            val outcome = GeminiClient.run(app.prefs(), prompt, request, search = false, raw = true)
            val added = if (outcome is GeminiClient.Outcome.Success) {
                val known = read(app.prefs())
                val fresh = outcome.text.lines()
                    .map { it.trim().trimStart('-', '*', '•', '·').replace(Regex("^\\d+[.)]\\s*"), "").trim().trim('"') }
                    .filter { it.length in 6..(MAX_CHARS + 10) }
                    .filter { f -> known.none { it.text == f } }
                write(app.prefs(), known + fresh.map { Fact(it, false) })
                app.prefs().edit { putLong(PREF_FETCHED, System.currentTimeMillis()) }
                fresh.isNotEmpty()
            } else {
                Log.w(TAG, "no trivia: ${(outcome as GeminiClient.Outcome.Failure).message}")
                false
            }
            fetching = false
            handler.post { onDone(added) }
        }.start()
    }
}
