// SPDX-License-Identifier: GPL-3.0-only
// Sources and parsing follow WM Keyboard's CurrencyClient (github.com/wasi-master/wmkeyboard, core/tools/CurrencyClient.kt),
// MIT License, Copyright (c) 2026 Wasi Master. fork: plain HttpURLConnection + org.json, rates cached in preferences.
package helium314.keyboard.fork.smart

import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Exchange rates for the currency chips, as units per USD, from keyless APIs: Coinbase (live market rates, updated
 * every minute), then open.er-api.com (daily) and api.frankfurter.dev (European Central Bank, working days) as
 * fallbacks. Fetched in the background at most every [MAX_AGE_MILLIS], only while currency chips are on; the chips
 * use the cached table and stay off without one.
 */
object CurrencyRates {
    private const val TAG = "CurrencyRates"
    private const val PREF_TABLE = "fork_currency_rates"
    private const val PREF_TIME = "fork_currency_rates_time"
    private const val PREF_SOURCE = "fork_currency_rates_source"
    private const val MAX_AGE_MILLIS = 60 * 60 * 1000L
    private const val RETRY_MILLIS = 10 * 60 * 1000L

    @Volatile private var cached: Map<String, Double>? = null
    @Volatile private var fetching = false
    @Volatile private var lastAttempt = 0L

    /** the cached table, or null when none was fetched yet */
    fun rates(context: Context): Map<String, Double>? {
        cached?.let { return it }
        val json = context.prefs().getString(PREF_TABLE, null) ?: return null
        return runCatching { numbers(JSONObject(json)) }.getOrNull()?.takeIf { it.isNotEmpty() }?.also { cached = it }
    }

    /** when the cached table was fetched (ms), 0 when never */
    fun fetchedAt(context: Context): Long = context.prefs().getLong(PREF_TIME, 0)

    /** where the cached table came from */
    fun source(context: Context): String = context.prefs().getString(PREF_SOURCE, null).orEmpty()

    /** fetch in the background when the table is missing or old; cheap to call on every keyboard start */
    fun refreshIfOld(context: Context) {
        val prefs = context.prefs()
        val now = System.currentTimeMillis()
        if (fetching || now - lastAttempt < RETRY_MILLIS) return
        if (now - prefs.getLong(PREF_TIME, 0) < MAX_AGE_MILLIS && rates(context) != null) return
        fetching = true
        lastAttempt = now
        val app = context.applicationContext
        Thread {
            try {
                val (source, table) = runCatching { "Coinbase" to coinbase() }
                    .recoverCatching { Log.w(TAG, "coinbase failed", it); "ExchangeRate-API" to erApi() }
                    .getOrElse { Log.w(TAG, "er-api failed", it); "Frankfurter (ECB)" to frankfurter() }
                app.prefs().edit {
                    putString(PREF_TABLE, JSONObject(table as Map<*, *>).toString())
                    putLong(PREF_TIME, System.currentTimeMillis())
                    putString(PREF_SOURCE, source)
                }
                cached = table
                Log.i(TAG, "rates updated, ${table.size} currencies")
            } catch (e: Exception) {
                Log.w(TAG, "can't fetch rates", e)
            } finally {
                fetching = false
            }
        }.start()
    }

    /** live rates; values are strings, which optDouble reads */
    private fun coinbase(): Map<String, Double> {
        val rates = JSONObject(get("https://api.coinbase.com/v2/exchange-rates?currency=USD")).getJSONObject("data").getJSONObject("rates")
        val table = numbers(rates)
        check(table.containsKey("KRW") && table.containsKey("EUR")) { "incomplete table" }
        return table + ("USD" to 1.0)
    }

    private fun erApi(): Map<String, Double> {
        val root = JSONObject(get("https://open.er-api.com/v6/latest/USD"))
        check(root.optString("result") == "success") { "rate API error" }
        return numbers(root.getJSONObject("rates")) + ("USD" to 1.0)
    }

    private fun frankfurter(): Map<String, Double> =
        numbers(JSONObject(get("https://api.frankfurter.dev/v1/latest?from=USD")).getJSONObject("rates")) + ("USD" to 1.0)

    /** a flat code → rate object, dropping anything that is not a usable number */
    private fun numbers(table: JSONObject): Map<String, Double> = buildMap {
        for (code in table.keys()) {
            val rate = table.optDouble(code, Double.NaN)
            if (rate.isFinite() && rate > 0.0) put(code, rate)
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
