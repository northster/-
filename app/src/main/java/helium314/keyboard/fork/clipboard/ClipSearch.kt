// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.clipboard

import helium314.keyboard.event.CombinerChain
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.common.Constants

/**
 * fork: search query typed with the keyboard while the clipboard search bar is open.
 * Key events are taken away from the app and composed here (Hangul included, same combiners as normal typing).
 */
class ClipSearch {
    var isActive = false
        private set
    private var spec = ""
    private var chain = CombinerChain("", "")
    private val events = ArrayList<Event>()

    val query: String get() = chain.composingWordWithCombiningFeedback.toString()

    fun start(combiningSpec: String?) {
        isActive = true
        spec = combiningSpec.orEmpty()
        chain = CombinerChain("", spec)
        events.clear()
    }

    fun stop() {
        isActive = false
        chain = CombinerChain("", spec)
        events.clear()
    }

    /** language switch while searching: keep the text, compose with the new rules */
    fun setCombiningSpec(combiningSpec: String?) {
        val newSpec = combiningSpec.orEmpty()
        if (newSpec == spec) return
        chain = CombinerChain(query, newSpec)
        spec = newSpec
        events.clear()
    }

    /**
     * Returns true if the event changed the query and must not reach the app.
     * Enter, keyboard switching etc. are not taken.
     */
    fun onEvent(event: Event): Boolean {
        if (!isActive) return false
        val code = if (event.keyCode != Event.NOT_A_KEY_CODE) event.keyCode else event.codePoint
        val isText = event.codePoint >= Constants.CODE_SPACE && event.metaState == 0
        if (code != KeyCode.DELETE && !isText) return false
        val processed = chain.processEvent(events, event)
        events.add(event)
        chain.applyProcessedEvent(processed)
        if (code == KeyCode.DELETE && query.isEmpty()) events.clear()
        return true
    }

    fun onText(text: String) {
        if (!isActive) return
        // text (e.g. from a popup key) ends composition
        val q = query + text
        chain = CombinerChain(q, spec)
        events.clear()
    }

    companion object {
        /** clips matching [query], newest first, text clips only */
        fun filter(entries: List<ClipboardHistoryEntry>, query: String, limit: Int = 30): List<ClipboardHistoryEntry> {
            val q = query.trim()
            return entries.asSequence()
                .filter { it.filename == null && !it.text.isNullOrEmpty() }
                .filter { q.isEmpty() || it.text!!.contains(q, ignoreCase = true) }
                .sortedByDescending { it.timeStamp }
                .take(limit)
                .toList()
        }
    }
}
