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
 * fork: useless but true trivia for the toolbar ("A day on Venus is longer than its year."). A batch of short facts
 * (see [fetchBatch]: from Wikipedia's "Did you know", Gemini's own only as an experiment); each time the toolbar opens one
 * is shown, those not seen yet first. Once only a few are left unseen (20 of a batch of 25 seen) the next batch is
 * fetched. A few hundred are kept, a new fact that is already kept (also in other words) is dropped.
 */
object Trivia {
    private const val PREF_FACTS = "fork_trivia_facts"
    private const val PREF_FETCHED = "fork_trivia_fetched"
    private const val TAG = "Trivia"
    private const val BATCH = 25
    /** a new batch once this few are unseen */
    private const val LOW = 5
    private const val KEEP = 400
    /** facts sent along as "not these" (topics change every batch, so the latest few are enough) */
    private const val AVOID = 40
    /** after a failed fetch, wait this long before trying again on its own */
    private const val RETRY_MS = 15 * 60_000L
    private const val PREF_FAILED = "fork_trivia_failed"
    /** keys of the Wikipedia hooks taken so far, so none comes twice (about 9 characters each) */
    private const val PREF_WIKI_SEEN = "fork_trivia_wiki_seen"
    private const val WIKI_SEEN_MAX = 30_000
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
            val fresh = fetchBatch(app, korean, avoid)
            val added = if (fresh != null) {
                val known = read(app.prefs())
                val keys = known.mapTo(ArrayList()) { norm(it.text) }
                val kept = fresh.map { cleanLine(it) }
                    .filter { it.length in 6..(MAX_CHARS + 10) }
                    .filter { f ->
                        // not kept yet (also not written a bit differently), and not twice in this batch
                        val k = norm(f)
                        if (keys.any { it == k || similar(it, k) }) false else { keys.add(k); true }
                    }
                // the oldest seen ones go first when there are too many
                val all = known + kept.map { Fact(it, false) }
                var over = all.size - KEEP
                write(app.prefs(), all.filter { fact ->
                    val drop = over > 0 && fact.seen
                    if (drop) over--
                    !drop
                })
                app.prefs().edit { putLong(PREF_FETCHED, System.currentTimeMillis()); remove(PREF_FAILED) }
                kept.isNotEmpty()
            } else {
                app.prefs().edit { putLong(PREF_FAILED, System.currentTimeMillis()) }
                false
            }
            fetching = false
            handler.post { onDone(added) }
        }.start()
    }

    private val TOPICS = listOf("animals", "insects", "the ocean", "space", "planets", "the human body", "food", "drinks",
        "plants", "history", "ancient times", "language and words", "everyday objects", "inventions", "sports",
        "music", "geography", "countries", "weather", "chemistry", "physics", "money", "art", "birds", "dinosaurs")

    /**
     * About [BATCH] new facts, null if Gemini could not be reached.
     *  - normally all from Wikipedia's "Did you know" (checked by people), each hook taken once ever ([PREF_WIKI_SEEN]),
     *    Gemini only retells them as short sentences in the user's language: one call per batch
     *  - experimental ([WidgetPrefs.TRIVIA_AI]): half written by Gemini on three random topics, then checked by Gemini
     *    with a web search, only what is definitely true stays: a second call
     * Without Wikipedia (offline, error) and without the experiment there are no new facts this time.
     */
    private fun fetchBatch(app: Context, korean: Boolean, avoid: String): List<String>? {
        val prefs = app.prefs()
        val ai = prefs.getBoolean(WidgetPrefs.TRIVIA_AI, false)
        val wikiWanted = if (ai) BATCH - BATCH / 2 else BATCH
        val seen = wikiSeen(prefs)
        val notSeen = { hook: String -> hookKey(hook) !in seen }
        // the interests' share first (as many as are found), the rest general; some get dropped when they can't be short
        val interests = TriviaInterests.withKeywords(prefs)
        val share = if (interests.isEmpty()) 0f else TriviaInterests.share(prefs)
        val forInterests = Math.round(wikiWanted * share)
        val liked = if (forInterests == 0) emptyList()
            else WikiFacts.matching(interests.flatMap { it.keywords }, forInterests + 3, notSeen)
        val general = if (share >= 1f && liked.isNotEmpty()) emptyList()
            else WikiFacts.hooks(wikiWanted - minOf(liked.size, forInterests) + 6) { notSeen(it) && it !in liked }
        val wiki = liked + general
        val own = if (!ai) 0 else if (wiki.isEmpty()) BATCH else BATCH / 2
        if (wiki.isEmpty() && own == 0) {
            Log.w(TAG, "no trivia: Wikipedia not reached")
            return null
        }
        // Gemini's own (experiment): the interests take their share of the topics
        val liking = interests.map { it.text }.shuffled()
        val topics = (if (liking.isNotEmpty() && Math.random() < share) liking.take(3) else TOPICS.shuffled().take(3))
            .joinToString(", ")
        val lang = if (korean) "Korean" else "English"
        val prompt = "You write short, true and delightfully useless trivia facts. Every fact must be correct and " +
            "well established. Never write popular myths or misconceptions (like goldfish having a 3-second memory). " +
            "Avoid exact numbers, superlatives and \"the first / the only\" unless you are certain. If unsure, skip it. " +
            "Each fact is one short sentence in $lang, at most ${MAX_CHARS - 5} characters. " +
            "Output only lines starting with \"G: \" or \"W: \", nothing else."
        val request = buildString {
            if (own > 0) append("Part G: write $own facts about $topics, one per line, each starting with \"G: \".\n")
            if (wiki.isNotEmpty()) {
                append("Part W: retell each of these checked facts as one short $lang sentence starting with \"W: \". ")
                append("Keep the meaning exactly, add nothing. Skip any that can't be said that short.\n")
                wiki.forEach { append("- ").append(it).append('\n') }
            }
            if (own > 0 && avoid.isNotEmpty()) append("\nDo not repeat these or anything close to them:\n").append(avoid)
        }
        val first = GeminiClient.run(prefs, prompt, request, search = false, raw = true)
        if (first !is GeminiClient.Outcome.Success) {
            Log.w(TAG, "no trivia: ${(first as GeminiClient.Outcome.Failure).message}")
            return null
        }
        // these hooks are used up, also the ones that were skipped
        rememberWiki(prefs, seen, wiki)
        val lines = first.text.lines().map { it.trim() }
        val fromWiki = lines.filter { it.startsWith("W:") }.map { it.removePrefix("W:").trim() }
        val written = lines.filter { it.startsWith("G:") }.map { it.removePrefix("G:").trim() }.filter { it.isNotEmpty() }
        return fromWiki + verified(prefs, written)
    }

    /** a hook, the same however it is retold */
    private fun hookKey(hook: String) = Integer.toHexString(norm(hook).hashCode())

    private fun wikiSeen(prefs: SharedPreferences): MutableSet<String> =
        prefs.getString(PREF_WIKI_SEEN, "").orEmpty().split(',').filterTo(LinkedHashSet()) { it.isNotEmpty() }

    private fun rememberWiki(prefs: SharedPreferences, seen: MutableSet<String>, hooks: List<String>) {
        hooks.forEach { seen.add(hookKey(it)) }
        prefs.edit { putString(PREF_WIKI_SEEN, seen.toList().takeLast(WIKI_SEEN_MAX).joinToString(",")) }
    }

    /** the facts Gemini, searching the web, says are definitely true; none if the check can't be made */
    private fun verified(prefs: SharedPreferences, facts: List<String>): List<String> {
        if (facts.isEmpty()) return facts
        val prompt = "You are a strict fact checker. Search the web to check each statement. Output, unchanged and one " +
            "per line, only the statements that are definitely true as written. Leave out anything false, exaggerated, " +
            "a popular myth, or that you can't confirm. Output nothing else."
        val check = GeminiClient.run(prefs, prompt, facts.joinToString("\n"), search = true, raw = true)
        if (check !is GeminiClient.Outcome.Success) return emptyList()
        // with search on, answers may carry citation marks like [1]
        val ok = check.text.lines().map { norm(cleanLine(it.replace(Regex("\\[\\d+(?:,\\s*\\d+)*]"), ""))) }
            .filter { it.isNotEmpty() }.toSet()
        return facts.filter { norm(it) in ok }
    }

    private fun cleanLine(line: String) =
        line.trim().trimStart('-', '*', '•', '·').replace(Regex("^\\d+[.)]\\s*"), "").trim().trim('"')

    /** two facts share most of their letter pairs: the same fact in other words */
    private fun similar(a: String, b: String): Boolean {
        if (a.length < 4 || b.length < 4) return false
        val pa = a.windowed(2).toSet()
        val pb = b.windowed(2).toSet()
        val inter = pa.count { it in pb }
        return inter.toFloat() / (pa.size + pb.size - inter) >= 0.6f
    }
}
