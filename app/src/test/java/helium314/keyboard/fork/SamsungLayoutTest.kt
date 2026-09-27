// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the Samsung style default layouts parse and have the expected shape. */
class SamsungLayoutTest {
    private fun asset(path: String) = File("src/main/assets/layouts/$path").readText()

    @Test fun functionalKeysParse() {
        val text = asset("functional/functional_keys.json")
        // built-in layouts are only parsed as json if they start with '['
        assertTrue(text.trimStart().startsWith("["))
        val rows = LayoutParser.parseJsonString(text)
        assertEquals(2, rows.size)
        assertEquals(3, rows[0].size) // shift, placeholder, delete
        assertEquals(7, rows[1].size) // !#1, 한/영, emoji (symbols), @ or / (variation), space, period, action
    }

    @Test fun symbolsShape() {
        // first row is replaced by the number row (its labels become popups)
        val rows = LayoutParser.parseSimpleString(asset("symbols/symbols.txt"))
        assertEquals(listOf(10, 10, 7), rows.map { it.size })
        assertEquals(listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"), rows[1].map { it.label })
        assertEquals(listOf("-", "'", "\"", ":", ";", ",", "?"), rows[2].map { it.label })
    }

    @Test fun moreSymbolsShape() {
        val rows = LayoutParser.parseSimpleString(asset("more_symbols/symbols_shifted.txt"))
        assertEquals(listOf(10, 10, 7), rows.map { it.size })
        assertEquals(listOf("+", "×", "÷", "=", "<", ">", "{", "}", "[", "]"), rows[0].map { it.label })
        assertEquals(listOf("€", "£", "¥", "₩", "/", "~", "`", "¤", "°", "♡"), rows[1].map { it.label })
        assertEquals(listOf("_", "\\", "|", "《", "》", "¡", "¿"), rows[2].map { it.label })
    }

    @Test fun emojiBottomRowsParse() {
        // comment lines must start at column 0, otherwise the json is invalid and the file shows up as keys
        for (name in listOf("emoji_bottom_row.json", "emoji_bottom_row_with_action.json")) {
            val text = asset("emoji_bottom/$name")
            assertTrue(text.trimStart().startsWith("["))
            val rows = LayoutParser.parseJsonString(text)
            assertEquals(1, rows.size)
            assertTrue(rows[0].size >= 3) // spacer, space, delete (, action)
        }
    }
}
