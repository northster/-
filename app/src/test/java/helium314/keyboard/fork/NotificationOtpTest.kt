// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import helium314.keyboard.fork.clipboard.NotificationOtpExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationOtpTest {
    private fun code(text: String) = NotificationOtpExtractor.extract(text)

    @Test fun koreanBrackets() = assertEquals("482913", code("[Web발신] 인증번호 [482913]를 입력해주세요."))
    @Test fun koreanParticle() = assertEquals("123456", code("[카카오] 인증번호는 123456 입니다"))
    @Test fun koreanEnterOnly() = assertEquals("482913", code("[482913]를 입력해 주세요"))
    @Test fun koreanParens() = assertEquals("778899", code("본인확인 인증번호(778899)입력시 정상처리"))
    @Test fun google() = assertEquals("123456", code("G-123456 is your Google verification code."))
    @Test fun splitCode() = assertEquals("482913", code("Your OTP is 482 913"))
    @Test fun accountBeforeCode() = assertEquals("4279", code("OTP for account 2310990533 is 4279"))
    @Test fun zipIsNotCode() = assertNull(code("Your zip code: 12345"))
    @Test fun orderNumber() = assertNull(code("배송 예정 14:30, 주문번호 20240101"))
    @Test fun year() = assertNull(code("Your code is 2024"))
}
