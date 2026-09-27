// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import helium314.keyboard.fork.smart.SmartSuggest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartSuggestTest {
    private val ctx = SmartSuggest.Context(rates = mapOf("USD" to 1.0, "KRW" to 1400.0, "JPY" to 150.0, "EUR" to 0.9))
    private fun hit(text: String) = SmartSuggest.detect(text, ctx)

    @Test fun calc() {
        val h = hit("값은 12*4")!!
        assertEquals("48", h.result)
        assertEquals(4, h.replaceSpan)
    }
    @Test fun calcWithEquals() {
        val h = hit("12*4=")!!
        assertEquals("48", h.result)
        assertEquals(0, h.replaceSpan)
    }
    @Test fun phoneNumberIsNotSum() = assertNull(hit("555-1234"))
    @Test fun paddedDateIsNotSum() = assertNull(hit("12/04"))
    @Test fun plainText() = assertNull(hit("안녕하세요"))

    @Test fun dollarsToWon() = assertEquals("14,000원", hit("10달러")!!.result)
    @Test fun symbolDollars() = assertEquals("14,000원", hit("$10")!!.result)
    @Test fun manWonToDollars() = assertEquals("$35.71", hit("5만원")!!.result)
    @Test fun wonSpan() = assertEquals(3, hit("가격 5만원")!!.replaceSpan)
    @Test fun noRatesNoCurrency() = assertNull(SmartSuggest.detect("10달러", SmartSuggest.Context()))

    @Test fun pyeong() = assertEquals("99.1736 m²", hit("30평")!!.result)
    @Test fun feet() = assertEquals("0.3048 m", hit("1 ft")!!.result)
    @Test fun spacedInchIsProse() = assertNull(hit("5 in"))
}
