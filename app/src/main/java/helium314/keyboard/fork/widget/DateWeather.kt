// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.widget

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * fork: today's date and the weather, for the spacebar (Settings > popup widgets > spacebar: date & weather), set in a
 * serif face. The weather is Open-Meteo's (free, no key): the city written in the settings is looked up once, then the
 * current weather there is fetched at most every [REFRESH_MS]. Without a city only the date shows.
 */
object DateWeather {
    /** the city the weather is for, written freely ("서울", "Busan") */
    const val PREF_CITY = "fork_weather_city"
    private const val PREF_PLACE = "fork_weather_place" // "city|lat|lon" the city was found at
    private const val PREF_NOW = "fork_weather_now" // "time|temperature|weather code"
    private const val REFRESH_MS = 30 * 60 * 1000L
    private const val MAX_AGE_MS = 4 * 60 * 60 * 1000L

    @Volatile private var fetching = false
    private val main = Handler(Looper.getMainLooper())

    /** e.g. "11월 6일 (목) · 맑음 14°"; starts fetching the weather when it is old, [onUpdate] once it has come */
    fun text(context: Context, onUpdate: () -> Unit): String {
        val prefs = context.prefs()
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMdEEE")
        val date = java.text.SimpleDateFormat(pattern, locale).format(java.util.Date())
        val city = prefs.getString(PREF_CITY, null)?.trim().orEmpty()
        if (city.isEmpty()) return date
        // the city was changed: the weather of the old one is not shown
        if (prefs.getString(PREF_PLACE, null)?.substringBefore('|') != city) {
            refresh(context, onUpdate)
            return date
        }
        val now = prefs.getString(PREF_NOW, null)?.split('|')
        val time = now?.getOrNull(0)?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - time > REFRESH_MS) refresh(context, onUpdate)
        // shown up to 4 hours old (no network meanwhile), not older
        if (now == null || System.currentTimeMillis() - time > MAX_AGE_MS) return date
        val temp = now.getOrNull(1)?.toDoubleOrNull() ?: return date
        val code = now.getOrNull(2)?.toIntOrNull() ?: return date
        return "$date · ${describe(code, locale.language == "ko")} ${Math.round(temp)}°"
    }

    /** fetches the weather now (in the background) */
    fun refresh(context: Context, onUpdate: () -> Unit) {
        if (fetching) return
        val prefs = context.prefs()
        val city = prefs.getString(PREF_CITY, null)?.trim().orEmpty()
        if (city.isEmpty()) return
        fetching = true
        Thread {
            try {
                val place = place(prefs, city) ?: return@Thread
                val json = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=${place.first}" +
                    "&longitude=${place.second}&current=temperature_2m,weather_code"))
                val current = json.getJSONObject("current")
                prefs.edit {
                    putString(PREF_NOW, "${System.currentTimeMillis()}|${current.getDouble("temperature_2m")}|" +
                        current.getInt("weather_code"))
                }
                main.post(onUpdate)
            } catch (_: Exception) {
                // no network or no such city: the date alone, tried again next time
            } finally {
                fetching = false
            }
        }.start()
    }

    /** where [city] is, looked up once (and again when it is changed) */
    private fun place(prefs: SharedPreferences, city: String): Pair<Double, Double>? {
        prefs.getString(PREF_PLACE, null)?.split('|')?.let {
            if (it.size == 3 && it[0] == city) return it[1].toDouble() to it[2].toDouble()
        }
        val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=ko&name=" +
            URLEncoder.encode(city, "UTF-8")))
        val first = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        val lat = first.getDouble("latitude")
        val lon = first.getDouble("longitude")
        prefs.edit { putString(PREF_PLACE, "$city|$lat|$lon"); remove(PREF_NOW) }
        return lat to lon
    }

    /** the WMO weather code in words */
    private fun describe(code: Int, korean: Boolean): String = when (code) {
        0 -> if (korean) "맑음" else "Clear"
        1, 2 -> if (korean) "구름 조금" else "Partly cloudy"
        3 -> if (korean) "흐림" else "Cloudy"
        45, 48 -> if (korean) "안개" else "Fog"
        in 51..57 -> if (korean) "이슬비" else "Drizzle"
        in 61..67 -> if (korean) "비" else "Rain"
        in 71..77 -> if (korean) "눈" else "Snow"
        in 80..82 -> if (korean) "소나기" else "Showers"
        85, 86 -> if (korean) "눈" else "Snow"
        in 95..99 -> if (korean) "뇌우" else "Thunderstorm"
        else -> ""
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
