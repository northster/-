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
 * fork: useless but true trivia for the toolbar ("A day on Venus is longer than its year."). Gemini writes a batch of
 * short facts; each time the toolbar opens one is shown, those not seen yet first. Once only a few are left unseen (20 of
 * a batch of 25 seen) the next batch is fetched. A few hundred are kept, a new fact that is already kept is dropped, and
 * the latest ones are sent along so Gemini writes others.
 */
object Trivia {
    private const val PREF_FACTS = "fork_trivia_facts"
    private const val PREF_FETCHED = "fork_trivia_fetched"
    private const val TAG = "Trivia"
    private const val BATCH = 25
    /** a new batch once this few are unseen */
    private const val LOW = 5
    private const val KEEP = 400
    /** facts sent along as "not these" */
    private const val AVOID = 120
    /** after a failed fetch, wait this long before trying again on its own */
    private const val RETRY_MS = 15 * 60_000L
    private const val PREF_FAILED = "fork_trivia_failed"
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
        facts.takeLast(KEEP + BATCH).forEach { arr.put(JSONObject().put("t", it.text).put("s", it.seen)) }
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

    /** same fact, written a bit differently */
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** a new batch once few are unseen (or [force]); [onDone] on the main thread */
    fun refreshIfDue(context: Context, force: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        val prefs = context.prefs()
        if (fetching || SlateKeys.keys(prefs).isEmpty()) return
        val unseen = read(prefs).count { !it.seen }
        if (!force && unseen > LOW) return
        if (!force && System.currentTimeMillis() - prefs.getLong(PREF_FAILED, 0) < RETRY_MS) return
        fetching = true
        val avoid = read(prefs).takeLast(AVOID).joinToString("\n") { it.text }
        val app = context.applicationContext
        val korean = java.util.Locale.getDefault().language == "ko"
        Thread {
            val prompt = "You write short, true and delightfully useless trivia facts. Every fact must be correct. " +
                "Output only the facts, one per line, no numbering, no bullets, no quotes."
            val request = (if (korean)
                "쓸데없지만 사실인 잡학 상식을 ${BATCH}개 써 줘. 한 줄에 하나, 각각 ${MAX_CHARS - 5}자 이내의 짧은 한 문장. " +
                    "동물, 우주, 역사, 음식, 사람 몸, 언어, 일상 사물 등 주제를 섞고 서로 겹치지 않게."
            else "Write $BATCH useless but true trivia facts, one per line, each one short sentence under ${MAX_CHARS - 5} " +
                "characters. Mix topics: animals, space, history, food, the human body, language, everyday things.") +
                (if (avoid.isEmpty()) "" else if (korean) "\n\n아래 지식과 같거나 비슷한 건 쓰지 마:\n$avoid"
                    else "\n\nDo not repeat these or anything close to them:\n$avoid")
            val outcome = GeminiClient.run(app.prefs(), prompt, request, search = false, raw = true)
            val added = if (outcome is GeminiClient.Outcome.Success) {
                val known = read(app.prefs())
                val seenKeys = known.mapTo(HashSet()) { norm(it.text) }
                val fresh = outcome.text.lines()
                    .map { it.trim().trimStart('-', '*', '•', '·').replace(Regex("^\\d+[.)]\\s*"), "").trim().trim('"') }
                    .filter { it.length in 6..(MAX_CHARS + 10) }
                    .filter { seenKeys.add(norm(it)) } // not kept yet, and not twice in this batch
                // the oldest seen ones go first when there are too many
                val all = known + fresh.map { Fact(it, false) }
                var over = all.size - KEEP
                val kept = all.filter { fact ->
                    val drop = over > 0 && fact.seen
                    if (drop) over--
                    !drop
                }
                write(app.prefs(), kept)
                app.prefs().edit { putLong(PREF_FETCHED, System.currentTimeMillis()); remove(PREF_FAILED) }
                fresh.isNotEmpty()
            } else {
                Log.w(TAG, "no trivia: ${(outcome as GeminiClient.Outcome.Failure).message}")
                app.prefs().edit { putLong(PREF_FAILED, System.currentTimeMillis()) }
                false
            }
            fetching = false
            handler.post { onDone(added) }
        }.start()
    }
}
