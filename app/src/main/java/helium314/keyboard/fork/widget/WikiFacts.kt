// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

/**
 * fork: facts checked by people for the trivia widget: the "Did you know" hooks of English Wikipedia (each one reviewed
 * by editors, with a source in its article), from a random month of the archive
 * (Wikipedia:Did you know archive/<year>/<month>). Text from Wikipedia, CC BY-SA 4.0.
 */
object WikiFacts {
    /** a hook as plain text and the article it is about (its bold link), null if none is found */
    class Hook(val text: String, val title: String?)

    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September",
        "October", "November", "December")
    private const val FIRST_YEAR = 2012
    private const val API = "https://en.wikipedia.org/w/api.php"
    /** what went wrong last, for the settings */
    @Volatile var lastError: String? = null

    /**
     * up to [count] hooks ("... that X did Y?" without the "... that") that are [wanted] (not taken before), from two
     * random months, about well-known things: Did you know is about new, often obscure articles, so the hooks whose
     * article is read most (Wikipedia page views of the last 30 days) are taken; blocking, empty on errors
     */
    fun hooks(count: Int, wanted: (String) -> Boolean): List<Hook> {
        val candidates = LinkedHashMap<String, Hook>()
        repeat(2) {
            runCatching {
                val lastYear = Calendar.getInstance().get(Calendar.YEAR) - 1
                val page = "Wikipedia:Did you know archive/${(FIRST_YEAR..lastYear).random()}/${MONTHS.random()}"
                val all = wikitext(page).lineSequence().mapNotNull { hook(it) }.toList()
                if (all.isEmpty()) lastError = "$page: 0 hooks"
                all.filter { it.title != null && wanted(it.text) }.forEach { candidates[it.text] = it }
            }.onFailure { lastError = it.javaClass.simpleName + ": " + it.message }
        }
        return mostRead(candidates.values.shuffled().take(MAX_SCORED), count)
    }

    /** hooks whose article is read at least this often in 30 days (a cat: 350 000, a sea otter: 35 000) */
    private const val MIN_VIEWS = 3000L
    private const val MIN_VIEWS_INTEREST = 300L
    /** hooks whose page views are looked up per batch (50 per request) */
    private const val MAX_SCORED = 300

    /** the [count] hooks about the most read articles (and read at least [MIN_VIEWS]), most read first */
    private fun mostRead(hooks: List<Hook>, count: Int, min: Long = MIN_VIEWS): List<Hook> {
        if (hooks.isEmpty()) return hooks
        val views = views(hooks.mapNotNull { it.title })
        val scored = hooks.map { it to (views[it.title] ?: 0L) }.filter { it.second >= min }
        if (scored.isEmpty()) lastError = "${hooks.size} hooks, none about a well-known article"
        return scored.sortedByDescending { it.second }.take(count).map { it.first }
    }

    /** page views of the last 30 days for each of [titles]; titles missing on errors */
    private fun views(titles: List<String>): Map<String, Long> {
        val result = HashMap<String, Long>()
        for (chunk in titles.distinct().chunked(50)) runCatching {
            val url = "$API?action=query&format=json&formatversion=2&redirects=1&prop=pageviews&pvipdays=30&titles=" +
                URLEncoder.encode(chunk.joinToString("|"), "UTF-8")
            val query = JSONObject(get(url)).getJSONObject("query")
            // titles may have been normalized or redirected on the way
            val renamed = HashMap<String, String>()
            for (key in listOf("normalized", "redirects")) query.optJSONArray(key)?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { renamed[it.getString("to")] = it.getString("from") }
            }
            val pages = query.optJSONArray("pages") ?: return@runCatching
            for (i in 0 until pages.length()) {
                val page = pages.getJSONObject(i)
                val pv = page.optJSONObject("pageviews") ?: continue
                var sum = 0L
                for (day in pv.keys()) if (!pv.isNull(day)) sum += pv.optLong(day)
                var title = page.getString("title")
                var hops = 0
                while (title !in chunk && hops++ < 3) title = renamed[title] ?: break
                result[title] = sum
            }
        }
        return result
    }

    /**
     * up to [count] hooks that mention one of [keywords] (as a word), found with Wikipedia's search in the archive
     * pages, the most read articles first; blocking, empty on errors
     */
    fun matching(keywords: List<String>, count: Int, wanted: (String) -> Boolean): List<Hook> {
        val found = LinkedHashMap<String, Hook>()
        for (keyword in keywords.shuffled().take(4)) {
            runCatching {
                val query = "\"$keyword\" prefix:Wikipedia:Did you know archive/"
                val url = "$API?action=query&format=json&formatversion=2&list=search&srnamespace=4&srlimit=10&srsearch=" +
                    URLEncoder.encode(query, "UTF-8")
                val results = JSONObject(get(url)).getJSONObject("query").getJSONArray("search")
                val titles = (0 until results.length()).map { results.getJSONObject(it).getString("title") }
                val word = Regex("\\b" + Regex.escape(keyword) + "(?:s|es)?\\b", RegexOption.IGNORE_CASE)
                for (title in titles.shuffled().take(2)) {
                    wikitext(title).lineSequence().mapNotNull { hook(it) }
                        .filter { word.containsMatchIn(it.text) && wanted(it.text) }.toList().shuffled()
                        .take(10).forEach { found[it.text] = it }
                }
            }
        }
        // about what the user likes, so a lower bar: the most read first, a few hundred views are enough
        return mostRead(found.values.filter { it.title != null }, count, MIN_VIEWS_INTEREST)
    }

    private fun hook(line: String) = clean(line)?.let { Hook(it, title(line)) }

    /** the article a hook line is about: its bold link, else its first link */
    internal fun title(line: String): String? {
        val bold = Regex("'''+\\s*\\[\\[([^|\\]#]+)").find(line)
        val any = Regex("\\[\\[([^|\\]#:]+)").find(line)
        return (bold ?: any)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Where to read about each of [titles] (English articles): the Korean article when there is one and [korean],
     * else the English one; blocking, English links on errors
     */
    fun pages(titles: List<String>, korean: Boolean): Map<String, String> {
        val result = titles.associateWith { url("en", it) }.toMutableMap()
        if (!korean || titles.isEmpty()) return result
        runCatching {
            for (chunk in titles.distinct().chunked(50)) {
                val url = "$API?action=query&format=json&formatversion=2&redirects=1&prop=langlinks&lllang=ko&lllimit=max&titles=" +
                    URLEncoder.encode(chunk.joinToString("|"), "UTF-8")
                val query = JSONObject(get(url)).getJSONObject("query")
                // titles may have been normalized or redirected on the way
                val renamed = HashMap<String, String>()
                for (key in listOf("normalized", "redirects")) query.optJSONArray(key)?.let { a ->
                    for (i in 0 until a.length()) a.getJSONObject(i).let { renamed[it.getString("to")] = it.getString("from") }
                }
                val pages = query.optJSONArray("pages") ?: continue
                for (i in 0 until pages.length()) {
                    val page = pages.getJSONObject(i)
                    val ko = page.optJSONArray("langlinks")?.optJSONObject(0)?.optString("title")?.takeIf { it.isNotEmpty() }
                        ?: continue
                    var original = page.getString("title")
                    var hops = 0
                    while (original !in result && hops++ < 3) original = renamed[original] ?: break
                    if (original in result) result[original] = url("ko", ko)
                }
            }
        }
        return result
    }

    private fun url(lang: String, title: String) =
        "https://$lang.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "_")

    private fun wikitext(title: String): String {
        val url = "$API?action=parse&format=json&formatversion=2&redirects=1&prop=wikitext&page=" + URLEncoder.encode(title, "UTF-8")
        return JSONObject(get(url)).getJSONObject("parse").getString("wikitext")
    }

    /** a hook line as plain text, null if it is not one or has markup that can't be read simply */
    internal fun clean(line: String): String? {
        val t = line.trim()
        if (!t.startsWith("*")) return null
        var s = t.trimStart('*', ' ').removePrefix("...").removePrefix("…").trim()
        if (!s.startsWith("that ")) return null
        s = s.removePrefix("that ").trim()
        if ("{{" in s || "<ref" in s) return null
        s = s.replace(Regex("\\[\\[(?:[^|\\]]*\\|)?([^\\]]*)]]"), "$1") // [[target|label]] -> label
            .replace("'''", "").replace("''", "")
            .replace(Regex("\\s*\\((?:pictured|shown|illustrated|example shown)[^)]*\\)"), "")
            .replace(Regex("<[^>]+>"), "")
            .trim()
        if (s.length < 15 || s.length > 240 || '[' in s || '|' in s) return null
        return s
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            // Wikipedia asks for a User-Agent that says who calls
            c.setRequestProperty("User-Agent", "DTKeyboard (https://github.com/northster/-)")
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
