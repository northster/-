// SPDX-License-Identifier: GPL-3.0-only
// Commands, defaults and trigger matching ported from SwiftSlate (github.com/Musheer360/SwiftSlate,
// manager/CommandManager.kt, model/Command.kt), MIT License, Copyright (c) 2026 Musheer Alam.
// fork: stored in the keyboard's preferences; AI commands may use Google Search (Gemini grounding), and two such
// commands are added: the original title of a song / work (천본앵 -> 千本桜) and a short web answer.
package helium314.keyboard.fork.slate

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

enum class CommandType { AI, TEXT_REPLACER }

/**
 * A command typed at the end of the text: "`...text ?fix`". [prompt] is the instruction for an AI command, or the text
 * put in place of the trigger for a text replacer. [search]: the AI may look things up on the web first.
 */
data class SlateCommand(
    val trigger: String,
    val prompt: String,
    val isBuiltIn: Boolean = false,
    val type: CommandType = CommandType.AI,
    val search: Boolean = false,
)

object SlateCommands {
    const val PREF_ENABLED = "fork_slate_enabled"
    const val PREF_PREFIX = "fork_slate_prefix"
    const val PREF_COMMANDS = "fork_slate_commands"
    private const val PREF_SEEDED = "fork_slate_seeded"
    const val DEFAULT_PREFIX = "?"
    const val MAX_TRIGGER_LENGTH = 50
    const val MAX_PROMPT_LENGTH = 5_000

    /** local operations that cannot be edited or deleted */
    private val systemDefinitions = listOf(
        "undo" to "Undo the last replacement and restore the original text.",
        "copy" to "Copy the text to clipboard.",
        "cut" to "Cut the text to clipboard.",
        "paste" to "Paste from clipboard.",
        "replace" to "Replace text with clipboard content.",
        "translate:xx" to "Translate text to any language code (e.g. ?translate:es, ?translate:fr)."
    )

    /** seeded into the custom commands on first use, so they can be edited or deleted */
    private val defaultAiDefinitions = listOf(
        Triple("fix", "Fix grammar, spelling, and punctuation errors.", false),
        Triple("improve", "Rewrite to improve clarity, flow, and coherence.", false),
        Triple("shorten", "Rewrite to be more concise while preserving the core meaning.", false),
        Triple("expand", "Rewrite with more detail. Elaborate only on what is stated or widely known — do not fabricate information.", false),
        Triple("formal", "Rewrite in a formal, professional tone.", false),
        Triple("casual", "Rewrite in a casual, friendly tone.", false),
        Triple("emoji", "Add relevant emojis throughout.", false),
        Triple("human", "Rewrite to sound naturally human, not AI-generated. Never use emdashes or semicolons, use commas or periods instead. Drop AI clichés and filler phrases. Use contractions, everyday words, and varied sentence lengths. Keep all facts, names, and numbers intact.", false),
        Triple("reply", "Generate a contextual reply to this message.", false),
        // fork: web search commands
        Triple("원어", "The input names a song, album, anime, game or other work, often as a Korean translation or " +
            "transliteration of its title (e.g. a Vocaloid song: 천본앵 is 千本桜). Search the web to find the work and " +
            "output only its official title in the original language, nothing else. If the input is several titles, " +
            "output them in the same order and format.", true),
        Triple("검색", "Search the web and answer the input as a short fact, in the language of the input. Output only " +
            "the answer, one or two sentences, no sources or formatting.", true),
    )

    fun enabled(prefs: SharedPreferences) = prefs.getBoolean(PREF_ENABLED, true)

    fun prefix(prefs: SharedPreferences): String =
        prefs.getString(PREF_PREFIX, DEFAULT_PREFIX)?.takeIf { it.length == 1 } ?: DEFAULT_PREFIX

    fun builtIns(prefs: SharedPreferences): List<SlateCommand> {
        val prefix = prefix(prefs)
        return systemDefinitions.map { (name, prompt) -> SlateCommand("$prefix$name", prompt, isBuiltIn = true) }
    }

