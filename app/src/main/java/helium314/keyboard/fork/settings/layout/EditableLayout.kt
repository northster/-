// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings.layout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * fork: a layout file (HeliBoard json or simple text format) as rows of keys that the visual editor can change.
 * Each key keeps its original json object, so fields the editor doesn't know (codes, flags, selectors) survive saving.
 */
class EditableKey(val json: JsonObject) {
    /** keys with a "$" (keyboard state selectors etc.) can only be moved or deleted */
    val isSpecial get() = json.containsKey("$")
    val isSpacer get() = json["type"]?.jsonPrimitive?.contentOrNull == "placeholder"
    val label get() = json["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
    /** 0 = automatic, -1 = fill the rest of the row, else share of the keyboard width */
    val width get() = json["width"]?.jsonPrimitive?.floatOrNull ?: 0f
    val popups: List<String> get() {
        val popup = json["popup"] as? JsonObject ?: return emptyList()
        val main = (popup["main"] as? JsonObject)?.get("label")?.jsonPrimitive?.contentOrNull
        val relevant = (popup["relevant"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("label")?.jsonPrimitive?.contentOrNull }
        return listOfNotNull(main) + relevant.orEmpty()
    }

    fun withLabel(label: String) = copy { put("label", JsonPrimitive(label)) }
    fun withWidth(width: Float) = copy { if (width == 0f) remove("width") else put("width", JsonPrimitive(width)) }
    fun withPopups(popups: List<String>) = copy {
        if (popups.isEmpty()) remove("popup")
        else put("popup", buildJsonObject {
            put("relevant", buildJsonArray { popups.forEach { add(buildJsonObject { put("label", JsonPrimitive(it)) }) } })
        })
    }
    fun asSpacer(spacer: Boolean) = copy {
        if (spacer) { put("type", JsonPrimitive("placeholder")); remove("label"); remove("popup") }
        else { remove("type"); put("label", JsonPrimitive("?")) }
    }

    private fun copy(change: MutableMap<String, JsonElement>.() -> Unit) = EditableKey(JsonObject(json.toMutableMap().apply(change)))

    companion object {
        fun ofLabel(label: String, popups: List<String> = emptyList()) =
            EditableKey(buildJsonObject { put("label", JsonPrimitive(label)) }).withPopups(popups)
    }
}

object EditableLayout {
    private val json = Json { isLenient = true }

    /** rows of keys, from json (starting with '[') or the simple format (rows separated by blank lines, one key per line) */
    fun parse(text: String): List<List<EditableKey>> {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("[")) {
            val stripped = text.split("\n").filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
            return json.parseToJsonElement(stripped).jsonArray.map { row ->
                row.jsonArray.map { EditableKey(it.jsonObject) }
            }
        }
        return text.split("\n").filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
            .split(Regex("\\n\\s*\\n")).map { block ->
                block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                    val parts = line.split(Regex("\\s+"))
                    EditableKey.ofLabel(parts.first(), parts.drop(1))
                }
            }.filter { it.isNotEmpty() }
    }

    /** json in the format HeliBoard reads, one key per line */
    fun toJson(rows: List<List<EditableKey>>): String = buildString {
        append("[\n")
        rows.forEachIndexed { r, row ->
            append("  [\n")
            row.forEachIndexed { k, key ->
                append("    ").append(key.json.toString())
                if (k < row.lastIndex) append(",")
                append("\n")
            }
            append("  ]")
            if (r < rows.lastIndex) append(",")
            append("\n")
        }
        append("]\n")
    }

    /** short text for a key in the editor grid */
    fun displayLabel(key: EditableKey): String {
        if (key.isSpacer) return ""
        if (key.isSpecial) {
            // show the first label found inside the selector
            val inner = Regex("\"label\"\\s*:\\s*\"([^\"]+)\"").find(key.json.toString())?.groupValues?.get(1)
            return inner?.let { displayLabel(EditableKey.ofLabel(it)) + "*" } ?: "⚙"
        }
        return when (val l = key.label) {
            "shift" -> "⇧"
            "delete" -> "⌫"
            "space" -> "␣"
            "action", "enter" -> "↵"
            "symbol_alpha", "symbol" -> "!#1"
            "alpha" -> "ABC"
            "language_switch" -> "🌐"
            "emoji" -> "☺"
            "emoji_search" -> "🔍"
            "numpad" -> "123"
            "period" -> "."
            "comma" -> ","
            "zwnj" -> "ZWNJ"
            else -> if (l.startsWith("!icon/")) l.substringAfter("!icon/").substringBefore("|") else l
        }
    }
}
