// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import helium314.keyboard.fork.widget.WikiFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikiFactsTest {
    @Test fun hook() = assertEquals("the Eiffel Tower grows in summer?",
        WikiFacts.clean("* ... that the '''[[Eiffel Tower]]''' grows in summer?"))
    @Test fun pipedLink() = assertEquals("cats sleep most of the day?",
        WikiFacts.clean("* ... that [[Cat|cats]] sleep most of the day?"))
    @Test fun pictured() = assertEquals("this bird can fly backwards?",
        WikiFacts.clean("* ... that this [[bird]] (pictured) can fly backwards?"))
    @Test fun template() = assertNull(WikiFacts.clean("* ... that it is {{convert|5|cm}} long?"))
    @Test fun notHook() = assertNull(WikiFacts.clean("== March 2019 =="))
    @Test fun boldTitle() = assertEquals("Eiffel Tower",
        WikiFacts.title("* ... that [[Paris|Paris's]] '''[[Eiffel Tower|tower]]''' grows in summer?"))
    @Test fun firstLinkTitle() = assertEquals("Cat", WikiFacts.title("* ... that [[Cat|cats]] sleep a lot?"))
}
