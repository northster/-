// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.translate

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * fork: AI translation with the whole keyboard area. Language, style and purpose are three wheels side by side, like
 * the alarm clock's time picker; the text to translate is shown above them. The toolbar above has the way back and
 * the translate button ([translate]). The translation replaces the text; text that already is in the chosen
 * language only gets its grammar and style fixed. The last choices are kept, languages are listed most used first.
 */
@SuppressLint("ViewConstructor")
class TranslatePanel(
    context: Context,
    private val keyboardView: View,
    private val prefs: SharedPreferences,
    /** the text that will be translated, shown at the top */
    private val preview: String,
    private val onTranslate: (prompt: String, label: String) -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val palette = Settings.getValues().mColors

    /** label on the wheel to what the model gets */
    class Option(val label: String, val prompt: String)

    private val languages = options(prefs, KIND_LANGUAGE).sortedByDescending { prefs.getInt(PREF_USE + it.label, 0) }
    private val styles = options(prefs, KIND_STYLE)
    private val purposes = options(prefs, KIND_PURPOSE)
    private var language = languages.firstOrNull { it.label == prefs.getString(PREF_LANGUAGE, null) } ?: languages.first()
    private var style = styles.firstOrNull { it.label == prefs.getString(PREF_STYLE, null) } ?: styles.first()
    private var purpose = purposes.firstOrNull { it.label == prefs.getString(PREF_PURPOSE, null) } ?: purposes.first()

    init {
        setBackgroundColor(palette.get(ColorType.MAIN_BACKGROUND))
        isClickable = true // touches stay here, the keys below don't get them
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(4))
        }
        // the text itself is in the toolbar above (AI translation | text)
        // the three wheels
        val wheels = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun column(title: Int, options: List<Option>, selected: Option, weight: Float, onPick: (Option) -> Unit) {
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(label(context.getString(title), 12f, ColorType.KEY_HINT_TEXT).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            col.addView(WheelPicker(context, options.map { it.label }, options.indexOf(selected).coerceAtLeast(0)) {
                onPick(options[it])
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            wheels.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight))
        }
        column(R.string.fork_translate_language, languages, language, 1.3f) { language = it }
        column(R.string.fork_translate_style, styles, style, 1f) { style = it }
        column(R.string.fork_translate_purpose, purposes, purpose, 1f) { purpose = it }
        content.addView(wheels, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** as large as the keyboard view: MATCH_PARENT in the wrap_content keyboard frame would take the whole screen */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec)
            else keyboardView.measuredHeight
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    /** the translate button in the toolbar */
    fun translate() {
        prefs.edit {
            putString(PREF_LANGUAGE, language.label)
            putString(PREF_STYLE, style.label)
            putString(PREF_PURPOSE, purpose.label)
            putInt(PREF_USE + language.label, prefs.getInt(PREF_USE + language.label, 0) + 1)
        }
        val prompt = buildString {
            append("If the text is not in ${language.prompt}, translate it to ${language.prompt}. ")
            append("If it already is in ${language.prompt}, do not translate it: only correct its grammar, spelling ")
            append("and punctuation, and apply the style below.")
            if (style.prompt.isNotEmpty()) append(' ').append(style.prompt)
            if (purpose.prompt.isNotEmpty()) append(' ').append(purpose.prompt)
            append(" Keep the meaning, line breaks, names and numbers. Output only the resulting text.")
        }
        onTranslate(prompt, "→ ${language.label}")
    }

    private fun label(text: String, sp: Float, color: ColorType) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(palette.get(color))
        KeyboardTypeface.applyToTextView(this)
    }

    private fun dp(v: Int) = (v * density).toInt()

    companion object {
        const val PREF_LANGUAGE = "fork_translate_language"
        const val PREF_STYLE = "fork_translate_style"
        const val PREF_PURPOSE = "fork_translate_purpose"
        private const val PREF_USE = "fork_translate_usage_"

        val LANGUAGES = listOf(
            Option("한국어", "Korean"), Option("영어", "English"), Option("일본어", "Japanese"),
            Option("중국어(간체)", "Simplified Chinese"), Option("중국어(번체)", "Traditional Chinese"),
            Option("스페인어", "Spanish"), Option("프랑스어", "French"), Option("독일어", "German"),
            Option("베트남어", "Vietnamese"), Option("태국어", "Thai"), Option("인도네시아어", "Indonesian"),
            Option("러시아어", "Russian"), Option("이탈리아어", "Italian"), Option("포르투갈어", "Portuguese"),
            Option("아랍어", "Arabic"), Option("힌디어", "Hindi"),
        )

        // ---- entries the user added (Tools > AI translation), after the built-in ones
        const val KIND_LANGUAGE = "languages"
        const val KIND_STYLE = "styles"
        const val KIND_PURPOSE = "purposes"
        private fun customPref(kind: String) = "fork_translate_custom_$kind"

        fun custom(prefs: SharedPreferences, kind: String): List<Option> {
            val arr = runCatching { org.json.JSONArray(prefs.getString(customPref(kind), "[]")) }.getOrElse { org.json.JSONArray() }
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val label = o.optString("l").ifBlank { return@mapNotNull null }
                Option(label, o.optString("p"))
            }
        }

        fun saveCustom(prefs: SharedPreferences, kind: String, options: List<Option>) {
            val arr = org.json.JSONArray()
            options.forEach { arr.put(org.json.JSONObject().put("l", it.label).put("p", it.prompt)) }
            prefs.edit { putString(customPref(kind), arr.toString()) }
        }

        /** built-in and added entries; an added one without an instruction is used by its name */
        fun options(prefs: SharedPreferences, kind: String): List<Option> {
            val builtIn = when (kind) { KIND_LANGUAGE -> LANGUAGES; KIND_STYLE -> STYLES; else -> PURPOSES }
            return builtIn + custom(prefs, kind).map { o ->
                Option(o.label, o.prompt.ifBlank {
                    when (kind) {
                        KIND_LANGUAGE -> o.label
                        KIND_STYLE -> "Write it in this style: ${o.label}."
                        else -> "What it is for: ${o.label}."
                    }
                })
            }
        }
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
