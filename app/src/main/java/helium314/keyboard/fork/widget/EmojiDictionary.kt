// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import org.json.JSONObject

/**
 * fork: emojis for the last words typed, offline: Korean and English keywords of every emoji from the Unicode CLDR
 * annotations (assets/emoji_keywords.txt, made by scripts/emoji_dict.py, Unicode License v3). Korean words with a
 * particle or ending ("피자를", "축하해요") are found by their start. Emojis picked often come up first.
 */
object EmojiDictionary {
    private const val ASSET = "emoji_keywords.txt"
    private const val PREF_USE = "fork_emoji_widget_use"
    @Volatile private var index: Map<String, List<String>>? = null

    private fun load(context: Context): Map<String, List<String>> {
        index?.let { return it }
        synchronized(this) {
            index?.let { return it }
            val map = HashMap<String, List<String>>(16000)
            runCatching {
                context.assets.open(ASSET).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val tab = line.indexOf('\t')
                        if (tab <= 0) continue
                        map[line.substring(0, tab)] = line.substring(tab + 1).split(' ').filter { !isBoxedLetter(it) }
                    }
                }
            }
            index = map
            return map
        }
    }

    /** 🈂 🈚 🅰 …: squares with a letter in them, they look like text rather than an emoji */
    private fun isBoxedLetter(emoji: String) = emoji.isNotEmpty() && emoji.codePointAt(0) in 0x1F170..0x1F2FF

    /** loads the dictionary now (it takes a moment), off the main thread */
    fun preload(context: Context) {
        if (index == null) Thread { load(context.applicationContext) }.start()
    }

    /** up to [count] emojis for the end of [text], best first; empty while the dictionary is not loaded yet */
    fun suggest(context: Context, text: String, count: Int): List<String> {
        val map = index ?: run { preload(context); return emptyList() }
        val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.takeLast(3).reversed()
        val found = LinkedHashMap<String, Int>() // emoji -> rank (lower is better)
        var rank = 0
        for ((w, word) in words.withIndex()) {
            for (emoji in lookup(map, word)) {
                // the last word counts most
                if (emoji !in found) found[emoji] = rank + w * 4
                rank++
            }
        }
        if (found.isEmpty()) return emptyList()
        val use = uses(context)
        return found.entries.sortedBy { it.value - minOf(use[it.key] ?: 0, 5) * 2 }.map { it.key }.take(count)
    }

    private fun lookup(map: Map<String, List<String>>, word: String): List<String> {
        map[word]?.let { return it }
        // English plural
        if (word.length > 3 && word.endsWith("s")) map[word.dropLast(1)]?.let { return it }
        // Korean with a particle / ending ("피자를", "축하해요"): a keyword followed by a known particle or ending.
        // Any start of the word gave odd emojis ("보여줘" -> "보"), so the rest must be a real particle / ending.
        if (word.any { it in '가'..'힣' }) {
            for (len in word.length - 1 downTo 1) {
                val rest = word.substring(len)
                if (!isEnding(rest) && !(len >= 2 && rest.length == 1)) continue
                map[word.substring(0, len)]?.let { return it }
            }
        }
        return emptyList()
    }

    /** particles and endings after a noun or the stem of a 하다 verb */
    private fun isEnding(rest: String) = rest in ENDINGS || rest.startsWith("하") || rest.startsWith("해")
        || rest.startsWith("했") || rest.startsWith("한") || rest.startsWith("할") || rest.startsWith("이에") || rest.startsWith("이야")

    private val ENDINGS = setOf(
        "이", "가", "은", "는", "을", "를", "의", "에", "에서", "에게", "한테", "께", "로", "으로", "와", "과", "랑", "이랑",
        "도", "만", "까지", "부터", "보다", "처럼", "같이", "이나", "나", "요", "야", "이다", "다", "예요", "에요", "입니다",
        "이죠", "죠", "인데", "인가", "이네", "네", "들", "들이", "들을", "들은", "들도", "이고", "고", "이지", "지",
    )

    private fun uses(context: Context): Map<String, Int> {
        val o = runCatching { JSONObject(context.prefs().getString(PREF_USE, "{}") ?: "{}") }.getOrNull() ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optInt(it) }
    }

    /** an emoji from the widget was used: it comes up earlier next time */
    fun used(context: Context, emoji: String) {
        val prefs = context.prefs()
        val o = runCatching { JSONObject(prefs.getString(PREF_USE, "{}") ?: "{}") }.getOrElse { JSONObject() }
        o.put(emoji, o.optInt(emoji) + 1)
        prefs.edit { putString(PREF_USE, o.toString()) }
    }
}
