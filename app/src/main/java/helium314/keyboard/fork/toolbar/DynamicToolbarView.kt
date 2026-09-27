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
 * Contents: the normal row (item buttons), the paste chip bar (back | clip chips, like Samsung's keyboard),
 * the header of an open tool (back to keyboard | tool name | actions) and the clipboard search bar.
 */
class DynamicToolbarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val density = resources.displayMetrics.density
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    // paste chip bar: back to the tools on the left, the chips centered
    private val chipBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
    }
    private val chips = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
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
        row.addView(buttons, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        resultScroll.addView(results, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(chipBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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
    private fun chip(text: String, highlight: Boolean, maxWidthDp: Int, border: Boolean = false, onClick: () -> Unit) = TextView(context).apply {
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
        background = chipBackground(highlight, border)
        setOnClickListener { onClick() }
    }

    /** key colored pill, [border] as a dotted outline in the enter key color (paste chips) */
    private fun chipBackground(highlight: Boolean, border: Boolean): android.graphics.drawable.Drawable {
        val colors = Settings.getValues().mColors
        val fill = if (highlight) colors.get(ColorType.ACTION_KEY_BACKGROUND) else colors.get(ColorType.KEY_BACKGROUND)
        val enter = colors.get(ColorType.ACTION_KEY_BACKGROUND)
        if (border) return DotBorderDrawable(fill, if (highlight) colors.get(ColorType.ACTION_KEY_ICON) else enter, density)
        return GradientDrawable().apply {
            cornerRadius = 15 * density
            setColor(fill)
        }
    }

    private fun chipParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (30 * density).toInt()).apply {
        marginStart = (6 * density).toInt()
    }

    /**
     * Paste chips instead of the tools: [text] (or an image thumbnail for [imageUri]) and a [code] found in it.
     * [onBack] goes back to the tools.
     */
    fun showChipBar(text: String?, code: String?, imageUri: android.net.Uri?, onPaste: (String?) -> Unit, onBack: () -> Unit) {
        chipBar.removeAllViews()
        chips.removeAllViews()
        chipBar.addView(iconButton(R.drawable.ic_dot_left, context.getString(R.string.fork_tool_back)) { onBack() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT))
        if (imageUri != null) {
            chips.addView(imageChip(imageUri) { onPaste(null) }, chipParams())
        } else if (text != null) {
            if (code != null && code != text.trim())
                chips.addView(chip(code, true, 120, border = true) { onPaste(code) }, chipParams())
            chips.addView(chip(text.replace('\n', ' '), false, 200, border = true) { onPaste(text) }, chipParams())
        }
        chipBar.addView(chips, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        // balance the back button so the chips are centered
        chipBar.addView(View(context), LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT))
        if (header.visibility != VISIBLE && searchBar.visibility != VISIBLE) {
            row.visibility = GONE
            chipBar.visibility = VISIBLE
        }
    }

    fun hideChipBar() {
        if (chipBar.visibility != VISIBLE) return
        chipBar.visibility = GONE
        if (header.visibility != VISIBLE && searchBar.visibility != VISIBLE) row.visibility = VISIBLE
    }

    val isChipBarShown get() = chipBar.visibility == VISIBLE

    /** thumbnail + "Paste", like Samsung's image clip chip */
    private fun imageChip(uri: android.net.Uri, onClick: () -> Unit) = LinearLayout(context).apply {
        val colors = Settings.getValues().mColors
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val h = (4 * density).toInt()
        setPadding(h, 0, (12 * density).toInt(), 0)
        background = chipBackground(false, true)
        val thumb = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, 11 * density)
            }
            try { setImageURI(uri) } catch (_: Exception) { } // permission may be gone
        }
        addView(thumb, LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt()))
        addView(TextView(context).apply {
            setText(R.string.fork_clip_paste)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.get(ColorType.KEY_TEXT))
            KeyboardTypeface.applyToTextView(this)
            setPadding((8 * density).toInt(), 0, 0, 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setOnClickListener { onClick() }
    }

    fun showSearch(onClose: () -> Unit) {
        val colors = Settings.getValues().mColors
        searchBar.removeAllViews()
        searchBar.addView(iconButton(R.drawable.ic_dot_search, context.getString(R.string.fork_clip_search)) { },
            LinearLayout.LayoutParams((40 * density).toInt(), LayoutParams.MATCH_PARENT))
        queryView.setTextColor(colors.get(ColorType.KEY_TEXT))
        queryView.setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        queryView.hint = context.getString(R.string.fork_clip_search_hint)
        KeyboardTypeface.applyToTextView(queryView)
        searchBar.addView(queryView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        searchBar.addView(resultScroll, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        searchBar.addView(iconButton(R.drawable.ic_dot_close, context.getString(android.R.string.cancel)) { onClose() },
            LinearLayout.LayoutParams((40 * density).toInt(), LayoutParams.MATCH_PARENT))
        row.visibility = GONE
        header.visibility = GONE
        chipBar.visibility = GONE
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

    /** vertical swipes on the tool header (true = up), e.g. to make the clipboard panel taller */
    var onHeaderSwipe: ((Boolean) -> Unit)? = null
    private var downY = 0f
    private var downX = 0f
    private var swipeHandled = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (header.visibility != VISIBLE || onHeaderSwipe == null) return false
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; swipeHandled = false }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                if (!swipeHandled && kotlin.math.abs(dy) > touchSlop * 2 && kotlin.math.abs(dy) > kotlin.math.abs(ev.x - downX)) {
                    swipeHandled = true
                    onHeaderSwipe?.invoke(dy < 0)
                    return true // children (buttons) get a cancel
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (header.visibility != VISIBLE || onHeaderSwipe == null) return super.onTouchEvent(event)
        // swipes starting on the header background (not on a button) end up here
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; swipeHandled = false }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (!swipeHandled && kotlin.math.abs(dy) > touchSlop * 2 && kotlin.math.abs(dy) > kotlin.math.abs(event.x - downX)) {
                    swipeHandled = true
                    onHeaderSwipe?.invoke(dy < 0)
                }
            }
        }
        return true
    }

    /** Header of an open tool: back to the keyboard, the tool's name, its actions on the right. */
    fun showToolHeader(title: String, actions: List<Pair<android.graphics.drawable.Drawable?, String>>, onBack: () -> Unit, onAction: (Int) -> Unit) {
        val colors = Settings.getValues().mColors
        header.removeAllViews()
        val size = LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT)
        header.addView(iconButton(R.drawable.ic_dot_keyboard, context.getString(R.string.fork_tool_back)) { onBack() }, size)
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
        chipBar.visibility = GONE
        header.visibility = VISIBLE
    }

    private val emojiTabs = ArrayList<ImageView>()
    private var emojiTabScroll: HorizontalScrollView? = null

    /**
     * Emoji header: back to the keyboard stays on the left, the category tabs scroll sideways next to it.
     * [tabs] are icon resource and description.
     */
    fun showEmojiHeader(tabs: List<Pair<Int, String>>, selected: Int, onBack: () -> Unit,
                        onTab: (Int) -> Unit, onLongTab: (Int) -> Boolean) {
        val colors = Settings.getValues().mColors
        header.removeAllViews()
        emojiTabs.clear()
        header.addView(iconButton(R.drawable.ic_dot_keyboard, context.getString(R.string.fork_tool_back)) { onBack() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT))
        header.addView(View(context).apply { setBackgroundColor(colors.get(ColorType.KEY_HINT_TEXT)) },
            LinearLayout.LayoutParams((1 * density).toInt().coerceAtLeast(1), (18 * density).toInt()).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (4 * density).toInt()
            })
        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabs.forEachIndexed { i, (icon, label) ->
            val tab = ImageView(context).apply {
                setImageResource(icon)
                contentDescription = label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                ripple.let { if (it != 0) setBackgroundResource(it) else background = null }
                setOnClickListener { onTab(i) }
                setOnLongClickListener { onLongTab(i) }
            }
            emojiTabs.add(tab)
            strip.addView(tab, LinearLayout.LayoutParams((46 * density).toInt(), LayoutParams.MATCH_PARENT))
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        emojiTabScroll = scroll
        header.addView(scroll, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        setEmojiTabSelected(selected)
        row.visibility = GONE
        searchBar.visibility = GONE
        chipBar.visibility = GONE
        header.visibility = VISIBLE
    }

    fun setEmojiTabSelected(index: Int) {
        val colors = Settings.getValues().mColors
        emojiTabs.forEachIndexed { i, tab ->
            colors.setColor(tab, if (i == index) ColorType.EMOJI_CATEGORY_SELECTED else ColorType.EMOJI_CATEGORY)
        }
        val tab = emojiTabs.getOrNull(index) ?: return
        emojiTabScroll?.post { emojiTabScroll?.smoothScrollTo((tab.left - (48 * density).toInt()).coerceAtLeast(0), 0) }
    }

    fun hideToolHeader() {
        header.visibility = GONE
        if (searchBar.visibility != VISIBLE) row.visibility = VISIBLE
    }
}
