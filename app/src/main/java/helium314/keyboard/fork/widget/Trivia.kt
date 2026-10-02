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
 * (see [fetchBatch]: Gemini on a different Wikipedia subject each, checked with a web search, and corrected
 * misconceptions from Wikipedia); each time the toolbar opens one
 * is shown, those not seen yet first. Once only a few are left unseen (20 of a batch of 25 seen) the next batch is
 * fetched. A few hundred are kept, a new fact that is already kept (also in other words) is dropped.
 */
object Trivia {
    private const val PREF_FACTS = "fork_trivia_facts"
    private const val PREF_FETCHED = "fork_trivia_fetched"
    private const val TAG = "Trivia"
    private const val BATCH = 25
    /** share of a batch from Wikipedia's lists of common misconceptions */
    private const val MYTH_SHARE = 0.3f
    /** a new batch once this few are unseen */
    private const val LOW = 5
    private const val KEEP = 400
    /** facts sent along as "not these" (topics change every batch, so the latest few are enough) */
    private const val AVOID = 40
    /** after a failed fetch, wait this long before trying again on its own */
    private const val RETRY_MS = 15 * 60_000L
    private const val PREF_FAILED = "fork_trivia_failed"
    /** keys of the Wikipedia articles used so far, so none comes twice (about 9 characters each) */
    private const val PREF_WIKI_SEEN = "fork_trivia_wiki_seen"
    private const val WIKI_SEEN_MAX = 30_000
    /** characters a fact may have, so it fits the widget in two lines */
    const val MAX_CHARS = 40

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var fetching = false
    private var lastShown: String? = null

    private class Fact(val text: String, var seen: Boolean, val page: String? = null)

    /** a fact to show and where to read more about it (null if it has no article) */
    class Shown(val text: String, val page: String?)

    private const val PREF_VERSION = "fork_trivia_version"

    /**
     * Once: the facts kept so far go. Before Wikipedia Gemini wrote the same famous facts again and again in other
     * words ("sea otters hold hands…"), the first Wikipedia ones were about obscure articles in mixed styles. The next
     * batch comes from Wikipedia, well-known articles only.
     */
    private fun migrate(prefs: SharedPreferences) {
        val version = prefs.getInt(PREF_VERSION, 0)
        if (version >= 5) return
        // versions 3 to 5: the Wikipedia-only facts were obscure or dry, they go too
        prefs.edit { putString(PREF_FACTS, "[]"); putInt(PREF_VERSION, 5); remove(PREF_FAILED) }
    }

