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
    /** look of the paste / smart chips: border on, [BORDER_DOTS] / [BORDER_DASHED] / [BORDER_SOLID], colors (unset = theme) */
    const val CHIP_BORDER = "fork_chip_border"
    const val CHIP_BORDER_STYLE = "fork_chip_border_style"
    const val BORDER_DOTS = "dots"
    const val BORDER_DASHED = "dashed"
    const val BORDER_SOLID = "solid"
    const val CHIP_BORDER_COLOR = "fork_chip_border_color"
    const val CHIP_BG_COLOR = "fork_chip_bg_color"
    const val CHIP_TEXT_COLOR = "fork_chip_text_color"
    const val CHIP_RADIUS = "fork_chip_radius"
    const val DEFAULT_CHIP_RADIUS = 15f
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

    /** slider steps: hours one by one up to 6, then 8-12 in 2 h steps, 18 h, then days up to a week, 2 weeks, 30 days, 0 = no limit */
    val retentionChoices = listOf(1, 2, 3, 4, 5, 6, 8, 10, 12, 18, 24, 48, 72, 96, 120, 144, 168, 336, 720, 0)
    /** slider steps: tens up to 100, then coarser, 0 = no limit */
    val maxItemChoices = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 150, 200, 300, 500, 1000, 0)

    fun retentionHours(prefs: SharedPreferences): Int {
        if (prefs.contains(RETENTION_HOURS)) return prefs.getInt(RETENTION_HOURS, DEFAULT_RETENTION_HOURS)
        // HeliBoard stored minutes, 121 = no limit. Keep "no limit", other values were minutes and get the new default.
        return if (prefs.getInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, 0) > 120) 0 else DEFAULT_RETENTION_HOURS
    }

    fun maxItems(prefs: SharedPreferences) = prefs.getInt(MAX_ITEMS, DEFAULT_MAX_ITEMS)
    fun columns(prefs: SharedPreferences) = prefs.getInt(COLUMNS, DEFAULT_COLUMNS).coerceIn(1, 4)

    /** the columns for a panel [widthDp] wide: the setting is for a phone (~400 dp), wider screens get as many more */
    fun columns(prefs: SharedPreferences, widthDp: Float) =
        (columns(prefs) * widthDp / 400f).toInt().coerceIn(columns(prefs), 8)
    fun previewLines(prefs: SharedPreferences) = prefs.getInt(PREVIEW_LINES, DEFAULT_PREVIEW_LINES).coerceIn(1, 12)
    fun screenshots(prefs: SharedPreferences) = prefs.getBoolean(SCREENSHOTS, DEFAULT_SCREENSHOTS)
    fun pasteChip(prefs: SharedPreferences) = prefs.getBoolean(PASTE_CHIP, DEFAULT_PASTE_CHIP)
    /** chip look; colors are null where the theme's (enter key / key) colors are used */
    class ChipStyle(val border: Boolean, val borderStyle: String, val borderColor: Int?, val background: Int?,
                    val text: Int?, val radiusDp: Float)

    fun chipStyle(prefs: SharedPreferences) = ChipStyle(
        border = prefs.getBoolean(CHIP_BORDER, true),
        borderStyle = prefs.getString(CHIP_BORDER_STYLE, BORDER_DOTS) ?: BORDER_DOTS,
        borderColor = if (prefs.contains(CHIP_BORDER_COLOR)) prefs.getInt(CHIP_BORDER_COLOR, 0) else null,
        background = if (prefs.contains(CHIP_BG_COLOR)) prefs.getInt(CHIP_BG_COLOR, 0) else null,
        text = if (prefs.contains(CHIP_TEXT_COLOR)) prefs.getInt(CHIP_TEXT_COLOR, 0) else null,
        radiusDp = prefs.getFloat(CHIP_RADIUS, DEFAULT_CHIP_RADIUS).coerceIn(0f, 40f),
    )

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
