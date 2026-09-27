// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.toolbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.edit
import helium314.keyboard.event.Event
import helium314.keyboard.fork.ForkSettings
import helium314.keyboard.fork.clipboard.ClipAction
import helium314.keyboard.fork.clipboard.ClipPrefs
import helium314.keyboard.fork.clipboard.ClipSearch
import helium314.keyboard.fork.clipboard.ScreenshotWatcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs

/**
 * Owns the expanded / collapsed state of the dynamic toolbar and animates it.
 *
 * Layout: the toolbar is a child of InputView (which fills the whole IME window) and is placed
 * *behind* the keyboard frame. It is moved with translationY only, so animating never triggers a
 * layout pass of the keyboard.
 *
 * Insets (what the app behind sees): apps are resized when contentTopInsets change.
 *  - smooth resize (default): the insets follow the visible toolbar top on every animation frame, so the
 *    app moves together with the toolbar. The toolbar is moved via translationY, which invalidates the
 *    view tree, and every traversal calls LatinIME.onComputeInsets, so no relayout of the keyboard is needed.
 *    Costs one app relayout per frame, which may stutter in heavy apps.
 *  - otherwise the insets change exactly once per transition: at the start of expanding and at the
 *    end of collapsing (app jumps once).
 *  - with the "overlay" setting they are never changed and the toolbar covers the bottom of the app.
 *
 * The state is persisted, so it survives input view re-creation (e.g. Fold cover <-> main display)
 * and process death.
 */
class DynamicToolbarController(private val context: Context) {
    init { current = this }
    private var toolbar: DynamicToolbarView? = null
    private val clipSearch = ClipSearch()
    /** toolbar state before the search opened it */
    private var expandedBeforeSearch = false
    /** a tool (clipboard panel) is open and the toolbar shows its header */
    private var toolActive = false
    /** toolbar state before the tool opened it */
    private var expandedBeforeTool = false
    /** key of the clip whose chip was used or closed, not offered again */
    private var dismissedChipKey: String? = null
    /** recent clip offered as a paste chip, null if none */
    private var chip: ClipboardHistoryManager.RecentClip? = null
    /** hint (dot glow behind the keys, or a corner dot) while a chip waits behind the collapsed toolbar */
    private var hintAnimator: ValueAnimator? = null
    private var hintGlow: DotGlowDrawable? = null
    private var hintDot: CornerDotDrawable? = null
    private var hintHost: View? = null
    /** clipboard panel made taller by swiping up on the header */
    private var panelAnimator: ValueAnimator? = null
    var isClipboardPanelTall = false
        private set
    private var keyboardFrame: View? = null
    private var inputView: View? = null
    private var animator: ValueAnimator? = null

    var isExpanded = context.prefs().getBoolean(ForkSettings.PREF_TOOLBAR_EXPANDED, false)
        private set
    /** current slide position, 0 = fully shown, 1 = fully hidden behind keyboard */
    private var hiddenFraction = if (isExpanded) 0f else 1f
    /** whether the app should currently be resized for the toolbar */
    private var insetsIncludeToolbar = isExpanded

