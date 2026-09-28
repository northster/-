// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.usage

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import helium314.keyboard.fork.slate.KeyCipher
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone

/**
 * fork: the Claude plan's usage limits for the toolbar: the 5-hour session limit and the weekly limit, how much of
 * each is used and when it resets.
 *
 * There is no public API for this. claude.ai shows the same numbers on its usage page, from
 * `claude.ai/api/organizations/{id}/usage` (`five_hour` / `seven_day`: `utilization` in percent, `resets_at`); the
 * keyboard reads that with the account's `sessionKey` cookie, entered once in the toolbar settings and stored
 * encrypted like the other keys. It is only ever sent to claude.ai. Being unofficial, it can stop working when
 * claude.ai changes; the indicator then shows empty rows and the settings say why.
 */
object ClaudeUsage {
    const val PREF_SESSION_KEY = "fork_claude_session_key"
    private const val PREF_ORG = "fork_claude_org"
    private const val PREF_DATA = "fork_claude_usage_data"
    private const val PREF_STATUS = "fork_claude_usage_status"
    private const val MAX_AGE_MILLIS = 3 * 60 * 1000L
    private const val TAG = "ClaudeUsage"

    /** one limit: [used] 0..1, [resetsAt] ms (0 = unknown) */
    data class Limit(val used: Float, val resetsAt: Long)
    data class Usage(val session: Limit?, val week: Limit?, val time: Long)

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var fetching = false
    @Volatile private var lastAttempt = 0L

    fun hasKey(prefs: SharedPreferences) = key(prefs) != null

    private fun key(prefs: SharedPreferences): String? =
        prefs.getString(PREF_SESSION_KEY, null)?.let { KeyCipher.decrypt(it) }?.takeIf { it.isNotBlank() }

    /** @return false if it could not be stored encrypted; blank removes it */
    fun setKey(prefs: SharedPreferences, key: String): Boolean {
        prefs.edit { remove(PREF_ORG); remove(PREF_DATA); remove(PREF_STATUS) }
        if (key.isBlank()) { prefs.edit { remove(PREF_SESSION_KEY) }; return true }
        val encrypted = KeyCipher.encrypt(key.trim().removePrefix("sessionKey=")) ?: return false
        prefs.edit { putString(PREF_SESSION_KEY, encrypted) }
        lastAttempt = 0
        return true
    }

    /** the last numbers fetched, null if none */
    fun cached(prefs: SharedPreferences): Usage? {
        val o = runCatching { JSONObject(prefs.getString(PREF_DATA, null) ?: return null) }.getOrNull() ?: return null
        fun limit(name: String) = o.optJSONObject(name)?.let { Limit(it.optDouble("used").toFloat(), it.optLong("resets")) }
        return Usage(limit("session"), limit("week"), o.optLong("time"))
    }

    /** what happened on the last try, for the settings (empty = fine) */
    fun status(prefs: SharedPreferences): String = prefs.getString(PREF_STATUS, null).orEmpty()

    /** fetch in the background if the numbers are older than a few minutes; [onDone] on the main thread with new ones */
    fun refreshIfOld(context: Context, force: Boolean = false, onDone: (Usage?) -> Unit) {
        val prefs = context.prefs()
        val key = key(prefs) ?: return
        val now = System.currentTimeMillis()
        if (fetching) return
        if (!force && (now - (cached(prefs)?.time ?: 0) < MAX_AGE_MILLIS || now - lastAttempt < 60_000)) return
        fetching = true
        lastAttempt = now
        val app = context.applicationContext
        Thread {
            val result = runCatching { fetch(app.prefs(), key) }
            fetching = false
            result.onFailure { Log.w(TAG, "can't read the usage", it); app.prefs().edit { putString(PREF_STATUS, it.message ?: it.javaClass.simpleName) } }
            val usage = result.getOrNull()
            handler.post { onDone(usage) }
        }.start()
    }

    private fun fetch(prefs: SharedPreferences, key: String): Usage {
        val org = prefs.getString(PREF_ORG, null) ?: findOrg(key).also { prefs.edit { putString(PREF_ORG, it) } }
        val json = runCatching { JSONObject(get("https://claude.ai/api/organizations/$org/usage", key)) }
            .getOrElse {
                // the organization may have changed: look it up again next time
                prefs.edit { remove(PREF_ORG) }
                throw it
            }
        fun limit(name: String) = json.optJSONObject(name)?.let {
            Limit((it.optDouble("utilization", 0.0) / 100).toFloat().coerceIn(0f, 1f), parseTime(it.optString("resets_at")))
        }
        val usage = Usage(limit("five_hour"), limit("seven_day"), System.currentTimeMillis())
        val stored = JSONObject().put("time", usage.time)
        usage.session?.let { stored.put("session", JSONObject().put("used", it.used.toDouble()).put("resets", it.resetsAt)) }
        usage.week?.let { stored.put("week", JSONObject().put("used", it.used.toDouble()).put("resets", it.resetsAt)) }
        prefs.edit { putString(PREF_DATA, stored.toString()); putString(PREF_STATUS, "") }
        return usage
    }

    /** the account's organization with the chat plan (the first one otherwise) */
    private fun findOrg(key: String): String {
        val orgs = JSONArray(get("https://claude.ai/api/organizations", key))
        var first: String? = null
        for (i in 0 until orgs.length()) {
            val o = orgs.optJSONObject(i) ?: continue
            val uuid = o.optString("uuid").ifEmpty { continue }
            if (first == null) first = uuid
            val caps = o.optJSONArray("capabilities")
            if (caps != null && (0 until caps.length()).any { caps.optString(it) == "chat" }) return uuid
        }
        return first ?: error("no organization")
    }

    private fun get(url: String, key: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Cookie", "sessionKey=$key")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
        try {
            val code = c.responseCode
            if (code == 401 || code == 403) error("sessionKey not accepted (HTTP $code)")
            check(code == 200) { "HTTP $code" }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** "2026-10-03T08:00:00.123456+00:00" or "...Z" -> ms, 0 if it can't be read (no java.time before Android 8) */
    fun parseTime(s: String): Long {
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|([+-])(\d{2}):?(\d{2}))?""").find(s) ?: return 0
        val g = m.groupValues
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        var time = cal.timeInMillis
        if (g[8].isNotEmpty()) {
            val offset = (g[9].toInt() * 60 + g[10].toInt()) * 60_000L
            time -= if (g[8] == "+") offset else -offset
        }
        return time
    }

    /** time left until [resetsAt]: "45m", "3h", "2d" (24 hours and more in days) */
    fun remaining(resetsAt: Long, now: Long = System.currentTimeMillis()): String {
        if (resetsAt <= 0) return "–"
        val minutes = ((resetsAt - now) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 60 -> "${minutes}m"
            minutes < 24 * 60 -> "${minutes / 60}h"
            else -> "${minutes / (24 * 60)}d"
        }
    }
}
