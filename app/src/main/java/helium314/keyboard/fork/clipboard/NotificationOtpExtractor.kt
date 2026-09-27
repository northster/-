// SPDX-License-Identifier: GPL-3.0-only
// Ported from WM Keyboard (github.com/wasi-master/wmkeyboard, core/otp/NotificationOtp.kt,
// core/clipboard/ClipEntities.kt), MIT License, Copyright (c) 2026 Wasi Master.
// fork: Korean keyword patterns added ("인증번호 [482913]", "[482913]를 입력"); the fallback is ClipPrefs.findCode
// limited to codes next to a keyword.
package helium314.keyboard.fork.clipboard

/**
 * Finds the one-time code in a notification's text, or decides there is none.
 *
 * A notification chip is pushier than a clipboard chip (it appears unbidden), so every pattern is anchored to an
 * explicit code word. There is no "any six digits" tier: a delivery slot, an order number and a flight time all
 * arrive as notifications too. The anchored patterns run in precision order, so a message with several numbers
 * ("OTP for account 2310990533 is 4279") yields the code and not the account.
 *
 * Plain Kotlin (no Android), so it is unit tested on the JVM.
 */
object NotificationOtpExtractor {

    /** Words that promise the number beside them is a login code. */
    private const val KEYWORDS =
        "(?:otp|one[- ]?time\\s+(?:password|passcode|code)|passcode|pass\\s+code|" +
            "(?:verification|security|authentication|authorization|access|login|" +
            "sign[- ]?in|confirmation|activation|2fa)\\s+(?:code|pin)|" +
            "verification|code|pin)"

    /** Korean code words: 인증번호, 인증 코드, 확인번호, 승인번호, 보안코드, 본인확인 번호 ... */
    private const val KO_KEYWORDS = "(?:인증\\s*(?:번호|코드)|확인\\s*(?:번호|코드)|승인\\s*번호|보안\\s*(?:번호|코드)|" +
        "본인\\s*확인\\s*번호|OTP|otp)"

    /** `123456`, `123 456`, `123-456`: separators stripped before use. */
    private const val CODE = "(\\d{3}[- ]?\\d{3}|\\d{4,8})"

    /** Google's `G-123456` style branding prefix, not part of the code. */
    private const val BRAND_PREFIX = "(?:[A-Za-z]-)?"

    /** brackets Korean senders put around the code: [482913], (482913), 【482913】 */
    private const val OPEN = "[\\[(【<]?\\s*"
    private const val CLOSE = "\\s*[\\])】>]?"

    private val ANCHORED = listOf(
        // "인증번호 [482913]", "인증번호는 482913", "인증번호: 482913", "OTP 482913"
        Regex("$KO_KEYWORDS\\s*(?:는|은|:|-|=)?\\s*$OPEN$BRAND_PREFIX$CODE(?!\\d)"),
        // "[482913]를 입력", "482913 을 입력해 주세요", "482913은 인증번호"
        Regex("$OPEN$BRAND_PREFIX(?<!\\d)$CODE$CLOSE\\s*(?:를|을)\\s*입력"),
        Regex("(?<!\\d)$CODE$CLOSE\\s*(?:는|은|이|가)?\\s*(?:귀하의\\s*)?$KO_KEYWORDS"),
        // "482913 is your Amazon OTP", "4279 is the verification code for …"
        Regex(
            "\\b$BRAND_PREFIX$CODE\\s+is\\s+(?:your|the)?\\s*(?:[\\w.-]+\\s+)?$KEYWORDS",
            RegexOption.IGNORE_CASE,
        ),
        // "Your OTP is 482913", "code: 482913", "PIN - 4829"
        Regex(
            "$KEYWORDS\\s*(?:is|:|-|=)\\s*$BRAND_PREFIX$CODE\\b",
            RegexOption.IGNORE_CASE,
        ),
        // "OTP for account 2310990533 is 4279": keyword, a short stretch of anything-but-sentence-end, "is", the code
        Regex(
            "$KEYWORDS\\b[^.!\\n]{0,40}?\\bis\\s+$BRAND_PREFIX$CODE\\b",
            RegexOption.IGNORE_CASE,
        ),
        // "use 482913 to sign in", "enter 4829 to verify"
        Regex(
            "\\b(?:use|enter|type)\\s+(?:code\\s+)?$BRAND_PREFIX$CODE\\b",
            RegexOption.IGNORE_CASE,
        ),
    )

    /** the word before the keyword turns it into something else: "zip code 12345" stays a ZIP */
    private val KEYWORD_DENY = setOf(
        "zip", "postal", "post", "area", "country", "dial", "colour", "color",
        "hex", "sort", "swift", "ifsc", "iban", "bar", "qr", "source", "error",
    )

    /** keyword near a number, for the fallback */
    private val NEAR_KEYWORD = Regex("(?i)(인증|확인\\s*번호|승인\\s*번호|보안\\s*코드|code|otp|pin|verification|passcode)")

    fun extract(text: String): String? {
        if (text.isBlank()) return null
        for (pattern in ANCHORED) {
            for (match in pattern.findAll(text)) {
                if (keywordDenied(text, match.range.first)) continue
                val code = clean(match.groupValues[1])
                if (isPlausibleCode(code)) return code
            }
        }
        // recall net: a lone code, but only with a code word close by
        val code = ClipPrefs.findCode(text) ?: return null
        val at = text.indexOf(code)
        if (at < 0) return null
        val from = (at - 40).coerceAtLeast(0)
        val around = text.substring(from, (at + code.length + 20).coerceAtMost(text.length))
        val keywordNear = NEAR_KEYWORD.findAll(around).any { !keywordDenied(text, from + it.range.first) }
        return code.takeIf { keywordNear && isPlausibleCode(it) }
    }

    private fun clean(raw: String): String = raw.replace(Regex("[- ]"), "")

    private fun isPlausibleCode(code: String): Boolean {
        if (code.length !in 4..8) return false
        // a bare year reads as a code to a regex and as a date to a person
        if (code.length == 4 && code.toInt() in 1900..2099) return false
        return true
    }

    private fun keywordDenied(text: String, matchStart: Int): Boolean {
        val word = text.take(matchStart).trimEnd().takeLastWhile { it.isLetter() }
        return word.isNotEmpty() && word.lowercase() in KEYWORD_DENY
    }
}
