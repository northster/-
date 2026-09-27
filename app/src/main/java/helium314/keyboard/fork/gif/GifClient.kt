// SPDX-License-Identifier: GPL-3.0-only
// Requests and parsing follow WM Keyboard's KlipyClient and GiphyClient (github.com/wasi-master/wmkeyboard,
// feature/tools/.../core/tools), MIT License, Copyright (c) 2026 Wasi Master. fork: org.json instead of
// kotlinx.serialization, one search function over both providers, keys encrypted like the Gemini keys.
package helium314.keyboard.fork.gif

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.fork.slate.KeyCipher
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
 * API key, entered in the toolbar settings; KLIPY is used when both are there. An empty query gives trending GIFs.
 */
object GifClient {
    const val PREF_KLIPY_KEY = "fork_gif_klipy_key"
    const val PREF_GIPHY_KEY = "fork_gif_giphy_key"
    private const val LIMIT = 24

    fun key(prefs: SharedPreferences, pref: String): String? =
        prefs.getString(pref, null)?.let { KeyCipher.decrypt(it) }?.takeIf { it.isNotBlank() }

    /** @return false if the key could not be encrypted (no keystore), blank removes it */
    fun setKey(prefs: SharedPreferences, pref: String, key: String): Boolean {
        if (key.isBlank()) { prefs.edit { remove(pref) }; return true }
        val encrypted = KeyCipher.encrypt(key.trim()) ?: return false
        prefs.edit { putString(pref, encrypted) }
        return true
    }

    fun hasKey(prefs: SharedPreferences) = key(prefs, PREF_KLIPY_KEY) != null || key(prefs, PREF_GIPHY_KEY) != null

    /** blocking, call off the main thread */
    fun search(prefs: SharedPreferences, query: String): List<GifItem> {
        key(prefs, PREF_KLIPY_KEY)?.let { return klipy(query, it) }
        key(prefs, PREF_GIPHY_KEY)?.let { return giphy(query, it) }
        throw IllegalStateException("no GIF API key")
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