    fun custom(prefs: SharedPreferences): List<SlateCommand> {
        if (!prefs.getBoolean(PREF_SEEDED, false)) seed(prefs)
        val arr = runCatching { JSONArray(prefs.getString(PREF_COMMANDS, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val trigger = o.optString("trigger")
            val prompt = o.optString("prompt")
            if (trigger.isEmpty() || prompt.isEmpty()) return@mapNotNull null
            SlateCommand(trigger, prompt, false,
                runCatching { CommandType.valueOf(o.optString("type", CommandType.AI.name)) }.getOrDefault(CommandType.AI),
                o.optBoolean("search", false))
        }
    }

    private fun seed(prefs: SharedPreferences) {
        val prefix = prefix(prefs)
        val arr = JSONArray()
        for ((name, prompt, search) in defaultAiDefinitions)
            arr.put(JSONObject().put("trigger", "$prefix$name").put("prompt", prompt).put("type", CommandType.AI.name).put("search", search))
        prefs.edit { putString(PREF_COMMANDS, arr.toString()); putBoolean(PREF_SEEDED, true) }
    }

    fun saveCustom(prefs: SharedPreferences, commands: List<SlateCommand>) {
        val arr = JSONArray()
        commands.filter { isValid(it, prefix(prefs)) }.forEach {
            arr.put(JSONObject().put("trigger", it.trigger).put("prompt", it.prompt).put("type", it.type.name).put("search", it.search))
        }
        prefs.edit { putString(PREF_COMMANDS, arr.toString()) }
    }

    fun isValid(command: SlateCommand, prefix: String) =
        command.trigger.isNotBlank() && command.prompt.isNotBlank() &&
            command.trigger.length <= MAX_TRIGGER_LENGTH && command.prompt.length <= MAX_PROMPT_LENGTH &&
            command.trigger.startsWith(prefix) && command.trigger.length > prefix.length && ' ' !in command.trigger

    private const val PREF_USAGE = "fork_slate_usage"

    /** a command was run: counted, so the toolbar lists the most used ones first */
    fun countUse(prefs: SharedPreferences, trigger: String) {
        val usage = runCatching { JSONObject(prefs.getString(PREF_USAGE, "{}")!!) }.getOrElse { JSONObject() }
        usage.put(trigger, usage.optInt(trigger) + 1)
        prefs.edit { putString(PREF_USAGE, usage.toString()) }
    }

    /** [commands] with the most used first; the ones never used keep their order */
    fun <T> byUse(prefs: SharedPreferences, commands: List<T>, trigger: (T) -> String): List<T> {
        val usage = runCatching { JSONObject(prefs.getString(PREF_USAGE, "{}")!!) }.getOrElse { JSONObject() }
        return commands.sortedByDescending { usage.optInt(trigger(it)) }
    }

    /** all commands, longest trigger first so "?formal" wins over a "?form" */
    fun all(prefs: SharedPreferences): List<SlateCommand> =
        (builtIns(prefs) + custom(prefs)).sortedByDescending { it.trigger.length }

    /** the command [text] ends with, if any */
    fun find(prefs: SharedPreferences, text: String): SlateCommand? {
        for (cmd in all(prefs)) {
            if (cmd.trigger.endsWith("translate:xx")) continue
            if (text.endsWith(cmd.trigger)) return cmd
        }
        // ?translate:xx takes any 2-5 letter language code; the model handles invalid ones
        val translatePrefix = "${prefix(prefs)}translate:"
        val at = text.lastIndexOf(translatePrefix)
        if (at >= 0) {
            val lang = text.substring(at + translatePrefix.length)
            if (lang.length in 2..5 && lang.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' })
                return SlateCommand("$translatePrefix$lang", "Translate to language code '$lang'.", true)
        }
        return null
    }
}

/** The Gemini API keys, encrypted with [KeyCipher]; used in turn, a rate limited key rests for a while. */
object SlateKeys {
    const val PREF_KEYS = "fork_slate_keys"
    const val PREF_MODEL = "fork_slate_model"
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    private var next = 0
    private val benchedUntil = HashMap<String, Long>()

    fun model(prefs: SharedPreferences) = prefs.getString(PREF_MODEL, null)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL

    fun keys(prefs: SharedPreferences): List<String> {
        val arr = runCatching { JSONArray(prefs.getString(PREF_KEYS, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { KeyCipher.decrypt(arr.optString(it)) }
    }

    /** @return false when the key can't be stored (no keystore) */
    fun add(prefs: SharedPreferences, key: String): Boolean {
        val encrypted = KeyCipher.encrypt(key.trim()) ?: return false
        val arr = runCatching { JSONArray(prefs.getString(PREF_KEYS, "[]")) }.getOrElse { JSONArray() }
        arr.put(encrypted)
        prefs.edit { putString(PREF_KEYS, arr.toString()) }
        return true
    }

    fun remove(prefs: SharedPreferences, index: Int) {
        val arr = runCatching { JSONArray(prefs.getString(PREF_KEYS, "[]")) }.getOrElse { JSONArray() }
        if (index in 0 until arr.length()) arr.remove(index)
        prefs.edit { putString(PREF_KEYS, arr.toString()) }
    }

    /** the next key to use, skipping [tried] and resting ones */
    @Synchronized
    fun nextKey(prefs: SharedPreferences, tried: Set<String>): String? {
        val keys = keys(prefs)
        val now = System.currentTimeMillis()
        for (i in keys.indices) {
            val key = keys[(next + i) % keys.size]
            if (key in tried || (benchedUntil[key] ?: 0) > now) continue
            next = (next + i + 1) % keys.size
            return key
        }
        return null
    }

    @Synchronized
    fun rateLimited(key: String, seconds: Long) {
        benchedUntil[key] = System.currentTimeMillis() + seconds * 1000
    }
}
