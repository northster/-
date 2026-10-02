// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.fork.slate.GeminiClient
import helium314.keyboard.fork.slate.SlateKeys
import helium314.keyboard.latin.utils.prefs

/**
 * fork: emojis that fit what is being written, for the toolbar widget. Gemini is asked once typing pauses, only
 * while the widget is shown; the last answers are kept, so going back to the same text asks nothing.
 */
object EmojiSuggest {
    const val COUNT = 6
    private val handler = Handler(Looper.getMainLooper())
    private val cache = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 20
    }
    private var request = 0

    fun cached(text: String) = cache[key(text)]

    private fun key(text: String) = text.trim().takeLast(200)

    /** asks for emojis for [text]; [onDone] on the main thread unless a newer request came meanwhile */
    fun suggest(context: Context, text: String, onDone: (List<String>) -> Unit) {
        val k = key(text)
        if (k.length < 2) return
        cache[k]?.let { onDone(it); return }
        if (SlateKeys.keys(context.prefs()).isEmpty()) return
        val id = ++request
        val app = context.applicationContext
        Thread {
            val outcome = GeminiClient.run(app.prefs(),
                "You suggest emojis for a message being written. Output only $COUNT emojis that fit it best, best first, " +
                    "separated by spaces, nothing else.", k, search = false, raw = true)
            val emojis = (outcome as? GeminiClient.Outcome.Success)?.text?.split(Regex("\\s+"))
                ?.map { it.trim() }?.filter { isEmoji(it) }?.distinct()?.take(COUNT).orEmpty()
            handler.post {
                if (emojis.isNotEmpty()) cache[k] = emojis
                if (id == request && emojis.isNotEmpty()) onDone(emojis)
            }
        }.start()
    }

    /** an emoji (with its modifiers / joiners), not text */
    private fun isEmoji(s: String): Boolean {
        if (s.isEmpty() || s.length > 16) return false
        val first = s.codePointAt(0)
        return first >= 0x1F000 || first in 0x2190..0x2BFF || first in 0x2600..0x27BF || first == 0x00A9 || first == 0x00AE
    }
}
