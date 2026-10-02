// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.fork.slate.GeminiClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * fork: what the user likes to hear trivia about, written freely ("고양이", "로마 제국 역사", "F1"). [share] of each
 * batch is about them: Wikipedia is searched with English keywords Gemini makes once per
 * interest (kept with it).
 */
object TriviaInterests {
    const val PREF = "fork_trivia_interests"
    /** share of a batch about the interests, 0..1 */
    const val PREF_SHARE = "fork_trivia_interest_share"
    const val DEFAULT_SHARE = 0.4f

    class Interest(val text: String, val keywords: List<String>)

    fun all(prefs: SharedPreferences): List<Interest> {
        val arr = runCatching { JSONArray(prefs.getString(PREF, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("t").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val k = o.optJSONArray("k")
            Interest(text, (0 until (k?.length() ?: 0)).mapNotNull { k?.optString(it)?.trim()?.takeIf { s -> s.isNotEmpty() } })
        }
    }

    private fun save(prefs: SharedPreferences, list: List<Interest>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.text).put("k", JSONArray(it.keywords))) }
        prefs.edit { putString(PREF, arr.toString()) }
    }

    fun add(prefs: SharedPreferences, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val list = all(prefs)
        if (list.any { it.text.equals(t, true) }) return
        save(prefs, list + Interest(t, emptyList()))
    }

    fun remove(prefs: SharedPreferences, text: String) = save(prefs, all(prefs).filter { it.text != text })

    fun share(prefs: SharedPreferences) = prefs.getFloat(PREF_SHARE, DEFAULT_SHARE).coerceIn(0f, 1f)

    /**
     * The interests with their English search keywords; the ones still without keywords get them now (one Gemini call
     * for all of them, blocking). Interests whose keywords could not be made are left out this time.
     */
    fun withKeywords(prefs: SharedPreferences): List<Interest> {
        val list = all(prefs)
        val missing = list.filter { it.keywords.isEmpty() }
        if (missing.isEmpty()) return list
        val prompt = "For each numbered interest, give 3 to 6 English search words or short phrases that would appear " +
            "literally in Wikipedia sentences about it (names, nouns; no generic words like \"history\" alone). " +
            "Output one line per interest: \"<number>: word, word, word\". Nothing else."
        val request = missing.mapIndexed { i, it -> "${i + 1}: ${it.text}" }.joinToString("\n")
        val outcome = GeminiClient.run(prefs, prompt, request, search = false, raw = true)
        val made = HashMap<String, List<String>>()
        if (outcome is GeminiClient.Outcome.Success) {
            for (line in outcome.text.lines()) {
                val m = Regex("^\\s*(\\d+)\\s*[:.)]\\s*(.+)$").find(line) ?: continue
                val interest = missing.getOrNull(m.groupValues[1].toInt() - 1) ?: continue
                made[interest.text] = m.groupValues[2].split(',').map { it.trim().trim('"') }
                    .filter { it.length in 2..40 }.take(6)
            }
        }
        // keywords the user wrote in English work as they are
        val updated = list.map { i ->
            if (i.keywords.isNotEmpty()) i
            else Interest(i.text, made[i.text] ?: listOfNotNull(i.text.takeIf { t -> t.all { it.code < 128 } }))
        }
        // re-read: the list may have changed in the settings meanwhile
        val current = all(prefs).map { c -> updated.firstOrNull { it.text == c.text && c.keywords.isEmpty() } ?: c }
        save(prefs, current)
        return updated.filter { it.keywords.isNotEmpty() }
    }
}
