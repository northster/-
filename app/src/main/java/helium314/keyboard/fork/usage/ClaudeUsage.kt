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
    /** Cloudflare blocked the plain requests: ask through a WebView */
    private const val PREF_VIA_WEB = "fork_claude_usage_via_web"
    private const val ORGS_URL = "https://claude.ai/api/organizations"
    private fun usageUrl(org: String) = "https://claude.ai/api/organizations/$org/usage"
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
        prefs.edit { remove(PREF_ORG); remove(PREF_DATA); remove(PREF_STATUS); remove(PREF_VIA_WEB) }
        if (key.isBlank()) { prefs.edit { remove(PREF_SESSION_KEY) }; return true }
        val encrypted = KeyCipher.encrypt(key.trim().removePrefix("sessionKey=").trimEnd(';').trim()) ?: return false
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
        fun done(result: Result<Usage>) {
            fetching = false
            result.onFailure { Log.w(TAG, "can't read the usage", it); app.prefs().edit { putString(PREF_STATUS, it.message ?: it.javaClass.simpleName) } }
            onDone(result.getOrNull())
        }
        if (prefs.getBoolean(PREF_VIA_WEB, false)) {
            fetchWeb(app, key, ::done)
            return
        }
        Thread {
            val result = runCatching { fetch(app.prefs(), key) }
            handler.post {
                if (result.exceptionOrNull() is CloudflareBlocked) {
                    // claude.ai's Cloudflare wants a browser: ask through a WebView from now on
                    app.prefs().edit { putBoolean(PREF_VIA_WEB, true) }
                    fetchWeb(app, key, ::done)
                } else done(result)
            }
        }.start()
    }

    private fun fetch(prefs: SharedPreferences, key: String): Usage {
        val org = prefs.getString(PREF_ORG, null) ?: findOrg(get(ORGS_URL, key)).also { prefs.edit { putString(PREF_ORG, it) } }
        val json = runCatching { get(usageUrl(org), key) }
            .getOrElse {
                // the organization may have changed: look it up again next time
                prefs.edit { remove(PREF_ORG) }
                throw it
            }
        return store(prefs, json)
    }

    /** the same through a WebView (a real browser, which Cloudflare lets through), on the main thread */
    private fun fetchWeb(context: Context, key: String, onDone: (Result<Usage>) -> Unit) {
        val prefs = context.prefs()
        val web = runCatching { WebGet(context, key) }.getOrElse { onDone(Result.failure(it)); return }
        fun finish(result: Result<Usage>) { web.destroy(); onDone(result) }
        fun usage(org: String) = web.get(usageUrl(org)) { r ->
            if (r.isFailure) prefs.edit { remove(PREF_ORG) }
            finish(r.mapCatching { store(prefs, it) })
        }
        val org = prefs.getString(PREF_ORG, null)
        if (org != null) usage(org)
        else web.get(ORGS_URL) { r ->
            r.mapCatching { findOrg(it) }.fold(
                { prefs.edit { putString(PREF_ORG, it) }; usage(it) },
                { finish(Result.failure(it)) },
            )
        }
    }

    /** reads the usage answer and keeps it */
    private fun store(prefs: SharedPreferences, text: String): Usage {
        val json = JSONObject(checkError(text))
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

    /** claude.ai's error answers ({"type":"error","error":{...}}) as exceptions */
    private fun checkError(text: String): String {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return text
        if (o.optString("type") != "error") return text
        val e = o.optJSONObject("error")
        val type = e?.optString("type").orEmpty()
        if (type == "permission_error" || type == "authentication_error") error("sessionKey not accepted ($type)")
        error(e?.optString("message")?.ifEmpty { null } ?: type.ifEmpty { "error" })
    }

    /** the account's organization with the chat plan (the first one otherwise) */
    private fun findOrg(text: String): String {
        val orgs = JSONArray(checkError(text))
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
            if (code != 200) {
                val body = runCatching { c.errorStream?.bufferedReader()?.use { it.readText().take(4000) } }.getOrNull().orEmpty()
                // Cloudflare's browser check, not claude.ai saying no
                if (c.getHeaderField("cf-mitigated") != null || "Just a moment" in body || "challenge-platform" in body)
                    throw CloudflareBlocked()
                checkError(body)
                if (code == 401 || code == 403) error("sessionKey not accepted (HTTP $code)")
                error("HTTP $code")
            }
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

    private class CloudflareBlocked : Exception("Cloudflare blocked the request")

    /**
     * GETs claude.ai pages in a hidden WebView with the session cookie, one after the other: Cloudflare lets a real
     * browser through (it solves the check page by itself, the answer comes with the next page load)
     */
    private class WebGet(context: Context, key: String) {
        private val web = android.webkit.WebView(context)
        private var callback: ((Result<String>) -> Unit)? = null
        private val timeout = Runnable { deliver(Result.failure(IllegalStateException("Cloudflare check did not finish"))) }

        init {
            val cookies = android.webkit.CookieManager.getInstance()
            cookies.setAcceptCookie(true)
            cookies.setCookie("https://claude.ai", "sessionKey=$key; path=/; secure")
            cookies.flush()
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView, url: String) { read() }
            }
        }

        fun get(url: String, onDone: (Result<String>) -> Unit) {
            callback = onDone
            handler.postDelayed(timeout, 25_000)
            web.loadUrl(url)
        }

        private fun read() {
            if (callback == null) return
            // JSON is shown in a <pre> (newer Chrome versions add a "pretty print" box around it)
            web.evaluateJavascript("(function(){var p=document.querySelector('pre');return (p||document.body||{}).innerText||''})()") { raw ->
                val text = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("").trim()
                // anything else is the check page: wait for the next load
                if (text.startsWith("{") || text.startsWith("[")) deliver(Result.success(text))
            }
        }

        private fun deliver(result: Result<String>) {
            val cb = callback ?: return
            callback = null
            handler.removeCallbacks(timeout)
            cb(result)
        }

        fun destroy() {
            handler.removeCallbacks(timeout)
            callback = null
            web.stopLoading()
            web.destroy()
        }
    }
}