    private val frameLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePosition() }

    /** Called with every new input view (first start, theme change, display / fold state change). */
    fun attach(newInputView: View) {
        if (clipSearch.isActive) endClipSearch(null)
        if (toolActive) {
            // the new input view starts with the letters, restore the state from before the tool
            toolActive = false
            if (!expandedBeforeTool) setExpanded(false, false)
        }
        keyboardFrame?.removeOnLayoutChangeListener(frameLayoutListener)
        animator?.cancel()
        inputView = newInputView
        toolbar = newInputView.findViewById(R.id.dynamic_toolbar)
        setHint(false)
        panelAnimator?.cancel()
        isClipboardPanelTall = false
        keyboardFrame = newInputView.findViewById<View>(R.id.main_keyboard_frame)?.also {
            it.addOnLayoutChangeListener(frameLayoutListener)
        }
        applyHeight()
        toolbar?.setItems(ToolbarItems.defaultItems, ::onItemClicked)
        refreshPasteChips()
        // restore persisted state without animation
        hiddenFraction = if (isExpanded) 0f else 1f
        insetsIncludeToolbar = isExpanded
        updatePosition()
    }

    fun onSwipe(up: Boolean) = setExpanded(up, true)

    fun setExpanded(expanded: Boolean, animate: Boolean) {
        if (expanded == isExpanded) return
        if (!expanded && toolActive) {
            // collapsing closes the tool
            expandedBeforeTool = false
            backToKeyboard()
            return
        }
        if (!expanded && clipSearch.isActive) {
            // collapsing closes the search
            expandedBeforeSearch = false
            endClipSearch(null)
            return
        }
        // closing the toolbar while it shows a paste chip dismisses the chip
        if (!expanded && toolbar?.isChipBarShown == true) dismissChip()
        isExpanded = expanded
        applyChipState()
        context.prefs().edit { putBoolean(ForkSettings.PREF_TOOLBAR_EXPANDED, expanded) }
        Log.i(TAG, "toolbar ${if (expanded) "expanded" else "collapsed"}")

        if (expanded) {
            // resize the app once, at the start
            insetsIncludeToolbar = true
            requestInsetsUpdate()
        }
        animator?.cancel()
        val target = if (expanded) 0f else 1f
        val duration = context.prefs().getInt(ForkSettings.PREF_TOOLBAR_ANIM_DURATION, ForkSettings.DEFAULT_TOOLBAR_ANIM_DURATION)
        if (!animate || duration <= 0 || inputView?.isShown != true) {
            hiddenFraction = target
            onTransitionEnd()
            return
        }
        animator = ValueAnimator.ofFloat(hiddenFraction, target).apply {
            this.duration = (duration * kotlin.math.abs(hiddenFraction - target)).toLong().coerceAtLeast(1)
            interpolator = EMPHASIZED
            addUpdateListener {
                hiddenFraction = it.animatedValue as Float
                updatePosition()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onTransitionEnd()
                }
            })
            start()
        }
        updatePosition()
    }

    private fun onTransitionEnd() {
        if (!isExpanded && insetsIncludeToolbar) {
            // give the space back to the app once the toolbar is hidden
            insetsIncludeToolbar = false
            requestInsetsUpdate()
        }
        updatePosition()
    }

    private fun isUsable() = !Settings.getValues().mIsFloatingKeyboard && keyboardFrame?.visibility == View.VISIBLE

    /** Place the toolbar right above the keyboard frame, shifted down by the hidden part. */
    private fun updatePosition() {
        val tb = toolbar ?: return
        val frame = keyboardFrame ?: return
        val visible = isUsable() && hiddenFraction < 1f
        tb.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (tb.height == 0) {
            // not laid out yet
            tb.post { if (tb.height != 0) updatePosition() }
            return
        }
        val frameTop = frame.top + frame.translationY
        tb.translationY = frameTop - tb.bottom + hiddenFraction * tb.height
    }

    private fun requestInsetsUpdate() {
        // onComputeInsets is called on the next traversal
        inputView?.requestLayout()
    }

    /** y of the toolbar top when fully shown, in input view coordinates */
    private fun expandedTop(): Int {
        val tb = toolbar ?: return Int.MAX_VALUE
        val frame = keyboardFrame ?: return Int.MAX_VALUE
        return (frame.top + frame.translationY).toInt() - tb.height
    }

    /** Top of the area the app should be resized for (contentTopInsets), Int.MAX_VALUE if toolbar is not relevant. */
    fun insetTop(): Int {
        if (!isUsable() || !insetsIncludeToolbar) return Int.MAX_VALUE
        val prefs = context.prefs()
        if (prefs.getBoolean(ForkSettings.PREF_TOOLBAR_OVERLAY, ForkSettings.DEFAULT_TOOLBAR_OVERLAY)) return Int.MAX_VALUE
        if (prefs.getBoolean(ForkSettings.PREF_TOOLBAR_SMOOTH_RESIZE, ForkSettings.DEFAULT_TOOLBAR_SMOOTH_RESIZE)) {
            // follow the currently visible part of the toolbar, so the app moves with the animation
            val tb = toolbar ?: return Int.MAX_VALUE
            if (hiddenFraction >= 1f) return Int.MAX_VALUE
            return expandedTop() + (hiddenFraction * tb.height).toInt()
        }
        return expandedTop()
    }

    /** Top of the area that must receive touches (touchableRegion), Int.MAX_VALUE if toolbar is hidden. */
    fun touchableTop(): Int {
        if (!isUsable() || toolbar?.visibility != View.VISIBLE) return Int.MAX_VALUE
        return expandedTop()
    }

    /** toolbar height from settings (Appearance & size), default from resources */
    fun applyHeight() {
        val tb = toolbar ?: return
        val dp = ForkSettings.sizeDp(context.prefs(), ForkSettings.PREF_TOOLBAR_HEIGHT_DP)
        val px = if (dp > 0) (dp * context.resources.displayMetrics.density).toInt()
            else context.resources.getDimensionPixelSize(R.dimen.fork_dynamic_toolbar_height)
        val lp = tb.layoutParams ?: return
        if (lp.height == px) return
        lp.height = px
        tb.layoutParams = lp
        tb.post { updatePosition(); requestInsetsUpdate() }
    }

    // ---------------------------------------------------------------- tool header (clipboard panel)

    /** Called by KeyboardSwitcher when the clipboard panel is shown. */
    fun onClipboardShown() {
        val tb = toolbar ?: return
        if (clipSearch.isActive) return
        if (!toolActive) {
            toolActive = true
            expandedBeforeTool = isExpanded
        }
        val actions = ClipAction.enabled(context.prefs())
        tb.showToolHeader(context.getString(R.string.fork_toolbar_clipboard), actions.map { it.icon(context) to it.label(context) },
            onBack = ::backToKeyboard) { i -> onClipAction(actions[i]) }
        tb.onHeaderSwipe = { up -> setClipboardPanelTall(up) }
        setExpanded(true, true)
    }

    /** Make the clipboard panel cover about 2/3 of the screen (true) or go back to its normal height. */
    fun setClipboardPanelTall(tall: Boolean) {
        val panel = KeyboardSwitcher.getInstance().clipboardHistoryView ?: return
        if (tall == isClipboardPanelTall || !toolActive) return
        isClipboardPanelTall = tall
        panelAnimator?.cancel()
        val normal = panel.forkNormalHeight()
        val screen = context.resources.displayMetrics.heightPixels
        val target = if (tall) maxOf(normal, screen * 2 / 3 - (toolbar?.height ?: 0)) else normal
        val start = maxOf(panel.height, normal)
        panelAnimator = ValueAnimator.ofInt(start, target).apply {
            duration = 220
            interpolator = EMPHASIZED
            addUpdateListener { panel.setForkExpandedHeight((it.animatedValue as Int).takeIf { h -> h > normal } ?: 0) }
            start()
        }
    }

    /** back key: shrinks a tall clipboard panel first. Returns true if it was used. */
    fun onBackKey(): Boolean {
        if (!isClipboardPanelTall) return false
        setClipboardPanelTall(false)
        return true
    }

    /** Called by KeyboardSwitcher when the clipboard panel is replaced by something else. */
    fun onClipboardHidden() {
        panelAnimator?.cancel()
        isClipboardPanelTall = false
        if (!toolActive) return
        toolActive = false
        toolbar?.hideToolHeader()
        if (!expandedBeforeTool) setExpanded(false, true)
        applyChipState()
    }

    private fun backToKeyboard() {
        val ime = latinIME ?: return
        ime.mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    private fun onClipAction(action: ClipAction) {
        val ime = latinIME ?: return
        if (action == ClipAction.SEARCH) {
            startClipSearch()
            return
        }
        val code = action.code() ?: return
        ime.mKeyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    // ---------------------------------------------------------------- clipboard: paste chips and search

    private val latinIME get() = context as? LatinIME

    /** Show the latest clip (if recent) and a verification code found in it. */
    /**
     * Look for a recent clip to offer as a paste chip.
     * Collapsed toolbar: the keyboard's top edge glows. Expanded toolbar: the chip bar (back | chips) replaces the tools,
     * like Samsung's keyboard. Using the chip, going back or closing the toolbar dismisses it.
     */
    fun refreshPasteChips() {
        val ime = latinIME ?: return
        handler.removeCallbacks(chipExpiry)
        val clip = if (ClipPrefs.pasteChip(context.prefs())) ime.clipboardHistoryManager.getRecentClip() else null
        chip = clip?.takeIf { it.key != dismissedChipKey }
        chip?.let {
            // it stops being recent after a while
            val left = ClipboardHistoryManager.RECENT_TIME_MILLIS - (System.currentTimeMillis() - it.timestamp)
            handler.postDelayed(chipExpiry, left.coerceAtLeast(0) + 500)
        }
        applyChipState()
    }

    private fun dismissChip() {
        chip?.let { dismissedChipKey = it.key }
        chip = null
        handler.removeCallbacks(chipExpiry)
    }

    /** show chip bar / glow for the current state */
    private fun applyChipState() {
        val tb = toolbar ?: return
        val c = chip
        val busy = toolActive || clipSearch.isActive
        if (c != null && isExpanded && !busy) {
            tb.showChipBar(c.text?.take(300), ClipPrefs.findCode(c.text), c.imageUri,
                onPaste = { text -> if (c.screenshot != null) pasteScreenshot(c.screenshot) else pasteChip(text) },
                onBack = { dismissChip(); applyChipState() })
        } else {
            tb.hideChipBar()
        }
        setHint(c != null && !isExpanded && !busy && isUsable())
    }

    private fun pasteChip(text: String?) {
        val ime = latinIME ?: return
        dismissChip()
        applyChipState()
        if (text != null) ime.onTextInput(text)
        else ime.mKeyboardActionListener.onCodeInput(KeyCode.CLIPBOARD_PASTE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    /** screenshots aren't on the clipboard: add to the clipboard history (our own file) and paste that */
    private fun pasteScreenshot(shot: ScreenshotWatcher.Screenshot) {
        val ime = latinIME ?: return
        dismissChip()
        applyChipState()
        val dao = ClipboardDao.getInstance(context) ?: return
        val time = System.currentTimeMillis()
        dao.addClipUri(time, false, shot.uri, android.content.ClipDescription(shot.name, arrayOf(shot.mime)), context)
        val entry = dao.getAll().filter { it.filename != null }.maxByOrNull { it.timeStamp } ?: return
        ime.mKeyboardActionListener.onContent(entry.getContentInfo(context))
    }

    private fun setHint(on: Boolean) {
        val host = KeyboardSwitcher.getInstance().wrapperView
        val shown = hintGlow != null || hintDot != null
        if (on == shown && (!on || host === hintHost)) return
        // remove the old one
        hintAnimator?.cancel()
        hintHost?.let { h ->
            if (hintGlow != null && h.background === hintGlow) h.background = null
            hintDot?.let { h.overlay.remove(it) }
        }
        hintGlow = null
        hintDot = null
        hintHost = null
        if (!on || host == null) return
        val enter = Settings.getValues().mColors.get(helium314.keyboard.latin.common.ColorType.ACTION_KEY_BACKGROUND)
        val density = context.resources.displayMetrics.density
        hintHost = host
        val setIntensity: (Float) -> Unit
        if (ClipPrefs.chipHint(context.prefs()) == ClipPrefs.HINT_DOT) {
            // over the keys, top left corner
            val dot = CornerDotDrawable(enter, density).apply { setBounds(0, 0, host.width, host.height) }
            host.overlay.add(dot)
            hintDot = dot
            setIntensity = { dot.intensity = it }
        } else {
            // behind the keys (the wrapper has no background of its own), visible between them
            val glowDrawable = DotGlowDrawable(enter, density)
            host.background = glowDrawable
            hintGlow = glowDrawable
            setIntensity = { glowDrawable.intensity = it }
        }
        // slow breathing, noticed without being in the way
        hintAnimator = ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { setIntensity(it.animatedValue as Float) }
            start()
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val chipExpiry = Runnable { refreshPasteChips() }

    val isClipSearchActive get() = clipSearch.isActive

    /** Open the search bar: the keyboard types into it instead of the app. */
    fun startClipSearch() {
        val ime = latinIME ?: return
        val tb = toolbar ?: return
        if (clipSearch.isActive) return
        // the search takes over from the clipboard header, keep the toolbar state from before the panel
        val before = if (toolActive) expandedBeforeTool else isExpanded
        if (toolActive) {
            toolActive = false
            tb.hideToolHeader()
        }
        // letters instead of the clipboard panel
        if (KeyboardSwitcher.getInstance().isShowingClipboardHistory)
            ime.mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        clipSearch.start(RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype)
        expandedBeforeSearch = before
        tb.showSearch { endClipSearch(null) }
        updateSearchResults()
        setExpanded(true, true)
    }

    /** Close the search bar, [paste] goes to the app. */
    fun endClipSearch(paste: String?) {
        if (!clipSearch.isActive) return
        clipSearch.stop()
        toolbar?.hideSearch()
        refreshPasteChips()
        if (!expandedBeforeSearch) setExpanded(false, true)
        if (paste != null) latinIME?.onTextInput(paste)
    }

    /** Key input while searching. Returns true if it was used for the search and must not reach the app. */
    fun onKeyEvent(event: Event): Boolean {
        if (!clipSearch.isActive) return false
        if (event.codePoint == Constants.CODE_ENTER) {
            // enter pastes the first match
            val first = matches().firstOrNull()
            if (clipSearch.query.isNotEmpty() && first != null) endClipSearch(first)
            else endClipSearch(null)
            return true
        }
        clipSearch.setCombiningSpec(RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype)
        if (!clipSearch.onEvent(event)) return false
        updateSearchResults()
        return true
    }

    /** Text input (e.g. popup keys) while searching. Returns true if used. */
    fun onTextInput(text: String): Boolean {
        if (!clipSearch.isActive) return false
        clipSearch.onText(text)
        updateSearchResults()
        return true
    }

    private fun matches(): List<String> {
        val dao = ClipboardDao.getInstance(context) ?: return emptyList()
        return ClipSearch.filter(dao.getAll(), clipSearch.query).mapNotNull { it.text }
    }

    private fun updateSearchResults() {
        val tb = toolbar ?: return
        tb.setSearchState(clipSearch.query, matches(), context.getString(R.string.fork_clip_search_empty)) { endClipSearch(it) }
    }

    private fun onItemClicked(item: ToolbarItem) {
        if (clipSearch.isActive) endClipSearch(null)
        when (item.id) {
            // opens / closes the clipboard history panel in place of the letters (like the clipboard key)
            ToolbarItems.CLIPBOARD -> {
                val listener = (context as? helium314.keyboard.latin.LatinIME)?.mKeyboardActionListener ?: return
                listener.onCodeInput(
                    helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.CLIPBOARD,
                    helium314.keyboard.latin.common.Constants.NOT_A_COORDINATE,
                    helium314.keyboard.latin.common.Constants.NOT_A_COORDINATE, false
                )
                return
            }
        }
        // placeholders, real features will be dispatched from here
        val label = context.getString(item.label)
        KeyboardSwitcher.getInstance().showToast(context.getString(R.string.fork_toolbar_placeholder, label), true)
    }

    companion object {
        private const val TAG = "DynamicToolbar"
        /** the controller of the running keyboard service */
        @JvmStatic
        var current: DynamicToolbarController? = null
            private set
        // material 3 "emphasized decelerate"-like curve
        private val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}
