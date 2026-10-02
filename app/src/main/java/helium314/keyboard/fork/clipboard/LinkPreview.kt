// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * fork: what a copied link is about, for the paste chip: the site's icon and a title (a video's title, a post's text,
 * a page's title). YouTube and X answer with oEmbed, other pages are read up to their head for og:title / <title>.
 * The request goes straight to the site (and its icon), only after a link was copied, and only with the setting on.
 */
object LinkPreview {
    const val PREF_ENABLED = "fork_link_preview"
    private const val TAG = "LinkPreview"
    private const val MAX_PAGE_BYTES = 256 * 1024

    class Preview(val title: String, val icon: Bitmap?)

    private val cache = LruCache<String, Preview>(30)
    /** links that gave nothing, not asked again */
    private val failed = HashSet<String>()
    private val loading = HashSet<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    fun enabled(context: Context) = context.prefs().getBoolean(PREF_ENABLED, true)

    fun cached(url: String): Preview? = cache.get(normalize(url))

    /** reads the preview in the background; [onDone] on the main thread when there is one */
    fun load(context: Context, url: String, onDone: (Preview) -> Unit) {
        if (!enabled(context)) return
        val key = normalize(url)
        cache.get(key)?.let { onDone(it); return }
        synchronized(this) {
            if (key in failed || key in loading) return
            loading.add(key)
        }
        executor.execute {
            val preview = runCatching { fetch(key) }.onFailure { Log.w(TAG, "no preview for $key", it) }.getOrNull()
            synchronized(this) {
                loading.remove(key)
                if (preview == null) failed.add(key)
            }
            if (preview != null) {
                cache.put(key, preview)
                handler.post { onDone(preview) }
            }
        }
    }

    private fun normalize(url: String) = if (url.contains("://")) url else "https://$url"

    private fun fetch(url: String): Preview? {
        val host = URL(url).host.lowercase()
        val origin = "https://$host"
        return when {
            host.endsWith("youtube.com") || host == "youtu.be" -> {
                val o = JSONObject(get("https://www.youtube.com/oembed?format=json&url=" + encode(url)))
                Preview(o.optString("title").ifBlank { return null }, icon("https://www.youtube.com/favicon.ico"))
            }
            host.endsWith("x.com") || host.endsWith("twitter.com") -> {
                // the post's text is in the embed html
                val o = JSONObject(get("https://publish.twitter.com/oembed?omit_script=true&url=" + encode(url)))
                val html = o.optString("html")
                val text = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
                val title = text?.let { strip(it) }?.takeIf { it.isNotBlank() }
                    ?: o.optString("author_name").takeIf { it.isNotBlank() } ?: return null
                Preview(title, icon("https://abs.twimg.com/favicons/twitter.3.ico"))
            }
            else -> {
                val page = get(url, MAX_PAGE_BYTES)
                val head = page.substringBefore("</head>")
                val title = meta(head, "og:title") ?: meta(head, "twitter:title")
                    ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                        .find(head)?.groupValues?.get(1)?.let { strip(it) }
                    ?: return null
                // the page's own icon, else the usual place
                val iconHref = Regex("<link[^>]+rel=[\"'](?:shortcut )?(?:icon|apple-touch-icon)[\"'][^>]*>", RegexOption.IGNORE_CASE)
                    .findAll(head).mapNotNull { attr(it.value, "href") }.firstOrNull()
                val iconUrl = when {
                    iconHref == null -> "$origin/favicon.ico"
                    iconHref.startsWith("//") -> "https:$iconHref"
                    iconHref.startsWith("http") -> iconHref
                    iconHref.startsWith("/") -> origin + iconHref
                    else -> "$origin/$iconHref"
                }
                Preview(title.take(200), icon(iconUrl))
            }
        }
    }

    private fun meta(head: String, property: String): String? {
        val tag = Regex("<meta[^>]+(?:property|name)=[\"']${Regex.escape(property)}[\"'][^>]*>", RegexOption.IGNORE_CASE)
            .find(head)?.value ?: return null
        return attr(tag, "content")?.let { strip(it) }?.takeIf { it.isNotBlank() }
    }

    private fun attr(tag: String, name: String) =
        Regex("$name=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    /** tags out, entities decoded, whitespace folded */
    private fun strip(html: String): String =
        androidx.core.text.HtmlCompat.fromHtml(html.replace(Regex("<br\\s*/?>"), " "), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString().replace(Regex("\\s+"), " ").trim()

    private fun icon(url: String): Bitmap? = runCatching {
        val bytes = bytes(url, 512 * 1024)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun get(url: String, max: Int = MAX_PAGE_BYTES) = String(bytes(url, max), Charsets.UTF_8)

    private fun bytes(url: String, max: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
        c.setRequestProperty("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
        try {
            check(c.responseCode in 200..299) { "HTTP ${c.responseCode}" }
            c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (out.size() < max) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
}
