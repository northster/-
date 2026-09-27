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
    /** how a waiting paste chip is shown while the toolbar is collapsed */
    const val CHIP_HINT = "fork_clip_chip_hint"
    const val HINT_GLOW = "glow"
    const val HINT_DOT = "dot"

    const val DEFAULT_RETENTION_HOURS = 24
    const val DEFAULT_MAX_ITEMS = 100
    const val DEFAULT_COLUMNS = 2
    const val DEFAULT_PREVIEW_LINES = 4
    const val DEFAULT_SCREENSHOTS = false
    const val DEFAULT_PASTE_CHIP = true

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
    fun chipHint(prefs: SharedPreferences) = prefs.getString(CHIP_HINT, HINT_GLOW) ?: HINT_GLOW

    private val otpRegex = Regex("""(?<![\d-])(\d{4,8})(?![\d-])""")

    /** A verification code in [text]: a lone 4-8 digit number, preferring one near a code keyword. */
    fun findCode(text: String?): String? {
        if (text.isNullOrBlank() || text.length > 500) return null
        val matches = otpRegex.findAll(text).map { it.groupValues[1] }.toList()
        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches[0]
        val keyword = Regex("(?i)(인증|코드|번호|code|otp|pin|verification)")
        return matches.firstOrNull { m ->
            val i = text.indexOf(m)
            keyword.containsMatchIn(text.substring((i - 30).coerceAtLeast(0), i))
        } ?: matches.first()
    }
}
