// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.clipboard

import android.content.SharedPreferences
import helium314.keyboard.latin.settings.Settings

/** fork: clipboard history settings added on top of HeliBoard's. */
object ClipPrefs {
    /** how long unpinned clips are kept, in hours, 0 = no limit (replaces HeliBoard's retention in minutes) */
    const val RETENTION_HOURS = "fork_clip_retention_hours"
    /** max number of unpinned clips, 0 = no limit */
    const val MAX_ITEMS = "fork_clip_max_items"
    const val COLUMNS = "fork_clip_columns"
    /** max text lines per card */
    const val PREVIEW_LINES = "fork_clip_preview_lines"
    /** add new screenshots to the history (needs the photo permission) */
    const val SCREENSHOTS = "fork_clip_screenshots"
    /** show the latest clip / a code found in it as chips on the dynamic toolbar */
    const val PASTE_CHIP = "fork_clip_paste_chip"
    /** actions for what the clip contains (open link, call, mail, paste the code) next to the paste chip */
    const val SMART_CHIPS = "fork_clip_smart_chips"
    /** open the toolbar right away when a copied text contains a verification code */
    const val CODE_AUTO_OPEN = "fork_clip_code_auto_open"
    /** until this time (ms) a test message with a code is offered as if it was just copied */
    const val CODE_TEST_UNTIL = "fork_clip_code_test_until"
    /** settings entry with a text field that shows what the smart chips find, nothing is stored */
    const val SMART_TESTER = "fork_clip_smart_tester"

    const val DEFAULT_RETENTION_HOURS = 24
    const val DEFAULT_MAX_ITEMS = 100
    const val DEFAULT_COLUMNS = 2
    const val DEFAULT_PREVIEW_LINES = 4
    const val DEFAULT_SCREENSHOTS = false
    const val DEFAULT_PASTE_CHIP = true
    const val DEFAULT_SMART_CHIPS = true
    const val DEFAULT_CODE_AUTO_OPEN = true

    val retentionChoices = listOf(1, 6, 24, 72, 168, 0)
    val maxItemChoices = listOf(20, 50, 100, 200, 500, 0)

    fun retentionHours(prefs: SharedPreferences): Int {
        if (prefs.contains(RETENTION_HOURS)) return prefs.getInt(RETENTION_HOURS, DEFAULT_RETENTION_HOURS)
        // HeliBoard stored minutes, 121 = no limit. Keep "no limit", other values were minutes and get the new default.
        return if (prefs.getInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, 0) > 120) 0 else DEFAULT_RETENTION_HOURS
    }

    fun maxItems(prefs: SharedPreferences) = prefs.getInt(MAX_ITEMS, DEFAULT_MAX_ITEMS)
    fun columns(prefs: SharedPreferences) = prefs.getInt(COLUMNS, DEFAULT_COLUMNS).coerceIn(1, 4)
    fun previewLines(prefs: SharedPreferences) = prefs.getInt(PREVIEW_LINES, DEFAULT_PREVIEW_LINES).coerceIn(1, 12)
    fun screenshots(prefs: SharedPreferences) = prefs.getBoolean(SCREENSHOTS, DEFAULT_SCREENSHOTS)
    fun pasteChip(prefs: SharedPreferences) = prefs.getBoolean(PASTE_CHIP, DEFAULT_PASTE_CHIP)
    fun smartChips(prefs: SharedPreferences) = prefs.getBoolean(SMART_CHIPS, DEFAULT_SMART_CHIPS)
    fun codeAutoOpen(prefs: SharedPreferences) = prefs.getBoolean(CODE_AUTO_OPEN, DEFAULT_CODE_AUTO_OPEN)

    // Google writes codes as G-123456, the code is the digits
    private val otpRegex = Regex("""(?:(?<=\bG-)|(?<![\d-]))(\d{4,8})(?![\d-])""")

    /** A verification code in [text]: a lone 4-8 digit number, preferring one near a code keyword, not part of a phone number. */
    fun findCode(text: String?): String? {
        if (text.isNullOrBlank() || text.length > 2000) return null
        val phones = phoneRegex.findAll(text).map { it.range }.toList()
        val matches = otpRegex.findAll(text).filter { m -> phones.none { m.range.first in it } }.toList()
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches[0].groupValues[1]
        val keyword = Regex("(?i)(인증|코드|번호|code|otp|pin|verification)")
        return (matches.firstOrNull { m ->
            keyword.containsMatchIn(text.substring((m.range.first - 30).coerceAtLeast(0), m.range.first))
        } ?: matches.first()).groupValues[1]
    }

    /** something in a clip the keyboard can act on, shown as a chip right of the clip */
    sealed class SmartAction(val value: String) {
        /** verification code, pasted */
        class Code(value: String) : SmartAction(value)
        /** web address, opened */
        class Link(value: String) : SmartAction(value)
        /** phone number, opened in the dialer */
        class Phone(value: String) : SmartAction(value)
        /** mail address, opened in a mail app */
        class Email(value: String) : SmartAction(value)
    }

    private val linkRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")
    private val emailRegex = Regex("""(?<![\w.+-])[\w.+-]+@[\w-]+(?:\.[\w-]+)*\.[a-zA-Z]{2,}""")
    // 010-1234-5678, 02 123 4567, 01012345678, +82 10-1234-5678, 1588-1234 (service numbers need the dash).
    private val phoneRegex = Regex("""(?<![\d-])(?:(?:\+\d{1,3}[- ]?\d{1,2}|0\d{1,2})[- .]?\d{3,4}[- .]?\d{4}|1\d{3}-\d{4})(?![\d-])""")

    /** What [text] contains: a code first (it is what people copy a message for), then link, phone number, mail. */
    fun findActions(text: String?): List<SmartAction> {
        if (text.isNullOrBlank() || text.length > 2000) return emptyList()
        val actions = mutableListOf<SmartAction>()
        findCode(text)?.let { actions.add(SmartAction.Code(it)) }
        val email = emailRegex.find(text)?.value
        // a link that is part of a mail address is not a link
        linkRegex.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
            ?.takeIf { email == null || !email.contains(it.removePrefix("www.")) }
            ?.let { actions.add(SmartAction.Link(it)) }
        phoneRegex.find(text)?.value?.let { actions.add(SmartAction.Phone(it)) }
        email?.let { actions.add(SmartAction.Email(it)) }
        return actions
    }
}
