// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * fork: where the trivia comes from: Wikipedia's "vital articles" (Level 4, about ten thousand subjects editors chose
 * as the ones every encyclopedia needs: cat, Moon, chocolate, Roman Empire…), so facts are about things people know.
 * A fact is taken from the start of such an article (its intro, written and sourced by people). Text from Wikipedia,
 * CC BY-SA 4.0.
 */
object WikiFacts {
    /** an article and the start of its text */
    class Article(val title: String, val text: String)

    private const val API = "https://en.wikipedia.org/w/api.php"
    /** the Level 4 lists, without mathematics (too abstract for a toolbar) */
    private val LISTS = listOf("Biology and health sciences", "Everyday life", "Geography", "History", "Arts",
        "Physical sciences", "Technology", "Society and social sciences", "Philosophy and religion", "People")
    /** characters of an intro sent along, enough for a few facts */
    private const val INTRO_CHARS = 1200

    /** what went wrong last, for the settings */
    @Volatile var lastError: String? = null

    /** the titles of each list, kept while the keyboard runs (a few requests each) */
    private val listCache = HashMap<String, List<String>>()

    /** up to [count] random vital articles that are [wanted] (not used before), with their intros; blocking */
    fun articles(count: Int, wanted: (String) -> Boolean): List<Article> {
        val titles = LinkedHashSet<String>()
        for (list in LISTS.shuffled().take(3)) runCatching {
            titles.addAll(listTitles(list).filter(wanted).shuffled().take(count))
        }.onFailure { lastError = it.javaClass.simpleName + ": " + it.message }
        if (titles.isEmpty() && lastError == null) lastError = "no vital articles"
        return intros(titles.shuffled().take(count))
    }

    /**
     * up to [count] articles found by Wikipedia's search for the [keywords] (the interests), the best matches of each
     * keyword, with their intros; blocking
     */
    fun matching(keywords: List<String>, count: Int, wanted: (String) -> Boolean): List<Article> {
        val titles = LinkedHashSet<String>()
        for (keyword in keywords.shuffled().take(4)) runCatching {
            val url = "$API?action=query&format=json&formatversion=2&list=search&srnamespace=0&srlimit=15&srsearch=" +
                URLEncoder.encode(keyword, "UTF-8")
            val results = JSONObject(get(url)).getJSONObject("query").getJSONArray("search")
            (0 until results.length()).map { results.getJSONObject(it).getString("title") }
                .filter { wanted(it) && !it.startsWith("List of") }.take(4).forEach { titles.add(it) }
        }.onFailure { lastError = it.javaClass.simpleName + ": " + it.message }
        return intros(titles.shuffled().take(count))
    }

    private fun listTitles(list: String): List<String> {
        synchronized(listCache) { listCache[list]?.let { return it } }
        val titles = ArrayList<String>()
        var cont = ""
        for (round in 0 until 10) {
            val url = "$API?action=query&format=json&formatversion=2&redirects=1&prop=links&plnamespace=0&pllimit=max" +
                "&titles=" + URLEncoder.encode("Wikipedia:Vital articles/Level 4/$list", "UTF-8") + cont
            val json = JSONObject(get(url))
            val pages = json.getJSONObject("query").optJSONArray("pages")
            if (pages != null) for (i in 0 until pages.length()) pages.getJSONObject(i).optJSONArray("links")?.let { links ->
                for (j in 0 until links.length()) titles.add(links.getJSONObject(j).getString("title"))
            }
            val next = json.optJSONObject("continue")?.optString("plcontinue")?.takeIf { it.isNotEmpty() } ?: break
            cont = "&plcontinue=" + URLEncoder.encode(next, "UTF-8")
        }
        if (titles.isNotEmpty()) synchronized(listCache) { listCache[list] = titles }
        return titles
    }

    /** the start of each article as plain text (20 per request); articles without text are left out */
    private fun intros(titles: List<String>): List<Article> {
        val result = ArrayList<Article>()
        for (chunk in titles.chunked(20)) runCatching {
            val url = "$API?action=query&format=json&formatversion=2&redirects=1&prop=extracts&exintro=1&explaintext=1" +
                "&exlimit=20&titles=" + URLEncoder.encode(chunk.joinToString("|"), "UTF-8")
            val pages = JSONObject(get(url)).getJSONObject("query").optJSONArray("pages") ?: return@runCatching
            for (i in 0 until pages.length()) {
                val page = pages.getJSONObject(i)
                val text = page.optString("extract").trim().takeIf { it.length > 80 } ?: continue
                result.add(Article(page.getString("title"), text.take(INTRO_CHARS)))
            }
        }.onFailure { lastError = it.javaClass.simpleName + ": " + it.message }
        return result
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
