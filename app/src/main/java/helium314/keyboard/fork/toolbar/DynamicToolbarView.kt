// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * The toolbar shown above the keyboard. It only draws buttons, state and animation are handled
 * by [DynamicToolbarController].
 * Three contents: the normal row (paste chips + item buttons), the header of an open tool
 * (back to keyboard | tool name | actions, like Samsung's keyboard) and the clipboard search bar.
 */
class DynamicToolbarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val density = resources.displayMetrics.density
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val chips = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val buttons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    // header of an open tool (clipboard panel)
    private val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
    }

    // search bar
    private val searchBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
    }
    private val queryView = TextView(context).apply {
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.START
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        maxWidth = (140 * density).toInt()
        minWidth = (72 * density).toInt()
    }
    private val resultScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
    private val results = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    init {
        row.addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        row.addView(buttons, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        resultScroll.addView(results, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private val ripple get() = TypedValue().also {
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
    }.resourceId

    private fun iconButton(icon: Int, label: String, onClick: () -> Unit) =
        iconButton(androidx.core.content.ContextCompat.getDrawable(context, icon), label, onClick)

    private fun iconButton(icon: android.graphics.drawable.Drawable?, label: String, onClick: () -> Unit) = ImageButton(context).apply {
        setImageDrawable(icon)
        contentDescription = label
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        ripple.let { if (it != 0) setBackgroundResource(it) else background = null }
        setOnClickListener { onClick() }
        Settings.getValues().mColors.setColor(this, ColorType.TOOL_BAR_KEY)
    }

    fun setItems(items: List<ToolbarItem>, onClick: (ToolbarItem) -> Unit) {
        buttons.removeAllViews()
        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.MAIN_BACKGROUND) // same as keyboard, looks like the keyboard grows
        for (item in items) {
            val button = iconButton(item.icon, context.getString(item.label)) { onClick(item) }.apply { tag = item.id }
            buttons.addView(button, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /** rounded chip in key colors */
    private fun chip(text: String, highlight: Boolean, maxWidthDp: Int, onClick: () -> Unit) = TextView(context).apply {
        val colors = Settings.getValues().mColors
        this.text = text
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = (maxWidthDp * density).toInt()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        KeyboardTypeface.applyToTextView(this)
        setTextColor(if (highlight) colors.get(ColorType.ACTION_KEY_ICON) else colors.get(ColorType.KEY_TEXT))
        gravity = Gravity.CENTER_VERTICAL
        val h = (12 * density).toInt()
        setPadding(h, 0, h, 0)
        background = GradientDrawable().apply {
            cornerRadius = 15 * density
            setColor(if (highlight) colors.get(ColorType.ACTION_KEY_BACKGROUND) else colors.get(ColorType.KEY_BACKGROUND))
        }
        setOnClickListener { onClick() }
    }

    private fun chipParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (30 * density).toInt()).apply {
        marginStart = (6 * density).toInt()
    }

    /** latest clip / code found in it, empty to hide */
    fun setPasteChips(latest: String?, code: String?, onPaste: (String) -> Unit) {
        chips.removeAllViews()
        if (code != null)
            chips.addView(chip(code, true, 120) { onPaste(code) }, chipParams())
        if (latest != null && latest != code)
            chips.addView(chip(latest.replace('\n', ' '), false, 150) { onPaste(latest) }, chipParams())
    }

    fun showSearch(onClose: () -> Unit) {
        val colors = Settings.getValues().mColors
        searchBar.removeAllViews()
        searchBar.addView(iconButton(R.drawable.sym_keyboard_search_lxx, context.getString(R.string.fork_clip_search)) { },
            LinearLayout.LayoutParams((40 * density).toInt(), LayoutParams.MATCH_PARENT))
        queryView.setTextColor(colors.get(ColorType.KEY_TEXT))
        queryView.setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        queryView.hint = context.getString(R.string.fork_clip_search_hint)
        KeyboardTypeface.applyToTextView(queryView)
        searchBar.addView(queryView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        searchBar.addView(resultScroll, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        searchBar.addView(iconButton(R.drawable.ic_close, context.getString(android.R.string.cancel)) { onClose() },
            LinearLayout.LayoutParams((40 * density).toInt(), LayoutParams.MATCH_PARENT))
        row.visibility = GONE
        header.visibility = GONE
        searchBar.visibility = VISIBLE
    }

    fun hideSearch() {
        searchBar.visibility = GONE
        row.visibility = VISIBLE
        results.removeAllViews()
        queryView.text = ""
    }

    /** [matches] are clip texts, [empty] shown when there are none */
    fun setSearchState(query: String, matches: List<String>, empty: String, onPick: (String) -> Unit) {
        queryView.text = query
        results.removeAllViews()
        if (matches.isEmpty()) {
            results.addView(TextView(context).apply {
                text = empty
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Settings.getValues().mColors.get(ColorType.KEY_HINT_TEXT))
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * density).toInt(), 0, 0, 0)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        }
        matches.forEachIndexed { i, text ->
            results.addView(chip(text.replace('\n', ' '), i == 0 && query.isNotEmpty(), 180) { onPick(text) }, chipParams())
        }
        resultScroll.scrollTo(0, 0)
    }

    val isSearchShown get() = searchBar.visibility == View.VISIBLE

    /** Header of an open tool: back to the keyboard, the tool's name, its actions on the right. */
    fun showToolHeader(title: String, actions: List<Pair<android.graphics.drawable.Drawable?, String>>, onBack: () -> Unit, onAction: (Int) -> Unit) {
        val colors = Settings.getValues().mColors
        header.removeAllViews()
        val size = LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT)
        header.addView(iconButton(R.drawable.ic_fork_keyboard, context.getString(R.string.fork_tool_back)) { onBack() }, size)
        header.addView(View(context).apply { setBackgroundColor(colors.get(ColorType.KEY_HINT_TEXT)) },
            LinearLayout.LayoutParams((1 * density).toInt().coerceAtLeast(1), (18 * density).toInt()).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (12 * density).toInt()
            })
        header.addView(TextView(context).apply {
            text = title
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(colors.get(ColorType.KEY_TEXT))
            KeyboardTypeface.applyToTextView(this)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.forEachIndexed { i, (icon, label) ->
            header.addView(iconButton(icon, label) { onAction(i) }, LinearLayout.LayoutParams((44 * density).toInt(), LayoutParams.MATCH_PARENT))
        }
        row.visibility = GONE
        searchBar.visibility = GONE
        header.visibility = VISIBLE
    }

    fun hideToolHeader() {
        header.visibility = GONE
        if (searchBar.visibility != VISIBLE) row.visibility = VISIBLE
    }
}
