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
import helium314.keyboard.latin.utils.prefs

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

    /** room before the < back button, easier to hit near the screen edge */
    private val backMargin get() = (8 * density).toInt()

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

    /** Claude usage between the tools and settings, when a claude.ai key is set ([setItems] with showUsage) */
    val usageView = helium314.keyboard.fork.usage.UsageView(context)

    fun setItems(items: List<ToolbarItem>, endItems: List<ToolbarItem>, onClick: (ToolbarItem) -> Unit,
                 showUsage: Boolean = false) {
        buttons.removeAllViews()
        (usageView.parent as? android.view.ViewGroup)?.removeView(usageView)
        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.MAIN_BACKGROUND) // same as keyboard, looks like the keyboard grows
        if (showUsage) {
            // tools packed to the left, the usage lines in the room up to settings
            val settings = items.filter { it.id == ToolbarItems.MORE }
            for (item in items - settings.toSet()) {
                val button = iconButton(item.icon, context.getString(item.label)) { onClick(item) }.apply { tag = item.id }
                buttons.addView(button, LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT))
            }
            buttons.addView(usageView, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = (6 * density).toInt()
                marginEnd = (4 * density).toInt()
            })
            for (item in settings) {
                val button = iconButton(item.icon, context.getString(item.label)) { onClick(item) }.apply { tag = item.id }
                buttons.addView(button, LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT))
            }
        } else for (item in items) {
            val button = iconButton(item.icon, context.getString(item.label)) { onClick(item) }.apply { tag = item.id }
            buttons.addView(button, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        // | undo redo, always at the right end
        while (row.childCount > 1) row.removeViewAt(1)
        row.addView(View(context).apply { setBackgroundColor(colors.get(ColorType.KEY_HINT_TEXT)) },
            LinearLayout.LayoutParams((1 * density).toInt().coerceAtLeast(1), (18 * density).toInt()).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (4 * density).toInt()
            })
        for (item in endItems) {
            val button = iconButton(item.icon, context.getString(item.label)) { onClick(item) }.apply { tag = item.id }
            row.addView(button, LinearLayout.LayoutParams((44 * density).toInt(), LayoutParams.MATCH_PARENT))
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
        val style = helium314.keyboard.fork.clipboard.ClipPrefs.chipStyle(context.prefs())
        setTextColor(if (highlight) colors.get(ColorType.ACTION_KEY_ICON) else style.text ?: colors.get(ColorType.KEY_TEXT))
        gravity = Gravity.CENTER_VERTICAL
        val h = (12 * density).toInt()
        setPadding(h, 0, h, 0)
        background = chipBackground(highlight, border)
        setOnClickListener { onClick() }
    }

    /**
     * Pill in key colors. Paste / smart chips ([border]) follow the chip look settings: outline dotted, dashed or
     * solid (or none), outline, fill and text colors (enter key and key colors unless set), corner radius.
     * The highlighted chip (a code) keeps the enter key colors.
     */
    private fun chipBackground(highlight: Boolean, border: Boolean): android.graphics.drawable.Drawable {
        val colors = Settings.getValues().mColors
        val style = helium314.keyboard.fork.clipboard.ClipPrefs.chipStyle(context.prefs())
        val enter = colors.get(ColorType.ACTION_KEY_BACKGROUND)
        val fill = if (highlight) enter else if (border) style.background ?: colors.get(ColorType.KEY_BACKGROUND)
            else colors.get(ColorType.KEY_BACKGROUND)
        val radius = if (border) style.radiusDp else 15f
        val lineColor = if (highlight) colors.get(ColorType.ACTION_KEY_ICON) else style.borderColor ?: enter
        if (border && style.border && style.borderStyle == helium314.keyboard.fork.clipboard.ClipPrefs.BORDER_DOTS)
            return DotBorderDrawable(fill, lineColor, density, radius)
        return GradientDrawable().apply {
            cornerRadius = radius * density
            setColor(fill)
            if (border && style.border) {
                val width = (1.5f * density).toInt().coerceAtLeast(1)
                if (style.borderStyle == helium314.keyboard.fork.clipboard.ClipPrefs.BORDER_DASHED)
                    setStroke(width, lineColor, 5 * density, 3 * density)
                else setStroke(width, lineColor)
            }
        }
    }

    private fun chipParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (30 * density).toInt()).apply {
        marginStart = (6 * density).toInt()
    }

    /**
     * Paste chips instead of the tools: [text] (or an image thumbnail for [imageUri]) and what was found in it.
     * With [smart] chips the clip moves to the left and the [actions] (code, open, call, mail) sit on the right,
     * otherwise a found code is shown as a chip before the centered clip. [onBack] goes back to the tools.
     */
    fun showChipBar(
        text: String?, actions: List<helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction>, smart: Boolean,
        imageUri: android.net.Uri?, onPaste: (String?) -> Unit,
        onAction: (helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction) -> Unit, onBack: () -> Unit,
    ) {
        chipBar.removeAllViews()
        chips.removeAllViews()
        val side = (48 * density).toInt()
        chipBar.addView(iconButton(R.drawable.ic_dot_left, context.getString(R.string.fork_tool_back)) { onBack() },
            LinearLayout.LayoutParams(side, LayoutParams.MATCH_PARENT).apply { marginStart = backMargin })
        val code = actions.firstOrNull { it is helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction.Code }?.value
        if (smart && imageUri == null && text != null && actions.isNotEmpty()) {
            // smart chips: the clip on the left, what can be done with it on the right
            chips.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            chips.addView(chip(text.replace('\n', ' '), false, 400, border = true) { onPaste(text) }, chipParams())
            chipBar.addView(chips, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, (8 * density).toInt(), 0)
            }
            actions.forEach { action ->
                val isCode = action is helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction.Code
                actionRow.addView(chip(actionLabel(action), isCode, 160, border = true) { onAction(action) }, chipParams())
            }
            chipBar.addView(actionRow, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        } else {
            chips.gravity = Gravity.CENTER
            if (imageUri != null) {
                chips.addView(imageChip(imageUri) { onPaste(null) }, chipParams())
            } else if (text != null) {
                if (code != null && code != text.trim())
                    chips.addView(chip(code, true, 120, border = true) { onPaste(code) }, chipParams())
                chips.addView(chip(text.replace('\n', ' '), false, 200, border = true) { onPaste(text) }, chipParams())
            }
            chipBar.addView(chips, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            // balance the back button so the chips are centered
            chipBar.addView(View(context), LinearLayout.LayoutParams(side, LayoutParams.MATCH_PARENT))
        }
        if (header.visibility != VISIBLE && searchBar.visibility != VISIBLE) {
            row.visibility = GONE
            chipBar.visibility = VISIBLE
        }
    }

    /**
     * Smart chip for typed text (WM Keyboard style): what was recognised on the left, the answer as a highlighted
     * chip on the right; tapping it puts the answer in place of the typed text. [onBack] hides it.
     */
    fun showSmartHit(query: String, result: String, onUse: () -> Unit, onBack: () -> Unit) {
        chipBar.removeAllViews()
        chips.removeAllViews()
        chipBar.addView(iconButton(R.drawable.ic_dot_left, context.getString(R.string.fork_tool_back)) { onBack() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT).apply { marginStart = backMargin })
        chips.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        chips.addView(TextView(context).apply {
            text = "$query ="
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            KeyboardTypeface.applyToTextView(this)
            setTextColor(Settings.getValues().mColors.get(ColorType.KEY_HINT_TEXT))
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * density).toInt(), 0, 0, 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        chipBar.addView(chips, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        chipBar.addView(chip(result, true, 220, border = true) { onUse() }, chipParams().apply {
            marginEnd = (8 * density).toInt()
        })
        if (header.visibility != VISIBLE && searchBar.visibility != VISIBLE) {
            row.visibility = GONE
            chipBar.visibility = VISIBLE
        }
    }

    /** an AI command is running: its trigger and a pulsing "…", the back button cancels it */
    fun showSlateProgress(label: String, onCancel: () -> Unit) {
        chipBar.removeAllViews()
        chips.removeAllViews()
        chipBar.addView(iconButton(R.drawable.ic_dot_close, context.getString(android.R.string.cancel)) { onCancel() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT))
        // centered in the toolbar (the border glows while it runs)
        val status = TextView(context).apply {
            text = label
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            KeyboardTypeface.applyToTextView(this)
            setTextColor(Settings.getValues().mColors.get(ColorType.KEY_TEXT))
            gravity = Gravity.CENTER
        }
        chipBar.addView(status, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        chipBar.addView(View(context), LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT))
        if (header.visibility != VISIBLE && searchBar.visibility != VISIBLE) {
            row.visibility = GONE
            chipBar.visibility = VISIBLE
        }
    }

    /** the AI commands as chips, to run one on the text before the cursor */
    fun showCommandChips(labels: List<String>, onPick: (Int) -> Unit, onBack: () -> Unit) {
        chipBar.removeAllViews()
        chips.removeAllViews()
        chipBar.addView(iconButton(R.drawable.ic_dot_left, context.getString(R.string.fork_tool_back)) { onBack() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT).apply { marginStart = backMargin })
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val list = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        labels.forEachIndexed { i, label -> list.addView(chip(label, false, 200, border = true) { onPick(i) }, chipParams()) }
        scroll.addView(list, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        chipBar.addView(scroll, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        row.visibility = GONE
        header.visibility = GONE
        searchBar.visibility = GONE
        chipBar.visibility = VISIBLE
    }

    private fun actionLabel(action: helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction) = when (action) {
        is helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction.Code -> action.value
        is helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction.Link -> context.getString(R.string.fork_smart_open)
        is helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction.Phone -> context.getString(R.string.fork_smart_call)
        is helium314.keyboard.fork.clipboard.ClipPrefs.SmartAction.Email -> context.getString(R.string.fork_smart_mail)
    }

    /** autofill suggestions (the service's own chip views, scrollable) in place of the tools, with a back button */
    fun showAutofill(view: View, onBack: () -> Unit) {
        chipBar.removeAllViews()
        chips.removeAllViews()
        chipBar.addView(iconButton(R.drawable.ic_dot_left, context.getString(R.string.fork_tool_back)) { onBack() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT).apply { marginStart = backMargin })
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        chipBar.addView(view, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            val m = (4 * density).toInt()
            setMargins(0, m, m, m)
        })
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
        }
        loadThumbnail(thumb, uri, (22 * density).toInt())
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

    /**
     * Small thumbnail decoded off the main thread: setImageURI decoded the full screenshot while the keyboard
     * was being shown, which delayed it noticeably.
     */
    private fun loadThumbnail(view: ImageView, uri: android.net.Uri, sizePx: Int) {
        view.tag = uri
        Thread {
            val bitmap = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, android.util.Size(sizePx * 2, sizePx * 2), null)
                } else {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= sizePx * 2 && bounds.outHeight / (sample * 2) >= sizePx * 2) sample *= 2
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                }
            } catch (_: Exception) {
                null // permission may be gone
            }
            if (bitmap != null) view.post { if (view.tag == uri) view.setImageBitmap(bitmap) }
        }.start()
    }

    /** [gif]: GIF search, the results are GIF previews; [strip] false: only the query (results go to the GIF panel) */
    fun showSearch(onClose: () -> Unit, gif: Boolean = false, strip: Boolean = true) {
        val colors = Settings.getValues().mColors
        searchBar.removeAllViews()
        searchBar.addView(iconButton(if (gif) R.drawable.ic_dot_gif else R.drawable.ic_dot_search,
            context.getString(if (gif) R.string.fork_toolbar_gif else R.string.fork_clip_search)) { },
            LinearLayout.LayoutParams((40 * density).toInt(), LayoutParams.MATCH_PARENT))
        queryView.setTextColor(colors.get(ColorType.KEY_TEXT))
        queryView.setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        queryView.hint = context.getString(if (gif) R.string.fork_gif_search_hint else R.string.fork_clip_search_hint)
        KeyboardTypeface.applyToTextView(queryView)
        if (strip) {
            searchBar.addView(queryView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            searchBar.addView(resultScroll, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        } else searchBar.addView(queryView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchBar.addView(iconButton(R.drawable.ic_dot_close, context.getString(android.R.string.cancel)) { onClose() },
            LinearLayout.LayoutParams((40 * density).toInt(), LayoutParams.MATCH_PARENT))
        row.visibility = GONE
        header.visibility = GONE
        chipBar.visibility = GONE
        searchBar.visibility = VISIBLE
    }

    fun setSearchQuery(query: String) {
        queryView.text = query
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

    /**
     * GIF search results: animated previews at the toolbar's height ([items] null while loading, [status] instead of
     * results when there are none).
     */
    fun setGifResults(query: String, items: List<helium314.keyboard.fork.gif.GifItem>?, status: String?,
                      onPick: (helium314.keyboard.fork.gif.GifItem) -> Unit) {
        queryView.text = query
        results.removeAllViews()
        if (items.isNullOrEmpty()) {
            results.addView(TextView(context).apply {
                text = status ?: "…"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Settings.getValues().mColors.get(ColorType.KEY_HINT_TEXT))
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * density).toInt(), 0, 0, 0)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
            resultScroll.scrollTo(0, 0)
            return
        }
        val h = (height - 6 * density).toInt().coerceAtLeast((30 * density).toInt())
        for (item in items) {
            val view = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = context.getString(R.string.fork_toolbar_gif)
                clipToOutline = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 6 * density
                    setColor(Settings.getValues().mColors.get(ColorType.KEY_BACKGROUND))
                }
                setOnClickListener { onPick(item) }
            }
            val w = (h * item.aspectRatio.coerceIn(0.6f, 2.2f)).toInt()
            results.addView(view, LinearLayout.LayoutParams(w, h).apply { marginStart = (4 * density).toInt() })
            loadGifPreview(view, item.previewUrl)
        }
        resultScroll.scrollTo(0, 0)
    }

    /** downloads a preview and shows it, animated from Android 9 on (first frame before) */
    private fun loadGifPreview(view: ImageView, url: String) {
        view.tag = url
        Thread {
            val bytes = runCatching { helium314.keyboard.fork.gif.GifClient.bytes(url) }.getOrNull() ?: return@Thread
            val drawable: android.graphics.drawable.Drawable? = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
                } else {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
                }
            }.getOrNull()
            if (drawable != null) view.post {
                if (view.tag != url || !view.isAttachedToWindow) return@post
                view.setImageDrawable(drawable)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && drawable is android.graphics.drawable.AnimatedImageDrawable)
                    drawable.start()
            }
        }.start()
    }

    val isSearchShown get() = searchBar.visibility == View.VISIBLE

    /** vertical swipes on the tool header (true = up), e.g. to make the clipboard panel taller */
    var onHeaderSwipe: ((Boolean) -> Unit)? = null
    private var downY = 0f
    private var downX = 0f
    private var swipeHandled = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    /** wave running up from the keyboard into the toolbar, drawn over the toolbar contents */
    private var wave: DotWave? = null

    /** show the current state of [wave] (positions are on screen), null to stop */
    fun setWave(wave: DotWave?) {
        if (wave == null && this.wave == null) return
        this.wave = wave
        invalidate()
    }

    private val screenPos = IntArray(2)

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        val w = wave ?: return
        getLocationOnScreen(screenPos)
        w.draw(canvas, screenPos[0].toFloat(), screenPos[1].toFloat(), width.toFloat(), height.toFloat())
    }

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
    private val headerActions = ArrayList<ImageButton>()

    /** a toggle in the tool header (e.g. pinned clips only) is on: drawn in the enter key color */
    /** [icon]: another drawable for the state (pinned clips: the pin filled in while they are shown) */
    fun setHeaderActionActive(index: Int, active: Boolean, icon: Int? = null) {
        val button = headerActions.getOrNull(index) ?: return
        val colors = Settings.getValues().mColors
        if (icon != null) button.setImageResource(icon)
        if (active) button.setColorFilter(colors.get(ColorType.ACTION_KEY_BACKGROUND))
        else colors.setColor(button, ColorType.TOOL_BAR_KEY)
    }

    /** [button]: a text button at the right end, in the enter key's colors (translation: translate) */
    fun showToolHeader(title: String, actions: List<Pair<android.graphics.drawable.Drawable?, String>>, onBack: () -> Unit,
                       onAction: (Int) -> Unit, button: Pair<String, () -> Unit>? = null,
                       endItems: List<ToolbarItem> = emptyList(), onEndItem: (ToolbarItem) -> Unit = {},
                       subtitle: String? = null) {
        val colors = Settings.getValues().mColors
        header.removeAllViews()
        val size = LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT)
        // back is < in every header (no divider after it)
        header.addView(iconButton(R.drawable.ic_dot_left, context.getString(R.string.fork_tool_back)) { onBack() }, LinearLayout.LayoutParams(size).apply { marginStart = backMargin })
        header.addView(TextView(context).apply {
            text = title
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(colors.get(ColorType.KEY_TEXT))
            KeyboardTypeface.applyToTextView(this)
        }, if (subtitle == null) LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // title | subtitle (translation: the text that will be translated)
        if (subtitle != null) {
            header.addView(View(context).apply { setBackgroundColor(colors.get(ColorType.KEY_HINT_TEXT)) },
                LinearLayout.LayoutParams((1 * density).toInt().coerceAtLeast(1), (16 * density).toInt()).apply {
                    marginStart = (10 * density).toInt()
                    marginEnd = (10 * density).toInt()
                })
            header.addView(TextView(context).apply {
                text = subtitle
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END // the start of the text shows
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
                KeyboardTypeface.applyToTextView(this)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * density).toInt()
            })
        }
        headerActions.clear()
        actions.forEachIndexed { i, (icon, label) ->
            val button = iconButton(icon, label) { onAction(i) }
            headerActions.add(button)
            header.addView(button, LinearLayout.LayoutParams((44 * density).toInt(), LayoutParams.MATCH_PARENT))
        }
        // | undo redo at the right end, like the tools row
        if (endItems.isNotEmpty()) {
            header.addView(View(context).apply { setBackgroundColor(colors.get(ColorType.KEY_HINT_TEXT)) },
                LinearLayout.LayoutParams((1 * density).toInt().coerceAtLeast(1), (18 * density).toInt()).apply {
                    marginStart = (4 * density).toInt()
                    marginEnd = (4 * density).toInt()
                })
            for (item in endItems)
                header.addView(iconButton(item.icon, context.getString(item.label)) { onEndItem(item) },
                    LinearLayout.LayoutParams((44 * density).toInt(), LayoutParams.MATCH_PARENT))
        }
        if (button != null) header.addView(TextView(context).apply {
            text = button.first
            setSingleLine()
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            KeyboardTypeface.applyToTextView(this)
            setTextColor(colors.get(ColorType.ACTION_KEY_ICON))
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(colors.get(ColorType.ACTION_KEY_BACKGROUND))
            }
            setOnClickListener { button.second() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (32 * density).toInt()).apply {
            marginEnd = (8 * density).toInt()
        })
        row.visibility = GONE
        searchBar.visibility = GONE
        chipBar.visibility = GONE
        header.visibility = VISIBLE
    }

    /**
     * GIF panel header: < GIF, a search field filling the middle (tapping it types a search), recent and favorites on
     * the right; [filter] 1 = recent, 2 = favorites shown (drawn in the enter key color)
     */
    fun showGifHeader(query: String, filter: Int, onBack: () -> Unit, onSearch: () -> Unit, onRecent: () -> Unit,
                      onFavorites: () -> Unit) {
        val colors = Settings.getValues().mColors
        header.removeAllViews()
        headerActions.clear()
        header.addView(iconButton(R.drawable.ic_dot_left, context.getString(R.string.fork_tool_back)) { onBack() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT).apply { marginStart = backMargin })
        header.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_dot_gif)
            contentDescription = context.getString(R.string.fork_toolbar_gif)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            colors.setColor(this, ColorType.TOOL_BAR_KEY)
        }, LinearLayout.LayoutParams((36 * density).toInt(), LayoutParams.MATCH_PARENT))
        header.addView(TextView(context).apply {
            text = query
            hint = context.getString(R.string.fork_gif_search_hint)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colors.get(ColorType.KEY_TEXT))
            setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
            KeyboardTypeface.applyToTextView(this)
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(colors.get(ColorType.KEY_BACKGROUND))
            }
            setOnClickListener { onSearch() }
        }, LinearLayout.LayoutParams(0, (34 * density).toInt(), 1f).apply {
            marginStart = (6 * density).toInt()
            marginEnd = (4 * density).toInt()
        })
        listOf(
            Triple(R.drawable.ic_dot_emoji_recents, R.string.fork_gif_recent, onRecent),
            Triple(if (filter == 2) R.drawable.ic_dot_star_filled else R.drawable.ic_dot_star, R.string.fork_gif_favorites, onFavorites),
        ).forEachIndexed { i, (icon, label, action) ->
            val button = iconButton(icon, context.getString(label)) { action() }
            if (filter == i + 1) button.setColorFilter(colors.get(ColorType.ACTION_KEY_BACKGROUND))
            headerActions.add(button)
            header.addView(button, LinearLayout.LayoutParams((44 * density).toInt(), LayoutParams.MATCH_PARENT))
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
                        onTab: (Int) -> Unit, onLongTab: (Int) -> Boolean, onSearch: () -> Unit) {
        val colors = Settings.getValues().mColors
        header.removeAllViews()
        emojiTabs.clear()
        header.addView(iconButton(R.drawable.ic_dot_left, context.getString(R.string.fork_tool_back)) { onBack() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT).apply { marginStart = backMargin })
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
        // search always stays on the right
        header.addView(View(context).apply { setBackgroundColor(colors.get(ColorType.KEY_HINT_TEXT)) },
            LinearLayout.LayoutParams((1 * density).toInt().coerceAtLeast(1), (18 * density).toInt()).apply {
                marginStart = (4 * density).toInt()
            })
        header.addView(iconButton(R.drawable.ic_dot_search, context.getString(R.string.fork_emoji_search)) { onSearch() },
            LinearLayout.LayoutParams((48 * density).toInt(), LayoutParams.MATCH_PARENT))
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
