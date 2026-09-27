// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * fork: undo / redo for settings changes, shown as buttons in the settings top bar. A slip (a wrong tap, a dragged
 * slider) can be taken back without remembering what the value was.
 *
 * Works on the preferences themselves: a copy of all values is kept, and every change is recorded with the value
 * before it. Changes to the same setting in quick succession (a slider being dragged) count as one. Values the
 * keyboard writes by itself while it is used (toolbar state, recent emojis, test timers, exchange rates, ...) are
 * not settings the user changed and are not recorded.
 */
object SettingsHistory {
    private class Change(val key: String, val old: Any?, var new: Any?, var time: Long)

    private val undoStack = ArrayDeque<Change>()
    private val redoStack = ArrayDeque<Change>()
    private var snapshot = HashMap<String, Any?>()
    private var applying = false

    /** changes with every step, for the buttons to recompose */
    val state = MutableStateFlow(0)

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun start(prefs: SharedPreferences) {
        snapshot = HashMap(prefs.all)
    }

    fun onChanged(prefs: SharedPreferences, key: String?) {
        if (key == null) { // cleared
            snapshot = HashMap(prefs.all)
            return
        }
        val new = prefs.all[key]
        val old = snapshot[key]
        if (new == null) snapshot.remove(key) else snapshot[key] = new
        if (applying || old == new || !isUserSetting(key)) return
        val now = SystemClock.uptimeMillis()
        val last = undoStack.lastOrNull()
        if (last != null && last.key == key && now - last.time < MERGE_MILLIS) {
            last.new = new
            last.time = now
        } else {
            undoStack.addLast(Change(key, old, new, now))
            if (undoStack.size > MAX_STEPS) undoStack.removeFirst()
        }
        redoStack.clear()
        state.value++
    }

    fun undo(prefs: SharedPreferences) {
        val change = undoStack.removeLastOrNull() ?: return
        write(prefs, change.key, change.old)
        redoStack.addLast(change)
        state.value++
    }

    fun redo(prefs: SharedPreferences) {
        val change = redoStack.removeLastOrNull() ?: return
        write(prefs, change.key, change.new)
        change.time = 0 // a following change of the same setting is a new step
        undoStack.addLast(change)
        state.value++
    }

    private fun write(prefs: SharedPreferences, key: String, value: Any?) {
        applying = true
        try {
            prefs.edit(commit = true) {
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    null -> remove(key)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value as Set<String>)
                }
            }
        } finally {
            applying = false
        }
    }

    /** values the keyboard keeps for itself, not choices made in the settings */
    private fun isUserSetting(key: String): Boolean {
        val k = key.lowercase()
        return IGNORED_PARTS.none { it in k } && !k.endsWith("_time")
    }

    private val IGNORED_PARTS = listOf("expanded", "recent", "_until", "rates", "last_", "pinned_added",
        "emoji_category", "page_id", "migrat", "version", "stats", "touch_data", "usage", "gif_klipy", "gif_giphy")

    private const val MERGE_MILLIS = 1500L
    private const val MAX_STEPS = 50
}
