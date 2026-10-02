// SPDX-License-Identifier: GPL-3.0-only
// Requests and parsing follow WM Keyboard's KlipyClient and GiphyClient (github.com/wasi-master/wmkeyboard,
// feature/tools/.../core/tools), MIT License, Copyright (c) 2026 Wasi Master. fork: org.json instead of
// kotlinx.serialization, one search function over both providers, keys encrypted like the Gemini keys.
package helium314.keyboard.fork.gif

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.fork.slate.KeyCipher
import helium314.keyboard.latin.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** a GIF found by [GifClient.search]: a small preview for the toolbar and the full file to send */
data class GifItem(val id: String, val previewUrl: String, val fullUrl: String, val aspectRatio: Float)

/**
 * GIF search on KLIPY (Tenor's successor, Google closed the Tenor API for other apps) or GIPHY. Both need a free
 * API key: the user's own from the toolbar settings, else the one built into the app (if the build has one); KLIPY
 * is used when both are there. An empty query gives trending GIFs.
 */
object GifClient {
    const val PREF_KLIPY_KEY = "fork_gif_klipy_key"
    const val PREF_GIPHY_KEY = "fork_gif_giphy_key"
    /** the panel opens with a search for the words before the cursor */
    const val PREF_AUTO = "fork_gif_auto"
    private const val LIMIT = 24

    /** the user's own key (settings) */
    fun key(prefs: SharedPreferences, pref: String): String? =
        prefs.getString(pref, null)?.let { KeyCipher.decrypt(it) }?.takeIf { it.isNotBlank() }

    /** the key built into the app (CI passes it from the repository secrets), used when the user has none */
    fun builtInKey(pref: String): String? = when (pref) {
        PREF_KLIPY_KEY -> BuildConfig.DT_KLIPY_KEY
        PREF_GIPHY_KEY -> BuildConfig.DT_GIPHY_KEY
        else -> ""
    }.takeIf { it.isNotBlank() }

    private fun keyOrBuiltIn(prefs: SharedPreferences, pref: String) = key(prefs, pref) ?: builtInKey(pref)

    /** a provider with the user's own key comes before the built-in keys */
    private fun provider(prefs: SharedPreferences): Pair<String, String>? {
        for (pref in listOf(PREF_KLIPY_KEY, PREF_GIPHY_KEY)) key(prefs, pref)?.let { return pref to it }
        for (pref in listOf(PREF_KLIPY_KEY, PREF_GIPHY_KEY)) builtInKey(pref)?.let { return pref to it }
        return null
    }

    /** @return false if the key could not be encrypted (no keystore), blank removes it */
    fun setKey(prefs: SharedPreferences, pref: String, key: String): Boolean {
        if (key.isBlank()) { prefs.edit { remove(pref) }; return true }
        val encrypted = KeyCipher.encrypt(key.trim()) ?: return false
        prefs.edit { putString(pref, encrypted) }
        return true
    }

    /** whose GIFs are shown, for the attribution the providers ask for ("Powered by GIPHY") */
    fun providerName(prefs: SharedPreferences): String? =
        provider(prefs)?.first?.let { if (it == PREF_KLIPY_KEY) "KLIPY" else "GIPHY" }

    fun hasKey(prefs: SharedPreferences) = keyOrBuiltIn(prefs, PREF_KLIPY_KEY) != null || keyOrBuiltIn(prefs, PREF_GIPHY_KEY) != null

    /**
     * A search for the end of [text]: its last two words, Korean ones without the particle / ending ("고양이가" ->
     * "고양이", "축하해요" -> "축하"). Empty when nothing is written.
     */
    fun autoQuery(text: String): String {
        val sentence = text.split(Regex("[.!?\n。]")).lastOrNull { it.isNotBlank() } ?: return ""
        return sentence.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.takeLast(2)
            .joinToString(" ") { stem(it) }.trim()
    }

    private val ENDINGS = setOf(
        "이", "가", "은", "는", "을", "를", "의", "에", "에서", "에게", "한테", "로", "으로", "와", "과", "랑", "이랑",
        "도", "만", "까지", "부터", "요", "야", "이다", "다", "예요", "에요", "입니다", "죠", "네", "들", "고", "지",
    )

    private fun stem(word: String): String {
        if (word.none { it in '가'..'힣' }) return word
        for (len in 2 until word.length) {
            val rest = word.substring(len)
            if (rest in ENDINGS || rest[0] in "하해했한할") return word.substring(0, len)
        }
        return word
    }

