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
    @Test fun googlePrefix() = assertEquals("123456", ClipPrefs.findCode("G-123456 is your Google verification code."))
    @Test fun notFromPhoneNumber() = assertNull(ClipPrefs.findCode("대표번호 02 123 4567"))
    @Test fun codeBesidePhone() = assertEquals("4567", ClipPrefs.findCode("문의 02 123 4567, 인증번호 4567"))

    private fun kinds(text: String) = ClipPrefs.findActions(text).map { "${it::class.simpleName}:${it.value}" }

    @Test fun smartCode() = assertEquals(listOf("Code:482913"), kinds("[Web발신] 인증번호 [482913]를 입력해주세요."))
    @Test fun smartLink() = assertEquals(listOf("Link:https://naver.com/a?b=1"), kinds("여기 https://naver.com/a?b=1."))
    @Test fun smartPhone() = assertEquals(listOf("Phone:010-1234-5678"), kinds("연락처 010-1234-5678"))
    @Test fun smartServiceNumber() = assertEquals(listOf("Phone:1588-1234"), kinds("고객센터 1588-1234"))
    @Test fun smartEmailNotLink() = assertEquals(listOf("Email:a.b@gmail.com"), kinds("메일 a.b@gmail.com"))
    @Test fun smartAll() = assertEquals(
        listOf("Code:7731", "Link:www.shop.kr/o", "Phone:010-1234-5678"),
        kinds("인증코드 7731 www.shop.kr/o 문의 010-1234-5678"))
    @Test fun smartNothing() = assertEquals(emptyList<String>(), kinds("그냥 복사한 문장"))
}
