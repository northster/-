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
    /** key of the clip the toolbar was opened for (verification code), opened only once per clip */
    private var autoOpenedKey: String? = null
    /** the toolbar was opened for a code and closes again once the chip is used or dismissed */
    private var autoOpened = false
    /** smart chip for the typed text (sum, amount, measure), shown instead of the paste chip */
    private var typedHit: helium314.keyboard.fork.smart.SmartSuggest.SmartHit? = null
    /** the toolbar was opened for [typedHit] and closes again when it is gone */
    private var typedAutoOpened = false
    /** the user closed the toolbar while a typed chip was shown: don't open it again until the chip is gone */
    private var typedSuppressed = false
    /** SwiftSlate style commands; what it shows on the toolbar while it runs, null when idle */
    private var slateView: ((DynamicToolbarView) -> Unit)? = null
    private var slateAutoOpened = false
    private val slate by lazy {
        latinIME?.let { ime -> helium314.keyboard.fork.slate.SlateRunner(ime, object : helium314.keyboard.fork.slate.SlateRunner.Ui {
            override fun showProgress(label: String, onCancel: () -> Unit) {
                aiGlow?.show(true)
                showSlate { it.showSlateProgress(context.getString(R.string.fork_slate_running, label), onCancel) }
            }
            override fun showResult(label: String, result: String, onInsert: () -> Unit, onDismiss: () -> Unit) {
                aiGlow?.show(false)
                showSlate { it.showSmartHit(label, result, onUse = onInsert, onBack = onDismiss) }
            }
            override fun hide() {
                aiGlow?.show(false)
                slateView = null
                if (slateAutoOpened) {
                    slateAutoOpened = false
                    closingForTyped = true
                    setExpanded(false, true)
                    closingForTyped = false
                } else applyChipState()
            }
        }) }
    }

    private fun showSlate(view: (DynamicToolbarView) -> Unit) {
        slateView = view
        if (!isExpanded && isUsable()) {
            setExpanded(true, true) // no wave, that is for swipes
            slateAutoOpened = true
        }
        applyChipState()
    }
    /** pastel border glow while an AI command runs */
    private var aiGlow: AiGlow? = null

    /** settings preview: the AI glow for a few seconds; false if the keyboard is not showing */
    fun previewAiGlow(): Boolean {
        val glow = aiGlow ?: return false
        val kv = KeyboardSwitcher.getInstance().mainKeyboardView ?: return false
        if (!kv.isShown) return false
        glow.show(true)
        kv.postDelayed({ if (slateView == null) glow.show(false) }, 6000)
        return true
    }

    /** dot glow behind the keys while a chip waits behind the collapsed toolbar */
    private var hintAnimator: ValueAnimator? = null
    /** white dot wave over the keyboard (and the toolbar) when the toolbar opens / closes */
    private var waveAnimator: ValueAnimator? = null
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

    private val frameLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updatePosition()
        // the keyboard may not have been visible yet when the chip was found
        if (chip != null) {
            setHint(hintWanted())
            autoOpenForCode()
        }
    }

    /** Called with every new input view (first start, theme change, display / fold state change). */
    fun attach(newInputView: View) {
        if (clipSearch.isActive) endClipSearch(null)
        if (toolActive) {
            // the new input view starts with the letters, restore the state from before the tool
            toolActive = false
            toolKind = TOOL_NONE
            if (!expandedBeforeTool) setExpanded(false, false)
        }
        keyboardFrame?.removeOnLayoutChangeListener(frameLayoutListener)
        animator?.cancel()
        inputView = newInputView
        aiGlow?.show(false)
        aiGlow = AiGlow(newInputView) { KeyboardSwitcher.getInstance().mainKeyboardView }
        toolbar = newInputView.findViewById(R.id.dynamic_toolbar)
        setHint(false, immediate = true)
        stopWave()
        closeTranslatePanel()
        closeGifPanel()
        typedHit = null
        autofillView = null
        autofillOpened = false
        typedAutoOpened = false
        typedSuppressed = false
        panelAnimator?.cancel()
        isClipboardPanelTall = false
        keyboardFrame = newInputView.findViewById<View>(R.id.main_keyboard_frame)?.also {
            it.addOnLayoutChangeListener(frameLayoutListener)
        }
        applyHeight()
        newInputView.post { requestHighRefreshRate() } // attached to the display by then
        setToolbarItems()
        refreshPasteChips()
        // restore persisted state without animation
        hiddenFraction = if (isExpanded) 0f else 1f
        insetsIncludeToolbar = isExpanded
        updatePosition()
    }

    fun onSwipe(up: Boolean) {
        val before = isExpanded
        setExpanded(up, true)
        if (isExpanded != before) startWave(isExpanded)
    }

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
        if (!expanded && toolbar?.isChipBarShown == true) {
            // autofill suggestions shown: those are what the user closes
            if (autofillView != null) autofillView = null else dismissChip()
        }
        if (!expanded) {
            closeTranslatePanel()
            gifExpandedBefore = true // closing now anyway
            closeGifPanel()
            autoOpened = false
            autofillOpened = false
            slateAutoOpened = false
            // closed by the user while a typed chip shows: it stays closed until the chip is gone
            if (typedHit != null && !closingForTyped) typedSuppressed = true
            typedAutoOpened = false
        }
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

    /** tool whose header is shown, see TOOL_* */
    private var toolKind = TOOL_NONE

    /** Called by KeyboardSwitcher when the clipboard or emoji panel is shown. */
    fun onToolShown(tool: Int) {
        val tb = toolbar ?: return
        if (clipSearch.isActive) return
        closeGifPanel()
        if (!toolActive) {
            toolActive = true
            expandedBeforeTool = isExpanded
        }
        if (toolKind != tool) {
            // another panel, not tall
            panelAnimator?.cancel()
            isClipboardPanelTall = false
        }
        toolKind = tool
        when (tool) {
            TOOL_CLIPBOARD -> {
                val actions = ClipAction.enabled(context.prefs())
                tb.showToolHeader(context.getString(R.string.fork_toolbar_clipboard), actions.map { it.icon(context) to it.label(context) },
                    onBack = ::backToKeyboard, onAction = { i -> onClipAction(actions[i]) },
                    endItems = ToolbarItems.endItems, onEndItem = ::onItemClicked)
            }
            TOOL_EMOJI -> {
                val emoji = KeyboardSwitcher.getInstance().emojiPalettesView ?: return
                val tabs = (0 until emoji.forkTabCount()).map { emoji.forkTabIcon(it) to emoji.forkTabDescription(it) }
                tb.showEmojiHeader(tabs, emoji.forkCurrentTab(), onBack = ::backToKeyboard,
                    onTab = { i ->
                        latinIME?.mKeyboardActionListener?.onPressKey(KeyCode.NOT_SPECIFIED, 0, 1, helium314.keyboard.event.HapticEvent.KEY_PRESS)
                        emoji.forkSelectTab(i)
                    },
                    onLongTab = { i ->
                        // long press on recents clears them, like before
                        if (emoji.forkIsRecentsTab(i)) { emoji.forkClearRecents(); true } else false
                    },
                    onSearch = {
                        if (emoji.forkCanSearch())
                            latinIME?.mKeyboardActionListener?.onCodeInput(KeyCode.EMOJI_SEARCH, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                        else KeyboardSwitcher.getInstance().showToast(context.getString(R.string.fork_emoji_search_needs_dict), true)
                    })
            }
        }
        tb.onHeaderSwipe = { up -> setPanelTall(up) }
        setExpanded(true, true)
    }

    /** the emoji panel changed its category, e.g. when opened */
    fun onEmojiTabChanged(index: Int) {
        if (toolKind == TOOL_EMOJI) toolbar?.setEmojiTabSelected(index)
    }

    private fun currentPanel(): ForkTallPanel? = gifPanel ?: when (toolKind) {
        TOOL_CLIPBOARD -> KeyboardSwitcher.getInstance().clipboardHistoryView
        TOOL_EMOJI -> KeyboardSwitcher.getInstance().emojiPalettesView
        else -> null
    }

    /** Make the open panel cover about 2/3 of the screen (true) or go back to its normal height. */
    fun setPanelTall(tall: Boolean) {
        val panel = currentPanel() ?: return
        if (tall == isClipboardPanelTall || (!toolActive && gifPanel == null)) return
        isClipboardPanelTall = tall
        panelAnimator?.cancel()
        val normal = panel.forkNormalHeight()
        val screen = context.resources.displayMetrics.heightPixels
        val target = if (tall) maxOf(normal, screen * 2 / 3 - (toolbar?.height ?: 0)) else normal
        val start = maxOf(panel.forkCurrentHeight(), normal)
        panelAnimator = ValueAnimator.ofInt(start, target).apply {
            duration = 220
            interpolator = EMPHASIZED
            addUpdateListener { panel.setForkExpandedHeight((it.animatedValue as Int).takeIf { h -> h > normal } ?: 0) }
            start()
        }
    }

    /** back key: shrinks a tall panel first. Returns true if it was used. */
    fun onBackKey(): Boolean {
        if (gifTyping) {
            finishGifTyping(false)
            return true
        }
        KeyboardSwitcher.getInstance().clipboardHistoryView?.takeIf { it.forkIsSelecting }?.let {
            it.forkStopSelecting()
            return true
        }
        if (translatePanel != null) {
            closeTranslatePanel()
            return true
        }
        if (gifPanel != null && !clipSearch.isActive) {
            if (isClipboardPanelTall) setPanelTall(false) else closeGifPanel()
            return true
        }
        if (!isClipboardPanelTall) return false
        setPanelTall(false)
        return true
    }

    /** Called by KeyboardSwitcher when the letters (or something else without a header) are shown again. */
    fun onToolHidden() {
        latinIME?.clipboardHistoryManager?.pinnedOnly = false // the panel opens with all clips again
        panelAnimator?.cancel()
        isClipboardPanelTall = false
        toolKind = TOOL_NONE
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
        if (action == ClipAction.PINNED) {
            val manager = ime.clipboardHistoryManager
            manager.pinnedOnly = !manager.pinnedOnly
            KeyboardSwitcher.getInstance().clipboardHistoryView?.forkRefreshList()
            toolbar?.setHeaderActionActive(ClipAction.enabled(context.prefs()).indexOf(ClipAction.PINNED), manager.pinnedOnly,
                if (manager.pinnedOnly) R.drawable.ic_dot_pin_filled else R.drawable.ic_dot_pin)
            return
        }
        if (action == ClipAction.CLEAR_CLIPBOARD) {
            // pick the clips to delete instead of clearing everything
            val view = KeyboardSwitcher.getInstance().clipboardHistoryView ?: return
            if (view.forkIsSelecting) view.forkStopSelecting() else view.forkStartSelecting()
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
    /** the tools row, with the Claude usage lines when a claude.ai key is set */
    fun setToolbarItems() {
        val tb = toolbar ?: return
        val usage = helium314.keyboard.fork.usage.ClaudeUsage.hasKey(context.prefs())
        tb.setItems(ToolbarItems.defaultItems, ToolbarItems.endItems, ::onItemClicked, usage)
        if (usage) tb.usageView.setUsage(helium314.keyboard.fork.usage.ClaudeUsage.cached(context.prefs()))
    }

    /** new usage numbers now and then, when the keyboard shows */
    private fun refreshUsage() {
        val tb = toolbar ?: return
        if (!helium314.keyboard.fork.usage.ClaudeUsage.hasKey(context.prefs())) return
        tb.usageView.setUsage(helium314.keyboard.fork.usage.ClaudeUsage.cached(context.prefs())) // times left move on
        helium314.keyboard.fork.usage.ClaudeUsage.refreshIfOld(context) { usage ->
            if (usage != null) toolbar?.usageView?.setUsage(usage)
        }
    }

    fun refreshPasteChips() {
        val ime = latinIME ?: return
        refreshUsage()
        if (helium314.keyboard.fork.smart.SmartPrefs.enabled(context)
            && context.prefs().getBoolean(helium314.keyboard.fork.smart.SmartPrefs.CURRENCY, true))
            helium314.keyboard.fork.smart.CurrencyRates.refreshIfOld(context)
        handler.removeCallbacks(chipExpiry)
        val prefs = context.prefs()
        val now = System.currentTimeMillis()
        val testUntil = prefs.getLong(GlowPrefs.GLOW_TEST_UNTIL, 0)
        val codeTestUntil = prefs.getLong(ClipPrefs.CODE_TEST_UNTIL, 0)
        val clip = when {
            now < codeTestUntil -> ClipboardHistoryManager.RecentClip(context.getString(R.string.fork_clip_code_test_text), null,
                codeTestUntil - ClipboardHistoryManager.RECENT_TIME_MILLIS)
            now < testUntil -> ClipboardHistoryManager.RecentClip(context.getString(R.string.fork_clip_hint_test_text), null,
                testUntil - ClipboardHistoryManager.RECENT_TIME_MILLIS)
            ClipPrefs.pasteChip(prefs) -> ime.clipboardHistoryManager.getRecentClip()
            else -> null
        }
        chip = clip?.takeIf { it.key != dismissedChipKey }
        chip?.let {
            // it stops being recent after a while
            val left = ClipboardHistoryManager.RECENT_TIME_MILLIS - (System.currentTimeMillis() - it.timestamp)
            handler.postDelayed(chipExpiry, left.coerceAtLeast(0) + 500)
        }
        applyChipState()
        autoOpenForCode()
    }

    /** a verification code was copied: open the toolbar so its chip is right there */
    private fun autoOpenForCode() {
        val c = chip ?: return
        if (c.key == autoOpenedKey || isExpanded || toolActive || clipSearch.isActive || !isUsable()) return
        if (!ClipPrefs.codeAutoOpen(context.prefs()) || (c.code ?: ClipPrefs.findCode(c.text)) == null) return
        Log.i(TAG, "opening the toolbar for a verification code")
        autoOpenedKey = c.key
        setExpanded(true, true) // no wave: that is for swipes
        autoOpened = true
    }

    /** the chip was used or dismissed: back to the tools, or closed again if it was opened for a code */
    private fun closeChip() {
        dismissChip()
        if (autoOpened) {
            autoOpened = false
            setExpanded(false, true)
        } else {
            applyChipState()
        }
    }

    private fun dismissChip() {
        chip?.let { dismissedChipKey = it.key }
        // a notification code is offered once
        if (chip?.code != null) helium314.keyboard.fork.clipboard.NotificationOtpBus.clear()
        chip = null
        handler.removeCallbacks(chipExpiry)
    }

    /** show chip bar / glow for the current state */
    private fun applyChipState() {
        Log.i(TAG, "chip state: chip=${chip != null} expanded=$isExpanded tool=$toolActive search=${clipSearch.isActive} " +
            "usable=${keyboardFrame != null && isUsable()} toolbar=${toolbar != null} " +
            "keyboardView=${KeyboardSwitcher.getInstance().mainKeyboardView != null} glow=${GlowPrefs.glowEnabled(context.prefs())}")
        val tb = toolbar ?: return
        val c = chip
        val busy = toolActive || clipSearch.isActive
        val hit = typedHit
        val slateUi = slateView
        val autofill = autofillView
        if (autofill != null && isExpanded && !busy) {
            // password manager (Samsung Pass, Google ...) suggestions for this field come first
            tb.showAutofill(autofill) {
                autofillView = null
                if (autofillOpened) { autofillOpened = false; setExpanded(false, true) } else applyChipState()
            }
        } else if (slateUi != null && isExpanded && !busy) {
            slateUi(tb)
        } else if (hit != null && isExpanded && !busy) {
            tb.showSmartHit(hit.query, hit.result,
                onUse = { useTypedHit(hit) },
                onBack = { typedSuppressed = true; typedHit = null; closeTyped() })
        } else if (c != null && isExpanded && !busy) {
            val prefs = context.prefs()
            val found = if (c.imageUri == null) ClipPrefs.findActions(c.text) else emptyList()
            // a code from a notification is the one to paste
            val actions = if (c.code == null) found
                else listOf(ClipPrefs.SmartAction.Code(c.code)) + found.filter { it !is ClipPrefs.SmartAction.Code }
            tb.showChipBar(c.text?.take(300), actions, ClipPrefs.smartChips(prefs), c.imageUri,
                onPaste = { text ->
                    when {
                        c.screenshot != null -> pasteScreenshot(c.screenshot)
                        c.code != null -> pasteChip(c.code) // the notification text itself is not wanted
                        // the chip shows the start of a long clip, the whole clip is pasted
                        text != null && c.text != null && c.text.startsWith(text) -> pasteChip(c.text)
                        else -> pasteChip(text)
                    }
                },
                onAction = ::onSmartAction,
                onBack = { closeChip() })
        } else {
            tb.hideChipBar()
        }
        setHint(hintWanted())
    }

    // ---------------------------------------------------------------- smart chips for typed text

    private val textCheck = Runnable { checkTypedText() }
    private var closingForTyped = false

    /** the text or the cursor changed: look at the text before the cursor once the caches are updated */
    fun onTextChangedSoon() {
        handler.removeCallbacks(textCheck)
        handler.post(textCheck)
    }

    private fun checkTypedText() {
        val ime = latinIME ?: return
        // a typed command ("...text ?fix") comes first
        if (!toolActive && !clipSearch.isActive && slate?.onTextChanged() == true) return
        val text = ime.forkTextBeforeCursor(helium314.keyboard.fork.smart.SmartSuggest.LOOKBEHIND)?.toString().orEmpty()
        val smartContext = helium314.keyboard.fork.smart.SmartPrefs.context(context)
        val hit = if (!helium314.keyboard.fork.smart.SmartPrefs.enabled(context) || toolActive || clipSearch.isActive) null
            else helium314.keyboard.fork.smart.SmartSuggest.detect(text, smartContext)
        if (context.prefs().getBoolean(helium314.keyboard.fork.smart.SmartPrefs.DEBUG, false)) {
            val rates = if (smartContext.rates == null) " · 환율 없음" else ""
            KeyboardSwitcher.getInstance().showToast("읽음: \"${text.takeLast(16)}\" → ${hit?.result ?: "없음"}$rates", true)
        }
        if (hit == typedHit) return
        typedHit = hit
        // an amount typed while the keyboard stayed open for a while: keep the rates within the hour
        if (hit?.kind == helium314.keyboard.fork.smart.SmartSuggest.Kind.CURRENCY)
            helium314.keyboard.fork.smart.CurrencyRates.refreshIfOld(context)
        if (hit == null) {
            typedSuppressed = false
            closeTyped()
            return
        }
        // the toolbar stays as it is: closed, the glow says there is an answer (open it to see it)
        applyChipState()
    }

    /** the typed chip is gone: close the toolbar again if it was opened for it */
    private fun closeTyped() {
        if (typedAutoOpened) {
            typedAutoOpened = false
            closingForTyped = true
            setExpanded(false, true)
            closingForTyped = false
        } else {
            applyChipState()
        }
    }

    private fun useTypedHit(hit: helium314.keyboard.fork.smart.SmartSuggest.SmartHit) {
        val ime = latinIME ?: return
        ime.forkReplaceBeforeCursor(hit.replaceSpan, hit.insert)
        // the new text is looked at again with the next selection update, which clears the chip
    }

    private fun onSmartAction(action: ClipPrefs.SmartAction) {
        if (action is ClipPrefs.SmartAction.Code) {
            pasteChip(action.value)
            return
        }
        val uri = when (action) {
            is ClipPrefs.SmartAction.Link ->
                if (action.value.contains("://")) action.value else "https://${action.value}"
            is ClipPrefs.SmartAction.Phone -> "tel:" + action.value.filter { it.isDigit() || it == '+' }
            is ClipPrefs.SmartAction.Email -> "mailto:" + action.value
            else -> return
        }
        val intent = android.content.Intent(
            if (action is ClipPrefs.SmartAction.Phone) android.content.Intent.ACTION_DIAL
            else if (action is ClipPrefs.SmartAction.Email) android.content.Intent.ACTION_SENDTO
            else android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(uri)
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        closeChip()
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "no app for $uri")
            KeyboardSwitcher.getInstance().showToast(context.getString(R.string.fork_smart_no_app), true)
        }
    }

    private fun pasteChip(text: String?) {
        val ime = latinIME ?: return
        closeChip()
        if (text != null) ime.onTextInput(text)
        else ime.mKeyboardActionListener.onCodeInput(KeyCode.CLIPBOARD_PASTE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    /** screenshots aren't on the clipboard: add to the clipboard history (our own file) and paste that */
    private fun pasteScreenshot(shot: ScreenshotWatcher.Screenshot) {
        val ime = latinIME ?: return
        closeChip()
        val dao = ClipboardDao.getInstance(context) ?: return
        val time = System.currentTimeMillis()
        dao.addClipUri(time, false, shot.uri, android.content.ClipDescription(shot.name, arrayOf(shot.mime)), context)
        val entry = dao.getAll().filter { it.filename != null }.maxByOrNull { it.timeStamp } ?: return
        ime.mKeyboardActionListener.onContent(entry.getContentInfo(context))
    }

    private fun hintWanted() = GlowPrefs.glowEnabled(context.prefs()) && (chip != null || typedHit != null || autofillView != null) && !isExpanded && !toolActive && !clipSearch.isActive && isUsable()

    /** keyboard view that draws the glow */
    private var hintView: helium314.keyboard.keyboard.KeyboardView? = null

    /** glow settings changed: show the glow again with them */
    fun onGlowSettingsChanged() {
        setHint(false, immediate = true) // a new glow with the new settings
        refreshPasteChips()
    }

    private var hintGlow: DotGlowDrawable? = null
    /** fades the glow in (to 1) or out (to 0, then removes it) */
    private var fadeAnimator: ValueAnimator? = null
    private var fade = 0f

    private fun setHint(on: Boolean, immediate: Boolean = false) {
        val kv = KeyboardSwitcher.getInstance().mainKeyboardView
        val fadingOut = fadeAnimator != null && hintView != null && !fadeTargetOn
        // the smart chip glow has its own color, a clip or autofill glows in the enter key's color
        val color = glowColor()
        if (on && hintView != null && hintView === kv && hintGlowColor != color) removeHint()
        if (on && hintView != null && hintView === kv && !fadingOut) return
        if (!on && (hintView == null || (fadingOut && !immediate))) return
        Log.i(TAG, "glow ${if (on) "on" else "off"}, keyboard view ${kv != null}")
        if (!on) {
            if (immediate) removeHint() else fadeHint(false)
            return
        }
        if (kv == null) return
        if (hintView !== kv) {
            removeHint()
            val params = GlowPrefs.glow(context.prefs())
            // over the background, under the keys
            val glow = DotGlowDrawable(color, context.resources.displayMetrics.density, params)
            hintGlowColor = color
            glow.setFade(0f)
            kv.setForkUnderlay(glow)
            hintView = kv
            hintGlow = glow
            fade = 0f
            // slow breathing, noticed without being in the way
            hintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = (params.periodMs / 2).coerceAtLeast(1)
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    // redraw the keyboard only when the glow's alpha actually changed
                    if (glow.setIntensity(it.animatedValue as Float)) kv.invalidateAllKeys()
                }
                start()
            }
        }
        fadeHint(true)
    }

    private var fadeTargetOn = false
    private var hintGlowColor = 0

    /** what the toolbar would show decides the color: typed smart chip in the glow color, else the enter key's */
    private fun glowColor(): Int =
        if (autofillView == null && typedHit != null) GlowPrefs.color(context.prefs())
        else Settings.getValues().mColors.get(helium314.keyboard.latin.common.ColorType.ACTION_KEY_BACKGROUND)

    private fun fadeHint(on: Boolean) {
        val glow = hintGlow ?: return
        val kv = hintView ?: return
        fadeAnimator?.cancel()
        fadeTargetOn = on
        val target = if (on) 1f else 0f
        fadeAnimator = ValueAnimator.ofFloat(fade, target).apply {
            duration = (FADE_MILLIS * kotlin.math.abs(target - fade)).toLong().coerceAtLeast(1)
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                fade = it.animatedValue as Float
                if (glow.setFade(fade)) kv.invalidateAllKeys()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (fadeAnimator !== animation) return
                    fadeAnimator = null
                    if (!on && !cancelled) removeHint()
                }
            })
            start()
        }
    }

    private fun removeHint() {
        val a = fadeAnimator
        fadeAnimator = null
        a?.cancel()
        hintAnimator?.cancel()
        hintAnimator = null
        hintView?.setForkUnderlay(null)
        hintView = null
        hintGlow = null
        fade = 0f
    }

    /**
     * White dot wave: opening runs from the keyboard bottom up and on through the toolbar, closing runs from
     * the keyboard top down, as a straight band or a ripple from a center hidden beyond the edge it starts at. Positions are in screen
     * coordinates, so the wave keeps going across both views while the toolbar slides.
     */
    private fun startWave(up: Boolean) {
        stopWave()
        val prefs = context.prefs()
        if (!GlowPrefs.waveEnabled(prefs)) return
        val kv = KeyboardSwitcher.getInstance().mainKeyboardView ?: return
        val frame = keyboardFrame ?: return
        if (!kv.isShown || kv.height == 0) return
        val tb = toolbar
        val params = GlowPrefs.wave(prefs)
        // opening while a glow shows (clip, smart chip): the wave is in the glow's color
        val color = if (up && hintGlow != null) visibleWaveColor(hintGlowColor) else android.graphics.Color.WHITE
        val wave = DotWave(context.resources.displayMetrics.density, params, up, color)
        val drawable = DotWaveDrawable(wave)
        val loc = IntArray(2)
        kv.getLocationOnScreen(loc)
        val kvLeft = loc[0].toFloat()
        val kvTop = loc[1].toFloat()
        val kvBottom = kvTop + kv.height
        frame.getLocationOnScreen(loc)
        val toolbarTop = loc[1].toFloat() - (tb?.height ?: 0)
        val thickness = wave.thicknessPx
        val start: Float
        val end: Float
        if (wave.isRipple) {
            // center hidden below the middle of the keyboard's bottom edge (opening) or above its top edge (closing),
            // the ring spreads from there, starting right at the edge
            val halfWidth = kv.width / 2f
            wave.centerX = kvLeft + halfWidth
            start = wave.rippleDepthPx
            if (up) {
                wave.centerY = kvBottom + wave.rippleDepthPx
                // until the whole band is past the toolbar's top corners
                end = kotlin.math.hypot(halfWidth, wave.centerY - toolbarTop) + thickness
            } else {
                wave.centerY = kvTop - wave.rippleDepthPx
                // until the whole band is past the keyboard's bottom corners
                end = kotlin.math.hypot(halfWidth, kvBottom - wave.centerY) + thickness
            }
        } else {
            // the front starts at one edge and moves until the whole band has left the far edge
            start = if (up) kvBottom else kvTop
            end = if (up) toolbarTop - thickness else kvBottom + thickness
        }
        wave.position = start
        // drawn by its own view over the keys: a frame redraws only the wave, not every key (120 Hz needs short frames)
        val layer = waveLayerFor(kv) ?: return
        layer.getLocationOnScreen(loc)
        drawable.originX = loc[0].toFloat()
        drawable.originY = loc[1].toFloat()
        layer.drawable = drawable
        layer.visibility = View.VISIBLE
        layer.highFrameRate(true)
        waveAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = params.durationMs
            // a ripple slows down as it spreads out
            interpolator = if (wave.isRipple) android.view.animation.DecelerateInterpolator(1.3f)
                else android.view.animation.LinearInterpolator()
            addUpdateListener {
                wave.position = it.animatedValue as Float
                layer.getLocationOnScreen(loc)
                drawable.originX = loc[0].toFloat()
                drawable.originY = loc[1].toFloat()
                layer.invalidate()
                if (up && tb != null && tb.visibility == View.VISIBLE) tb.setWave(wave)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (waveAnimator === animation) stopWave()
                }
            })
            start()
        }
    }

    /**
     * The glow's color lightened until it stands out on the dark keys: the wave is a thin band of small dots, and a
     * dark color like the red enter key disappeared. The hue stays.
     */
    private fun visibleWaveColor(color: Int): Int {
        var t = 0f
        var c = color
        while (androidx.core.graphics.ColorUtils.calculateLuminance(c) < 0.45 && t < 0.8f) {
            t += 0.05f
            c = androidx.core.graphics.ColorUtils.blendARGB(color, android.graphics.Color.WHITE, t)
        }
        return c
    }

    private fun stopWave() {
        val a = waveAnimator
        waveAnimator = null
        a?.cancel()
        waveLayer?.let {
            it.drawable = null
            it.visibility = View.INVISIBLE
            it.highFrameRate(false)
        }
        toolbar?.setWave(null)
    }

    private var waveLayer: WaveLayer? = null

    /** the wave layer on top of the keyboard view's parent, added the first time */
    private fun waveLayerFor(kv: View): WaveLayer? {
        val parent = kv.parent as? android.widget.FrameLayout ?: return null
        waveLayer?.let { if (it.parent === parent) return it else (it.parent as? android.view.ViewGroup)?.removeView(it) }
        val layer = WaveLayer(context, kv).apply { visibility = View.INVISIBLE }
        parent.addView(layer, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        waveLayer = layer
        return layer
    }

    /**
     * Ask for the display's highest refresh rate while the keyboard is shown (same resolution only), so the wave can
     * run at 120 Hz on phones that switch down to 60 Hz. The system may still decide otherwise (power saving).
     */
    private fun requestHighRefreshRate() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        val window = latinIME?.window?.window ?: return
        val display = window.decorView.display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId == best.modeId) return
        attrs.preferredDisplayModeId = best.modeId
        runCatching { window.attributes = attrs }.onFailure { Log.w(TAG, "can't set refresh rate", it) }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val chipExpiry = Runnable { refreshPasteChips() }

    val isClipSearchActive get() = clipSearch.isActive

    // ---------------------------------------------------------------- autofill (Samsung Pass, Google Password Manager ...)

    /** inline suggestions of the autofill service for the current field, null when there are none */
    private var autofillView: View? = null
    /** the toolbar was opened for them, and closes again when they go */
    private var autofillOpened = false

    /** whether the keyboard asks the autofill service for inline suggestions (Android 11+) */
    fun autofillEnabled() = context.prefs().getBoolean(PREF_AUTOFILL, true)

    /** height of the suggestion chips: the toolbar's, a bit smaller */
    fun autofillChipHeight(): Int {
        val h = toolbar?.layoutParams?.height?.takeIf { it > 0 }
            ?: context.resources.getDimensionPixelSize(R.dimen.fork_dynamic_toolbar_height)
        return (h - 8 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    /**
     * The autofill service answered: [view] holds its suggestion chips (passwords, logins, cards), null for none.
     * Shown in the toolbar in place of the tools; the toolbar opens for them unless turned off in the settings.
     */
    fun showAutofill(view: View?) {
        autofillView = view
        if (view == null) {
            if (autofillOpened && isExpanded) {
                autofillOpened = false
                setExpanded(false, true)
            } else applyChipState()
            return
        }
        if (!isExpanded && !toolActive && !clipSearch.isActive && isUsable() && context.prefs().getBoolean(PREF_AUTOFILL_OPEN, true)) {
            setExpanded(true, true) // no wave: that is for swipes
            autofillOpened = true
        }
        applyChipState()
    }

    /** the keyboard went away: with the setting, the toolbar is closed for the next time */
    fun onWindowHidden() {
        if (isExpanded && context.prefs().getBoolean(ForkSettings.PREF_TOOLBAR_START_CLOSED, false))
            setExpanded(false, false)
    }

    /** the field is left: its suggestions are gone */
    fun onFinishInputView() {
        closeGifPanel()
        endClipSearch(null)
        closeTranslatePanel()
        if (autofillView == null) return
        autofillView = null
        if (autofillOpened && isExpanded) {
            autofillOpened = false
            setExpanded(false, false)
        } else applyChipState()
    }

    // ---------------------------------------------------------------- AI translation panel

    private var translatePanel: helium314.keyboard.fork.translate.TranslatePanel? = null

    /**
     * The translation panel over the letters (same size) and a header in the toolbar: back to the keyboard | AI
     * translation, translate button on the right. Translates the paragraph before the cursor.
     */
    private fun showTranslatePanel() {
        val ime = latinIME ?: return
        val tb = toolbar ?: return
        if (translatePanel != null) return closeTranslatePanel()
        // letters instead of the clipboard / emoji panel
        if (KeyboardSwitcher.getInstance().isShowingClipboardHistory || KeyboardSwitcher.getInstance().isShowingEmojiPalettes)
            ime.mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        val kv = KeyboardSwitcher.getInstance().mainKeyboardView ?: return
        val parent = kv.parent as? android.widget.FrameLayout ?: return
        val paragraph = translateTarget()
        val panel = helium314.keyboard.fork.translate.TranslatePanel(context, kv, context.prefs(), paragraph.trim()) { prompt, label ->
            closeTranslatePanel()
            val target = translateTarget()
            if (target.isBlank()) {
                KeyboardSwitcher.getInstance().showToast(context.getString(R.string.fork_translate_empty), true)
                return@TranslatePanel
            }
            slate?.runOnTail(helium314.keyboard.fork.slate.SlateCommand(label, prompt), target)
        }
        parent.addView(panel, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        translatePanel = panel
        if (!isExpanded) setExpanded(true, true)
        tb.showToolHeader(context.getString(R.string.fork_translate_title), emptyList(), onBack = { closeTranslatePanel() },
            onAction = { }, button = context.getString(R.string.fork_translate_go) to { panel.translate() },
            subtitle = paragraph.trim().replace('\n', ' ').ifEmpty { context.getString(R.string.fork_translate_empty_short) })
    }

    /** the paragraph before the cursor (after the last line break), what the translation works on */
    private fun translateTarget(): String {
        val text = latinIME?.forkTextBeforeCursor(helium314.keyboard.fork.slate.SlateRunner.MAX_TEXT)?.toString().orEmpty()
        return text.substring(text.lastIndexOf('\n') + 1)
    }

    private fun closeTranslatePanel() {
        val panel = translatePanel ?: return
        translatePanel = null
        (panel.parent as? android.view.ViewGroup)?.removeView(panel)
        if (!toolActive) toolbar?.hideToolHeader()
    }

    /** the search bar is a GIF search (same query typing as the clipboard search) */
    private var gifMode = false
    private var gifResults: List<helium314.keyboard.fork.gif.GifItem>? = null
    private var gifStatus: String? = null
    /** id of the GIF request running, answers of older ones are dropped */
    private var gifRequest = 0
    private var gifQuery: String? = null
    private val gifSearchSoon = Runnable { runGifSearch() }

    // ---------------------------------------------------------------- GIF panel

    /** GIFs in place of the letters, with its header in the toolbar */
    private var gifPanel: helium314.keyboard.fork.gif.GifPanel? = null
    /** what the panel shows: [GIF_ALL] (trending or [gifPanelQuery]), [GIF_RECENT], [GIF_FAVORITES] */
    private var gifFilter = GIF_ALL
    private var gifPanelQuery = ""
    private var gifPanelRequest = 0
    private var gifExpandedBefore = false
    /** the search is typed from the panel (the letters are shown for it) and goes back to it */
    private var gifFromPanel = false
    /** the search for the panel is being typed (the panel is away, the letters are shown) */
    private var gifTyping = false
    val isGifPanelShown get() = gifPanel?.visibility == View.VISIBLE

    /** the GIF panel: trending GIFs (or a search, recent, favorites) where the letters are */
    private fun showGifPanel() {
        if (!helium314.keyboard.fork.gif.GifClient.hasKey(context.prefs())) {
            KeyboardSwitcher.getInstance().showToast(context.getString(R.string.fork_gif_no_key), true)
            return
        }
        val ime = latinIME ?: return
        if (gifPanel != null || gifTyping) return
        closeTranslatePanel()
        // letters instead of the clipboard / emoji panel
        if (KeyboardSwitcher.getInstance().isShowingClipboardHistory || KeyboardSwitcher.getInstance().isShowingEmojiPalettes)
            ime.mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        gifFilter = GIF_ALL
        gifPanelQuery = ""
        if (!addGifPanelView()) return
        gifExpandedBefore = isExpanded
        if (!isExpanded) setExpanded(true, true)
        showGifHeader()
        loadGifPanel()
    }

    /** a new panel over the letters (a fresh one each time it comes back from typing a search) */
    private fun addGifPanelView(): Boolean {
        val kv = KeyboardSwitcher.getInstance().mainKeyboardView ?: return false
        val parent = kv.parent as? android.widget.FrameLayout ?: return false
        val panel = helium314.keyboard.fork.gif.GifPanel(context, kv, context.prefs()) { sendGif(it) }
        parent.addView(panel, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        gifPanel = panel
        panelAnimator?.cancel()
        isClipboardPanelTall = false
        return true
    }

    private fun removeGifPanelView() {
        val panel = gifPanel ?: return
        gifPanel = null
        gifPanelRequest++
        (panel.parent as? android.view.ViewGroup)?.removeView(panel)
    }

    private fun showGifHeader() {
        val tb = toolbar ?: return
        tb.showGifHeader(gifPanelQuery, gifFilter, onBack = { closeGifPanel() },
            // tapping the field while typing goes back to the GIFs
            onSearch = { if (gifTyping) finishGifTyping(false) else startGifTyping() },
            onRecent = { finishGifTyping(false); setGifFilter(if (gifFilter == GIF_RECENT) GIF_ALL else GIF_RECENT) },
            onFavorites = { finishGifTyping(false); setGifFilter(if (gifFilter == GIF_FAVORITES) GIF_ALL else GIF_FAVORITES) })
        // swipe up on the header for a taller panel, like the clipboard
        tb.onHeaderSwipe = { up -> setPanelTall(up) }
    }

    private fun setGifFilter(filter: Int) {
        gifFilter = filter
        showGifHeader()
        loadGifPanel()
    }

    private fun loadGifPanel() {
        val panel = gifPanel ?: return
        val prefs = context.prefs()
        val id = ++gifPanelRequest
        when (gifFilter) {
            GIF_RECENT -> panel.show(helium314.keyboard.fork.gif.GifStore.recent(prefs), context.getString(R.string.fork_gif_no_recent))
            GIF_FAVORITES -> panel.show(helium314.keyboard.fork.gif.GifStore.favorites(prefs), context.getString(R.string.fork_gif_no_favorites))
            else -> {
                panel.show(null, context.getString(R.string.fork_gif_loading))
                val query = gifPanelQuery
                val app = context.applicationContext
                // no answer for long: say so instead of loading forever
                handler.postDelayed({
                    if (id == gifPanelRequest && gifPanel === panel && panel.isLoading)
                        panel.show(emptyList(), context.getString(R.string.fork_gif_timeout))
                }, 20_000)
                Thread {
                    // an empty query gives the trending GIFs
                    val found = runCatching { helium314.keyboard.fork.gif.GifClient.search(app.prefs(), query) }
                    handler.post {
                        if (id != gifPanelRequest || gifPanel !== panel) return@post
                        val error = found.exceptionOrNull()
                        if (error != null) Log.w(TAG, "GIF search failed", error)
                        panel.show(found.getOrNull() ?: emptyList(),
                            if (error != null) context.getString(R.string.fork_gif_failed_reason, error.message ?: error.javaClass.simpleName)
                            else context.getString(R.string.fork_gif_nothing))
                    }
                }.start()
            }
        }
    }

    /**
     * The search field was tapped: the letters come back in place of the panel to type the search, the header stays
     * and its field shows what is typed. Enter shows the results in a new panel.
     */
    private fun startGifTyping() {
        if (gifPanel == null || gifTyping) return
        removeGifPanelView()
        gifTyping = true
        gifMode = true
        gifFromPanel = true
        clipSearch.start(RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype)
        toolbar?.setGifQuery("", typing = true)
    }

    /** back to the panel, with the typed search when [search] (else as it was) */
    private fun finishGifTyping(search: Boolean) {
        if (!gifTyping) return
        val query = clipSearch.query.trim()
        gifTyping = false
        gifMode = false
        gifFromPanel = false
        clipSearch.stop()
        if (search) {
            gifPanelQuery = query
            gifFilter = GIF_ALL
        }
        if (!addGifPanelView()) return closeGifPanel()
        showGifHeader()
        loadGifPanel()
    }

    private fun closeGifPanel() {
        if (gifPanel == null && !gifTyping) return
        if (gifTyping) {
            gifTyping = false
            gifMode = false
            gifFromPanel = false
            clipSearch.stop()
        }
        removeGifPanelView()
        panelAnimator?.cancel()
        isClipboardPanelTall = false
        if (!toolActive) {
            toolbar?.hideToolHeader()
            if (!gifExpandedBefore) setExpanded(false, true)
        }
    }

    private fun runGifSearch() {
        if (!gifMode || !clipSearch.isActive) return
        val query = clipSearch.query.trim()
        if (query == gifQuery) return
        gifQuery = query
        val id = ++gifRequest
        gifResults = null
        gifStatus = null
        updateSearchResults()
        val app = context.applicationContext
        Thread {
            val found = runCatching { helium314.keyboard.fork.gif.GifClient.search(app.prefs(), query) }
            handler.post {
                if (id != gifRequest || !gifMode) return@post
                gifResults = found.getOrNull()
                gifStatus = if (found.isFailure) context.getString(R.string.fork_gif_failed)
                    else context.getString(R.string.fork_gif_nothing)
                if (found.isFailure) Log.w(TAG, "GIF search failed", found.exceptionOrNull())
                updateSearchResults()
            }
        }.start()
    }

    /** send [item] to the app as a GIF, or copy it when the field takes no images */
    private fun sendGif(item: helium314.keyboard.fork.gif.GifItem) {
        val ime = latinIME ?: return
        val app = context.applicationContext
        endClipSearch(null)
        helium314.keyboard.fork.gif.GifStore.addRecent(context.prefs(), item)
        KeyboardSwitcher.getInstance().showToast(context.getString(R.string.fork_gif_sending), true)
        val editor = ime.currentInputEditorInfo
        Thread {
            val file = runCatching { helium314.keyboard.fork.gif.GifClient.download(app, item) }
                .onFailure { Log.w(TAG, "GIF download failed", it) }.getOrNull()
            handler.post {
                if (file == null) {
                    KeyboardSwitcher.getInstance().showToast(context.getString(R.string.fork_gif_failed), true)
                    return@post
                }
                val uri = runCatching {
                    androidx.core.content.FileProvider.getUriForFile(app, app.getString(R.string.clipboard_provider_authority), file)
                }.getOrNull() ?: return@post
                val now = ime.currentInputEditorInfo
                val accepts = now != null && editor != null && now.packageName == editor.packageName && now.fieldId == editor.fieldId &&
                    androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(now)
                    .any { android.content.ClipDescription.compareMimeTypes("image/gif", it) }
                val sent = accepts && runCatching {
                    androidx.core.view.inputmethod.InputConnectionCompat.commitContent(ime.currentInputConnection, now!!,
                        androidx.core.view.inputmethod.InputContentInfoCompat(uri, android.content.ClipDescription("GIF", arrayOf("image/gif")), null),
                        androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                }.getOrDefault(false)
                if (!sent) {
                    // like WM Keyboard: the clipboard keeps it within reach
                    runCatching {
                        (app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newUri(app.contentResolver, "GIF", uri))
                    }
                    KeyboardSwitcher.getInstance().showToast(context.getString(R.string.fork_gif_copied), true)
                }
            }
        }.start()
    }

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
        tb.showSearch({ endClipSearch(null) }, gifMode)
        updateSearchResults()
        setExpanded(true, true)
    }

    /** Close the search bar, [paste] goes to the app. */
    fun endClipSearch(paste: String?) {
        if (gifTyping) return finishGifTyping(false)
        if (!clipSearch.isActive) return
        clipSearch.stop()
        if (gifMode) {
            gifMode = false
            gifRequest++
            handler.removeCallbacks(gifSearchSoon)
        }
        toolbar?.hideSearch()
        refreshPasteChips()
        if (!expandedBeforeSearch) setExpanded(false, true)
        if (paste != null) latinIME?.onTextInput(paste)
    }

    /** Key input while searching. Returns true if it was used for the search and must not reach the app. */
    fun onKeyEvent(event: Event): Boolean {
        if (!clipSearch.isActive) return false
        if (gifTyping) {
            // enter: the results in the panel
            if (event.codePoint == Constants.CODE_ENTER) {
                finishGifTyping(true)
                return true
            }
            clipSearch.setCombiningSpec(RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype)
            if (!clipSearch.onEvent(event)) return false
            toolbar?.setGifQuery(clipSearch.query, typing = true)
            return true
        }
        if (event.codePoint == Constants.CODE_ENTER && gifMode) {
            // enter searches right away
            handler.removeCallbacks(gifSearchSoon)
            runGifSearch()
            return true
        }
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
        if (gifTyping) {
            tb.setGifQuery(clipSearch.query, typing = true)
            return
        }
        if (gifMode) {
            // the request waits until typing pauses
            if (clipSearch.query.trim() != gifQuery) {
                handler.removeCallbacks(gifSearchSoon)
                handler.postDelayed(gifSearchSoon, if (gifQuery == null) 0L else 700L)
            }
            tb.setGifResults(clipSearch.query, gifResults, gifStatus) { sendGif(it) }
            return
        }
        tb.setSearchState(clipSearch.query, matches(), context.getString(R.string.fork_clip_search_empty)) { endClipSearch(it) }
    }

    private fun onItemClicked(item: ToolbarItem) {
        if (clipSearch.isActive) endClipSearch(null)
        if (item.id != ToolbarItems.TRANSLATE) closeTranslatePanel()
        if (item.id != ToolbarItems.GIF) closeGifPanel()
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
            // AI commands as chips, run on the text before the cursor
            ToolbarItems.AI -> {
                // most used first
                val commands = helium314.keyboard.fork.slate.SlateCommands.byUse(context.prefs(),
                    helium314.keyboard.fork.slate.SlateCommands.custom(context.prefs())) { it.trigger }
                toolbar?.showCommandChips(commands.map { it.trigger }, onPick = { i ->
                    toolbar?.hideChipBar()
                    slate?.runOnText(commands[i])
                }, onBack = { toolbar?.hideChipBar(); applyChipState() })
                return
            }
            // AI translation of the text before the cursor, target languages as chips (most used first)
            // AI translation: language, style and purpose chosen in a panel over the keys
            ToolbarItems.TRANSLATE -> {
                showTranslatePanel()
                return
            }
            ToolbarItems.GIF -> {
                showGifPanel()
                return
            }
            ToolbarItems.MORE -> {
                val intent = android.content.Intent(context, helium314.keyboard.settings.SettingsActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                runCatching { context.startActivity(intent) }
                return
            }
            // the app's own undo / redo (Ctrl+Z / Ctrl+Shift+Z)
            ToolbarItems.UNDO, ToolbarItems.REDO -> {
                val listener = (context as? helium314.keyboard.latin.LatinIME)?.mKeyboardActionListener ?: return
                listener.onCodeInput(
                    if (item.id == ToolbarItems.UNDO) KeyCode.UNDO else KeyCode.REDO,
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
        private const val FADE_MILLIS = 450L
        const val PREF_AUTOFILL = "fork_autofill_inline"
        const val PREF_AUTOFILL_OPEN = "fork_autofill_open"
        private const val TAG = "DynamicToolbar"
        const val TOOL_NONE = 0
        private const val GIF_ALL = 0
        private const val GIF_RECENT = 1
        private const val GIF_FAVORITES = 2
        const val TOOL_CLIPBOARD = 1
        const val TOOL_EMOJI = 2

        /** the controller of the running keyboard service */
        @JvmStatic
        var current: DynamicToolbarController? = null
            private set
        // material 3 "emphasized decelerate"-like curve
        private val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}

/** fork: the toolbar wave over the keys, in its own view so the keys are not redrawn every frame; takes no touches */
private class WaveLayer(context: Context, private val keyboardView: View) : View(context) {
    var drawable: DotWaveDrawable? = null

    /**
     * As large as the keyboard view, never larger: a plain MATCH_PARENT view in the wrap_content keyboard frame takes
     * all the height it is offered (the whole screen), and the keyboard frame grows with it.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec)
            else keyboardView.measuredHeight
        setMeasuredDimension(width, height)
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun highFrameRate(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 35)
            setRequestedFrameRate(if (on) REQUESTED_FRAME_RATE_CATEGORY_HIGH else REQUESTED_FRAME_RATE_CATEGORY_DEFAULT)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val d = drawable ?: return
        d.setBounds(0, 0, width, height)
        d.draw(canvas)
    }
}
