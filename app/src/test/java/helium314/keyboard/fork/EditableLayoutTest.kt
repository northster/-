// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import helium314.keyboard.fork.settings.layout.EditableLayout
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The visual layout editor must write files the keyboard can read, and keep what it doesn't edit. */
class EditableLayoutTest {
    private fun asset(path: String) = File("src/main/assets/layouts/$path").readText()

    @Test fun jsonRoundTrip() {
        for (path in listOf("functional/functional_keys.json", "emoji_bottom/emoji_bottom_row.json")) {
            val rows = EditableLayout.parse(asset(path))
            val saved = EditableLayout.toJson(rows)
            // same keys as the original when parsed by the keyboard
            assertEquals(LayoutParser.parseJsonString(asset(path)).map { it.size },
                LayoutParser.parseJsonString(saved).map { it.size })
            assertEquals(rows.map { r -> r.map { it.json } }, EditableLayout.parse(saved).map { r -> r.map { it.json } })
        }
    }

    @Test fun simpleToJson() {
        val rows = EditableLayout.parse(asset("symbols/symbols.txt"))
        assertEquals(listOf(10, 10, 7), rows.map { it.size })
        val parsed = LayoutParser.parseJsonString(EditableLayout.toJson(rows))
        assertEquals(listOf(10, 10, 7), parsed.map { it.size })
    }

    @Test fun editKeepsOtherFields() {
        val key = EditableLayout.parse("""[[{ "label": "a", "code": 97, "labelFlags": 4 }]]""")[0][0]
        val edited = key.withLabel("b").withPopups(listOf("c", "d")).withWidth(0.15f)
        assertEquals("b", edited.label)
        assertEquals(listOf("c", "d"), edited.popups)
        assertEquals(0.15f, edited.width)
        assertEquals("97", edited.json["code"].toString())
        assertEquals("4", edited.json["labelFlags"].toString())
    }
}
