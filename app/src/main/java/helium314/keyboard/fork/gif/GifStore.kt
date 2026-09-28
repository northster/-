// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.gif

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** fork: the GIFs sent recently and the favorites (starred in the GIF panel), newest first */
object GifStore {
    private const val PREF_RECENT = "fork_gif_recent"
    private const val PREF_FAVORITES = "fork_gif_favorites"
    private const val MAX_RECENT = 60

    fun recent(prefs: SharedPreferences) = read(prefs, PREF_RECENT)
    fun favorites(prefs: SharedPreferences) = read(prefs, PREF_FAVORITES)

    fun isFavorite(prefs: SharedPreferences, item: GifItem) = favorites(prefs).any { it.id == item.id }

    fun addRecent(prefs: SharedPreferences, item: GifItem) =
        write(prefs, PREF_RECENT, (listOf(item) + recent(prefs).filter { it.id != item.id }).take(MAX_RECENT))

    /** @return whether it is a favorite now */
    fun toggleFavorite(prefs: SharedPreferences, item: GifItem): Boolean {
        val list = favorites(prefs)
        val was = list.any { it.id == item.id }
        write(prefs, PREF_FAVORITES, if (was) list.filter { it.id != item.id } else listOf(item) + list)
        return !was
    }

    private fun read(prefs: SharedPreferences, pref: String): List<GifItem> {
        val array = runCatching { JSONArray(prefs.getString(pref, null) ?: return emptyList()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            GifItem(o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("preview"), o.optString("full"),
                o.optDouble("ratio", 1.0).toFloat())
        }
    }

    private fun write(prefs: SharedPreferences, pref: String, items: List<GifItem>) {
        val array = JSONArray()
        for (item in items) array.put(JSONObject().put("id", item.id).put("preview", item.previewUrl)
            .put("full", item.fullUrl).put("ratio", item.aspectRatio.toDouble()))
        prefs.edit { putString(pref, array.toString()) }
    }
}