    private fun read(prefs: SharedPreferences): MutableList<Fact> {
        migrate(prefs)
        val arr = runCatching { JSONArray(prefs.getString(PREF_FACTS, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                o.optString("t").takeIf { it.isNotBlank() }?.let { Fact(it, o.optBoolean("s"), o.optString("p").ifEmpty { null }) }
            }
        }.toMutableList()
    }

    private fun write(prefs: SharedPreferences, facts: List<Fact>) {
        val arr = JSONArray()
        facts.takeLast(KEEP + BATCH).forEach {
            arr.put(JSONObject().put("t", it.text).put("s", it.seen).apply { if (it.page != null) put("p", it.page) })
        }
        prefs.edit { putString(PREF_FACTS, arr.toString()) }
    }

    /** a fact to show now, not seen before if there is one (it counts as seen from now on); null if there are none */
    fun next(context: Context): Shown? {
        val prefs = context.prefs()
        val facts = read(prefs)
        if (facts.isEmpty()) return null
        // all seen (no new batch came): an old one, but not the one just shown
        val fact = facts.filter { !it.seen }.randomOrNull()
            ?: facts.filter { it.text != lastShown }.randomOrNull() ?: facts.random()
        lastShown = fact.text
        fact.seen = true
        write(prefs, facts)
        return Shown(fact.text, fact.page)
    }

    fun count(context: Context) = read(context.prefs()).size

    fun unseen(context: Context) = read(context.prefs()).count { !it.seen }

    /** same fact, written a bit differently */
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** why the last batch brought nothing, for the settings */
    @Volatile var lastError: String? = null
        private set

    /** a new batch once few are unseen (or [force]); [onDone] on the main thread (also when nothing is tried, if [force]) */
    fun refreshIfDue(context: Context, force: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        val prefs = context.prefs()
        if (fetching || SlateKeys.keys(prefs).isEmpty()) {
            if (force) {
                lastError = if (fetching) "이미 받는 중 (already fetching)" else "Gemini 키 없음 (no Gemini key)"
                handler.post { onDone(false) }
            }
            return
        }
        val unseen = read(prefs).count { !it.seen }
        if (!force && unseen > LOW) return
        if (!force && System.currentTimeMillis() - prefs.getLong(PREF_FAILED, 0) < RETRY_MS) return
        fetching = true
        val avoid = read(prefs).takeLast(AVOID).joinToString("\n") { it.text }
        val app = context.applicationContext
        val korean = java.util.Locale.getDefault().language == "ko"
        Thread {
            // whatever goes wrong, the fetch ends (a stuck flag kept "getting trivia…" forever) and says why
            val added = try {
                fetchAndKeep(app, korean, avoid)
            } catch (e: Exception) {
                Log.w(TAG, "trivia failed", e)
                lastError = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
                app.prefs().edit { putLong(PREF_FAILED, System.currentTimeMillis()) }
                false
            } finally {
                fetching = false
            }
            handler.post { onDone(added) }
        }.start()
    }

    private fun fetchAndKeep(app: Context, korean: Boolean, avoid: String): Boolean {
            val fresh = fetchBatch(app, korean, avoid)
            val added = if (fresh != null) {
                val known = read(app.prefs())
                val keys = known.mapTo(ArrayList()) { norm(it.text) }
                val kept = fresh.map { Fact(cleanLine(it.text), false, it.page) }
                    .filter { it.text.length in 6..(MAX_CHARS + 10) }
                    .filter { f ->
                        // not kept yet (also not written a bit differently), and not twice in this batch
                        val k = norm(f.text)
                        if (keys.any { it == k || similar(it, k) }) false else { keys.add(k); true }
                    }
                // the oldest seen ones go first when there are too many
                val all = known + kept
                var over = all.size - KEEP
                write(app.prefs(), all.filter { fact ->
                    val drop = over > 0 && fact.seen
                    if (drop) over--
                    !drop
                })
                app.prefs().edit { putLong(PREF_FETCHED, System.currentTimeMillis()); remove(PREF_FAILED) }
                if (kept.isEmpty()) lastError = "새 문장 없음: Gemini 답 ${fresh.size}줄, 모두 걸러짐 (nothing new in the answer)"
                else lastError = null
                kept.isNotEmpty()
            } else {
                app.prefs().edit { putLong(PREF_FAILED, System.currentTimeMillis()) }
                false
            }
            return added
    }

    /**
     * About [BATCH] new facts, null if nothing could be made. Two Gemini calls per batch:
     *  1. for most of the batch Gemini gets a subject each (a different Wikipedia vital article every time, the
     *     interests' share from a Wikipedia search for them) and writes the one fact about it people don't know but
     *     enjoy learning; [MYTH_SHARE] of the batch are Wikipedia's corrected misconceptions, retold short
     *  2. Gemini checks its own facts with Google Search, only what is definitely true stays
     * Subjects and misconceptions are used once ever ([PREF_WIKI_SEEN]).
     */
    private fun fetchBatch(app: Context, korean: Boolean, avoid: String): List<Shown>? {
        val prefs = app.prefs()
        val seen = wikiSeen(prefs)
        val notUsed = { key: String -> hookKey(key) !in seen }
        val mythsWanted = Math.round(BATCH * MYTH_SHARE)
        val subjectsWanted = BATCH - mythsWanted
        // the interests' share of the subjects first, the rest random; a few more than needed, some get skipped
        val interests = TriviaInterests.withKeywords(prefs)
        val share = if (interests.isEmpty()) 0f else TriviaInterests.share(prefs)
        val forInterests = Math.round(subjectsWanted * share)
        val liked = if (forInterests == 0) emptyList()
            else WikiFacts.matchingTitles(interests.flatMap { it.keywords }, forInterests + 2, notUsed)
        val general = if (share >= 1f && liked.isNotEmpty()) emptyList()
            else WikiFacts.randomTitles(subjectsWanted - minOf(liked.size, forInterests) + 4) { notUsed(it) && it !in liked }
        val subjects = liked + general
        val myths = WikiFacts.misconceptions(mythsWanted + 2) { notUsed(it) }
        if (subjects.isEmpty() && myths.isEmpty()) {
            Log.w(TAG, "no trivia: Wikipedia not reached")
            lastError = "위키백과를 읽지 못함 (Wikipedia: ${WikiFacts.lastError ?: "nothing found"})"
            return null
        }
        val lang = if (korean) "Korean" else "English"
        // one style for all: plain statements ending in -다 in Korean ("해달은 손을 잡고 잔다.")
        val style = if (korean) "Write every sentence in the plain written style ending in \"-다\" " +
            "(like \"해달은 서로 손을 잡고 잔다.\"), never -습니다/-요."
            else "Write plain statements in the present or past tense."
        val prompt = "You write short trivia facts people don't know but love to learn: surprising, a little useless, " +
            "understandable without background knowledge. Every fact must be true and well established. Never write " +
            "popular myths or misconceptions (like goldfish having a 3-second memory). Avoid exact numbers unless you " +
            "are certain. Each fact is one short sentence in $lang, at most ${MAX_CHARS - 5} characters, and names its " +
            "subject so it makes sense on its own. $style Output only lines starting with \"S<number>: \" or " +
            "\"M<number>: \", nothing else."
        val request = buildString {
            if (subjects.isNotEmpty()) {
                append("Part S: for each numbered subject, write the one fact about it that most people don't know but find ")
                append("surprising or fun. Skip a subject if you know no such fact for sure. Start each line with \"S\" and ")
                append("the subject's number, like \"S3: \".\n")
                subjects.forEachIndexed { i, t -> append(i + 1).append(". ").append(t).append('\n') }
                append('\n')
            }
            if (myths.isNotEmpty()) {
                append("Part M: each numbered text corrects a common misconception. Write the true fact it states as one ")
                append("short sentence (what is actually true, not the myth), starting with \"M\" and its number, like \"M2: \". ")
                append("Use only what the text says.\n")
                myths.forEachIndexed { i, m -> append(i + 1).append(". ").append(m.text.replace('\n', ' ')).append('\n') }
                append('\n')
            }
            if (avoid.isNotEmpty()) append("Do not repeat these or anything close to them:\n").append(avoid)
        }
        val first = GeminiClient.run(prefs, prompt, request, search = false, raw = true)
        if (first !is GeminiClient.Outcome.Success) {
            Log.w(TAG, "no trivia: ${(first as GeminiClient.Outcome.Failure).message}")
            lastError = "Gemini: ${first.message}"
            return null
        }
        // these subjects and misconceptions are used up, also the ones that were skipped
        rememberWiki(prefs, seen, subjects + myths.map { it.text })
        // markdown or list marks around the labels ("**S3:**", "- M1:") are dropped
        val lines = first.text.lines().map { it.replace("**", "").trim().trimStart('-', '*', '•', ' ') }
        fun labelled(letter: Char) = lines.mapNotNull { line ->
            val m = Regex("^$letter\\s*(\\d+)\\s*[:.)：]\\s*(.+)$").find(line) ?: return@mapNotNull null
            m.groupValues[1].toInt() - 1 to m.groupValues[2].trim()
        }.filter { plainStyle(it.second, korean) }
        // Gemini's own facts are checked with a web search, the corrected misconceptions come with sources already
        val written = labelled('S')
        val checked = verified(prefs, written.map { it.second }).toSet()
        val fromSubjects = written.filter { it.second in checked }.map { (i, text) -> text to subjects.getOrNull(i) }
        val fromMyths = labelled('M').map { (i, text) -> text to myths.getOrNull(i)?.title }
        val all = fromSubjects + fromMyths
        if (all.isEmpty()) lastError = "Gemini 답에서 쓸 문장이 없음 (S ${written.size}줄, 확인 통과 ${checked.size}줄)"
        // the article to read more (the Korean one when there is one)
        val pages = WikiFacts.pages(all.mapNotNull { it.second }, korean)
        return all.map { (text, title) -> Shown(text, title?.let { pages[it] }) }.shuffled()
    }

    /** Korean facts all end in -다 (one style in the widget); other languages as they come */
    private fun plainStyle(text: String, korean: Boolean): Boolean {
        if (!korean) return true
        val end = text.trimEnd { !it.isLetterOrDigit() }
        return end.endsWith("다") && !end.endsWith("니다") // not -습니다 / -입니다
    }

    /** an article used for a fact (it is not used again) */
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
