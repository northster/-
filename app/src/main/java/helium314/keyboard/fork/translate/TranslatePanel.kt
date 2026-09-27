// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.translate

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * fork: AI translation with the whole keyboard area: target language, style and purpose as chips, then whether the
 * translation replaces the text or goes under it. Covers the letters (same size) until it is closed.
 * The last choices are kept; languages are listed most used first.
 */
@SuppressLint("ViewConstructor")
class TranslatePanel(
    context: Context,
    private val keyboardView: View,
    private val prefs: SharedPreferences,
    /** the text that will be translated, shown at the top */
    private val preview: String,
    private val onTranslate: (prompt: String, label: String, keepOriginal: Boolean) -> Unit,
    private val onClose: () -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val colors = Settings.getValues().mColors

    /** label on the chip to the name the model gets */
    class Option(val label: String, val prompt: String)

    private val languages = LANGUAGES.sortedByDescending { prefs.getInt(PREF_USE + it.label, 0) }
    private var language = languages.firstOrNull { it.label == prefs.getString(PREF_LANGUAGE, null) } ?: languages.first()
    private var style = STYLES.firstOrNull { it.label == prefs.getString(PREF_STYLE, null) } ?: STYLES.first()
    private var purpose = PURPOSES.firstOrNull { it.label == prefs.getString(PREF_PURPOSE, null) } ?: PURPOSES.first()
    private var keepOriginal = prefs.getBoolean(PREF_KEEP, false)

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(6), dp(12), dp(10))
    }

    init {
        setBackgroundColor(colors.get(ColorType.MAIN_BACKGROUND))
        isClickable = true // touches stay here, the keys below don't get them
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        build()
    }

    /** as large as the keyboard view: MATCH_PARENT in the wrap_content keyboard frame would take the whole screen */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec)
            else keyboardView.measuredHeight
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    private fun build() {
        content.removeAllViews()
        // header: close, title, translate
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(chip("‹", false) { onClose() }, LinearLayout.LayoutParams(dp(40), dp(34)))
        header.addView(label(context.getString(R.string.fork_translate_title), 16f, ColorType.KEY_TEXT).apply {
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(chip(context.getString(R.string.fork_translate_go), true) { translate() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
        content.addView(header)
        // what is translated
        content.addView(label(preview.ifBlank { context.getString(R.string.fork_translate_empty) }, 13f, ColorType.KEY_HINT_TEXT).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            setPadding(dp(2), dp(6), dp(2), 0)
        })
        section(R.string.fork_translate_language, languages, language) { language = it }
        section(R.string.fork_translate_style, STYLES, style) { style = it }
        section(R.string.fork_translate_purpose, PURPOSES, purpose) { purpose = it }
        val results = listOf(Option(context.getString(R.string.fork_translate_replace), ""),
            Option(context.getString(R.string.fork_translate_keep), "keep"))
        section(R.string.fork_translate_result, results, results[if (keepOriginal) 1 else 0]) { keepOriginal = it.prompt == "keep" }
    }

    private fun section(title: Int, options: List<Option>, selected: Option, onPick: (Option) -> Unit) {
        content.addView(label(context.getString(title), 12f, ColorType.KEY_HINT_TEXT).apply {
            setPadding(dp(2), dp(10), 0, dp(4))
        })
        val flow = FlowLayout(context, dp(6))
        for (option in options) {
            flow.addView(chip(option.label, option === selected) { onPick(option); build() },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))
        }
        content.addView(flow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun translate() {
        prefs.edit {
            putString(PREF_LANGUAGE, language.label)
            putString(PREF_STYLE, style.label)
            putString(PREF_PURPOSE, purpose.label)
            putBoolean(PREF_KEEP, keepOriginal)
            putInt(PREF_USE + language.label, prefs.getInt(PREF_USE + language.label, 0) + 1)
        }
        val prompt = buildString {
            append("Translate the text to ${language.prompt}.")
            if (style.prompt.isNotEmpty()) append(' ').append(style.prompt)
            if (purpose.prompt.isNotEmpty()) append(' ').append(purpose.prompt)
            append(" Keep the meaning, line breaks, names and numbers. If the text is already in ${language.prompt}, ")
            append("rewrite it in the requested style instead. Output only the translation.")
        }
        onTranslate(prompt, "→ ${language.label}", keepOriginal)
    }

    private fun label(text: String, sp: Float, color: ColorType) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(colors.get(color))
        KeyboardTypeface.applyToTextView(this)
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        setSingleLine()
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        KeyboardTypeface.applyToTextView(this)
        setTextColor(colors.get(if (selected) ColorType.ACTION_KEY_ICON else ColorType.KEY_TEXT))
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(colors.get(if (selected) ColorType.ACTION_KEY_BACKGROUND else ColorType.KEY_BACKGROUND))
        }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = (v * density).toInt()

    /** children in rows, wrapping to the next row when full */
    private class FlowLayout(context: Context, private val gap: Int) : ViewGroup(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            var x = 0
            var y = 0
            var rowHeight = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(child.layoutParams.height, MeasureSpec.EXACTLY))
                if (x > 0 && x + child.measuredWidth > width) {
                    x = 0
                    y += rowHeight + gap
                    rowHeight = 0
                }
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
            setMeasuredDimension(width, y + rowHeight)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val width = r - l
            var x = 0
            var y = 0
            var rowHeight = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (x > 0 && x + child.measuredWidth > width) {
                    x = 0
                    y += rowHeight + gap
                    rowHeight = 0
                }
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                x += child.measuredWidth + gap
                rowHeight = maxOf(rowHeight, child.measuredHeight)
            }
        }
    }

    companion object {
        const val PREF_LANGUAGE = "fork_translate_language"
        const val PREF_STYLE = "fork_translate_style"
        const val PREF_PURPOSE = "fork_translate_purpose"
        const val PREF_KEEP = "fork_translate_keep"
        private const val PREF_USE = "fork_translate_usage_"

        val LANGUAGES = listOf(
            Option("한국어", "Korean"), Option("English", "English"), Option("日本語", "Japanese"),
            Option("中文(简体)", "Simplified Chinese"), Option("中文(繁體)", "Traditional Chinese"),
            Option("Español", "Spanish"), Option("Français", "French"), Option("Deutsch", "German"),
            Option("Tiếng Việt", "Vietnamese"), Option("ไทย", "Thai"), Option("Bahasa Indonesia", "Indonesian"),
            Option("Русский", "Russian"), Option("Italiano", "Italian"), Option("Português", "Portuguese"),
            Option("العربية", "Arabic"), Option("हिन्दी", "Hindi"),
        )
        val STYLES = listOf(
            Option("자연스럽게", ""),
            Option("존댓말", "Use a polite, respectful register (in Korean: 존댓말, 해요체)."),
            Option("반말", "Use a casual register between close friends (in Korean: 반말)."),
            Option("격식", "Use a formal, professional tone (in Korean: 하십시오체)."),
            Option("친근하게", "Use a warm, friendly tone."),
            Option("간결하게", "Make it short and concise."),
            Option("직역", "Translate literally, as close to the original wording as possible."),
        )
        val PURPOSES = listOf(
            Option("일반", ""),
            Option("채팅", "It is a chat message: keep it natural and conversational, as a native speaker would text."),
            Option("이메일", "It is an email: use the conventions of an email in that language."),
            Option("업무", "It is a work / business message."),
            Option("SNS", "It is a social media post: natural and engaging, keep hashtags and emojis."),
            Option("문서", "It is a document: precise and consistent terminology."),
            Option("여행", "It is said while travelling (asking, ordering, directions): simple and clear."),
        )
    }
}
