// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import helium314.keyboard.fork.clipboard.ClipPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipCodeTest {
    @Test fun singleCode() = assertEquals("482913", ClipPrefs.findCode("[Web발신] 인증번호 [482913]를 입력해주세요."))
    @Test fun codeOnly() = assertEquals("1234", ClipPrefs.findCode("1234"))
    @Test fun prefersKeyword() = assertEquals("7731", ClipPrefs.findCode("2024년 주문 건 인증코드 7731"))
    @Test fun noPhoneNumbers() = assertNull(ClipPrefs.findCode("010-1234-5678"))
    @Test fun tooShort() = assertNull(ClipPrefs.findCode("abc 123"))
    @Test fun tooLong() = assertNull(ClipPrefs.findCode("123456789"))
    @Test fun empty() = assertNull(ClipPrefs.findCode(null))
}
