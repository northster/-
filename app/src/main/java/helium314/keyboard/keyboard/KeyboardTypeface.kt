// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.ui.text.font.FontFamily
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.settings.Settings

object KeyboardTypeface {
    private val lock = Any()

    private var cachedCustomTypeface: Typeface? = null
    private var cachedCustomFontFamily: FontFamily? = null
    @Volatile
    private var customTypefaceLoaded = false

    // fork: separate font for Hangul
    private var cachedKoreanTypeface: Typeface? = null
    @Volatile
    private var koreanTypefaceLoaded = false

    private var cachedEmojiTypeface: Typeface? = null
    @Volatile
    private var emojiTypefaceLoaded = false

    private fun loadCustomTypeface(context: Context): Typeface? {
        return runCatching {
            Typeface.createFromFile(Settings.getCustomFontFile(context))
        }.getOrNull()
    }

    private fun loadCustomEmojiTypeface(context: Context): Typeface? {
        return runCatching {
            Typeface.createFromFile(Settings.getCustomEmojiFontFile(context))
        }.getOrNull()
    }

    @JvmStatic
    fun customTypeface(): Typeface? {
        if (customTypefaceLoaded) return cachedCustomTypeface
        val context = Settings.getCurrentContext() ?: return null
        synchronized(lock) {
            if (!customTypefaceLoaded) {
                cachedCustomTypeface = loadCustomTypeface(context)
                cachedCustomFontFamily = cachedCustomTypeface?.let(::FontFamily)
                customTypefaceLoaded = true
            }
            return cachedCustomTypeface
        }
    }

    /** fork: font file for Hangul, next to HeliBoard's custom font (which is used for everything else) */
    @JvmStatic
    fun koreanFontFile(context: Context) = java.io.File(helium314.keyboard.latin.utils.DeviceProtectedUtils.getFilesDir(context), "custom_font_ko")

    @JvmStatic
    fun koreanTypeface(): Typeface? {
        if (koreanTypefaceLoaded) return cachedKoreanTypeface
        val context = Settings.getCurrentContext() ?: return null
        synchronized(lock) {
            if (!koreanTypefaceLoaded) {
                cachedKoreanTypeface = runCatching { Typeface.createFromFile(koreanFontFile(context)) }.getOrNull()
                koreanTypefaceLoaded = true
            }
            return cachedKoreanTypeface
        }
    }

    /** fork: Hangul syllables and jamo */
    @JvmStatic
    fun containsHangul(text: CharSequence): Boolean = text.any {
        it in '\uAC00'..'\uD7A3' || it in '\u1100'..'\u11FF' || it in '\u3130'..'\u318F'
            || it in '\uA960'..'\uA97F' || it in '\uD7B0'..'\uD7FF'
    }

    @JvmStatic
    fun emojiTypeface(): Typeface? {
        if (emojiTypefaceLoaded) return cachedEmojiTypeface
        val context = Settings.getCurrentContext() ?: return null
        synchronized(lock) {
            if (!emojiTypefaceLoaded) {
                cachedEmojiTypeface = loadCustomEmojiTypeface(context)
                emojiTypefaceLoaded = true
            }
            return cachedEmojiTypeface
        }
    }

    @JvmStatic
    fun customFontFamily(): FontFamily? {
        if (!customTypefaceLoaded) customTypeface()
        return cachedCustomFontFamily
    }

    @JvmStatic
    fun resolve(
        text: CharSequence?,
        defaultTypeface: Typeface = Typeface.DEFAULT,
    ): Typeface {
        val emojiTypeface = emojiTypeface()
        return if (emojiTypeface != null && text != null && isEmoji(text)) {
            emojiTypeface
        } else if (text != null && containsHangul(text)) {
            // fork: Korean font for anything with Hangul in it. Without one, not the Latin font either: it usually has
            //  no Hangul, and the fallback font would get its weight (e.g. Doto Black makes Hangul very bold)
            //  (also when the caller passes the Latin font as its default, like the clipboard)
            koreanTypeface() ?: defaultTypeface.takeIf { it != customTypeface() } ?: Typeface.DEFAULT
        } else {
            customTypeface() ?: defaultTypeface
        }
    }

    @JvmStatic
    fun applyToTextView(textView: TextView) {
        applyToTextView(textView, textView.text, Typeface.DEFAULT)
    }

    @JvmStatic
    fun applyToTextView(textView: TextView, text: CharSequence?, defaultTypeface: Typeface) {
        textView.typeface = resolve(text, defaultTypeface = defaultTypeface)
    }

    @JvmStatic
    fun clearCache() {
        synchronized(lock) {
            cachedCustomTypeface = null
            cachedCustomFontFamily = null
            customTypefaceLoaded = false
            cachedEmojiTypeface = null
            emojiTypefaceLoaded = false
            cachedKoreanTypeface = null
            koreanTypefaceLoaded = false
        }
    }
}