    /** blocking, call off the main thread */
    fun search(prefs: SharedPreferences, query: String): List<GifItem> {
        val (pref, key) = provider(prefs) ?: throw IllegalStateException("no GIF API key")
        return if (pref == PREF_KLIPY_KEY) klipy(query, key) else giphy(query, key)
    }

    private fun klipy(query: String, apiKey: String): List<GifItem> {
        val endpoint = if (query.isBlank()) "trending" else "search"
        val url = buildString {
            append("https://api.klipy.com/api/v1/${encode(apiKey)}/gifs/$endpoint")
            append("?page=1&per_page=$LIMIT&rating=pg-13")
            if (query.isNotBlank()) append("&q=${encode(query.trim())}")
        }
        return parseKlipy(get(url))
    }

    private fun giphy(query: String, apiKey: String): List<GifItem> {
        val endpoint = if (query.isBlank()) "trending" else "search"
        val url = buildString {
            append("https://api.giphy.com/v1/gifs/$endpoint?api_key=${encode(apiKey)}")
            if (query.isNotBlank()) append("&q=${encode(query.trim())}")
            append("&limit=$LIMIT&rating=pg-13")
        }
        return parseGiphy(get(url))
    }

    /** `{"data":{"data":[…]}}` (or the list directly), media under `file.<size>.<format>` */
    private fun parseKlipy(body: String): List<GifItem> {
        val data = JSONObject(body).opt("data") ?: return emptyList()
        val items = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("data") ?: return emptyList()
            else -> return emptyList()
        }
        return (0 until items.length()).mapNotNull { i ->
            val result = items.optJSONObject(i) ?: return@mapNotNull null
            val file = result.optJSONObject("file") ?: return@mapNotNull null
            val preview = file.variant("sm", "gif") ?: file.variant("xs", "gif") ?: file.variant("md", "gif")
                ?: return@mapNotNull null
            val full = file.variant("hd", "gif") ?: file.variant("md", "gif") ?: preview
            val id = result.optString("id").ifEmpty { result.optString("slug") }.ifEmpty { return@mapNotNull null }
            GifItem("klipy_$id", preview.first, full.first, preview.second)
        }
    }

    private fun JSONObject.variant(size: String, format: String): Pair<String, Float>? {
        val holder = optJSONObject(size)?.optJSONObject(format) ?: return null
        val url = holder.optString("url").ifEmpty { return null }
        return url to ratio(holder)
    }

    private fun parseGiphy(body: String): List<GifItem> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val result = data.optJSONObject(i) ?: return@mapNotNull null
            val images = result.optJSONObject("images") ?: return@mapNotNull null
            fun rendition(name: String) = images.optJSONObject(name)?.let { r ->
                r.optString("url").takeIf { it.isNotEmpty() }?.let { it to ratio(r) }
            }
            val preview = rendition("fixed_height_small") ?: rendition("fixed_width_small") ?: rendition("preview_gif")
                ?: rendition("original") ?: return@mapNotNull null
            val full = rendition("original") ?: preview
            val id = result.optString("id").ifEmpty { return@mapNotNull null }
            GifItem("giphy_$id", preview.first, full.first, preview.second)
        }
    }

    private fun ratio(o: JSONObject): Float {
        val w = o.optString("width").toFloatOrNull() ?: o.optDouble("width", Double.NaN).toFloat()
        val h = o.optString("height").toFloatOrNull() ?: o.optDouble("height", Double.NaN).toFloat()
        return if (w.isFinite() && h.isFinite() && h > 0f && w > 0f) w / h else 1f
    }

    /** the full GIF in the cache (shared with the app through the clipboard file provider), blocking */
    fun download(context: Context, item: GifItem): File {
        val dir = File(context.cacheDir, "gif").apply { mkdirs() }
        val name = item.id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".gif"
        val file = File(dir, name)
        if (file.isFile && file.length() > 0) return file
        // keep the cache small: only the last GIFs sent
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(30)?.forEach { it.delete() }
        val tmp = File(dir, "$name.part")
        tmp.writeBytes(bytes(item.fullUrl))
        tmp.renameTo(file)
        return file
    }

    fun bytes(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 15000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun get(url: String) = String(bytes(url), Charsets.UTF_8)

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
}
