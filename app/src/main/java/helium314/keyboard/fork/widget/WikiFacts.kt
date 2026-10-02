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
 * (Wikipedia:Recent additions/<year>/<month>). Text from Wikipedia, CC BY-SA 4.0.
 */
object WikiFacts {
    private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September",
        "October", "November", "December")
    private const val FIRST_YEAR = 2012
    private const val API = "https://en.wikipedia.org/w/api.php"

    /**
     * up to [count] hooks ("... that X did Y?" without the "... that") that are [wanted] (not taken before), from random
     * months (a few tries if a month has too few left); blocking, empty on errors
     */
    fun hooks(count: Int, wanted: (String) -> Boolean): List<String> {
        val found = LinkedHashSet<String>()
        repeat(3) {
            if (found.size >= count) return@repeat
            runCatching {
                val lastYear = Calendar.getInstance().get(Calendar.YEAR) - 1
                val page = "Wikipedia:Recent additions/${(FIRST_YEAR..lastYear).random()}/${MONTHS.random()}"
                wikitext(page).lineSequence().mapNotNull { clean(it) }.filter(wanted).toList().shuffled()
                    .take(count - found.size).forEach { found.add(it) }
            }
        }
        return found.toList()
    }

    /**
     * up to [count] hooks that mention one of [keywords] (as a word), found with Wikipedia's search in the archive
     * pages; blocking, empty on errors
     */
    fun matching(keywords: List<String>, count: Int, wanted: (String) -> Boolean): List<String> {
        val found = LinkedHashSet<String>()
        for (keyword in keywords.shuffled().take(4)) {
            if (found.size >= count) break
            runCatching {
                val query = "\"$keyword\" prefix:Wikipedia:Recent additions/"
                val url = "$API?action=query&format=json&formatversion=2&list=search&srnamespace=4&srlimit=10&srsearch=" +
                    URLEncoder.encode(query, "UTF-8")
                val results = JSONObject(get(url)).getJSONObject("query").getJSONArray("search")
                val titles = (0 until results.length()).map { results.getJSONObject(it).getString("title") }
                val word = Regex("\\b" + Regex.escape(keyword) + "(?:s|es)?\\b", RegexOption.IGNORE_CASE)
                for (title in titles.shuffled().take(2)) {
                    if (found.size >= count) break
                    wikitext(title).lineSequence().mapNotNull { clean(it) }
                        .filter { word.containsMatchIn(it) && wanted(it) }.toList().shuffled()
                        .take(minOf(count - found.size, 3)) // a few per keyword, so all interests come up
                        .forEach { found.add(it) }
                }
            }
        }
        return found.toList()
    }

    private fun wikitext(title: String): String {
        val url = "$API?action=parse&format=json&formatversion=2&prop=wikitext&page=" + URLEncoder.encode(title, "UTF-8")
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
