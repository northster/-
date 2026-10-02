// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import helium314.keyboard.fork.gif.GifClient
import org.junit.Assert.assertEquals
import org.junit.Test

class GifAutoQueryTest {
    @Test fun empty() = assertEquals("", GifClient.autoQuery("   "))
    @Test fun lastTwoWords() = assertEquals("고양이 귀여워", GifClient.autoQuery("오늘 본 고양이가 귀여워"))
    @Test fun particle() = assertEquals("피자", GifClient.autoQuery("피자를"))
    @Test fun haVerb() = assertEquals("생일 축하", GifClient.autoQuery("생일 축하해요!!"))
    @Test fun lastSentenceOnly() = assertEquals("good morning", GifClient.autoQuery("Hi there. Good morning").lowercase())
    @Test fun english() = assertEquals("so tired", GifClient.autoQuery("i am so tired"))
}
