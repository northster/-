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
import helium314.keyboard.fork.clipboard.ClipPrefs
import helium314.keyboard.fork.clipboard.ClipSearch
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
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
    /** clip already pasted from a chip, not offered again */
    private var dismissedChipText: String? = null
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
        keyboardFrame?.removeOnLayoutChangeListener(frameLayoutListener)
        animator?.cancel()
        inputView = newInputView
        toolbar = newInputView.findViewById(R.id.dynamic_toolbar)
        keyboardFrame = newInputView.findViewById<View>(R.id.main_keyboard_frame)?.also {
            it.addOnLayoutChangeListener(frameLayoutListener)
        }
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
        if (!expanded && clipSearch.isActive) {
            // collapsing closes the search
            expandedBeforeSearch = false
            endClipSearch(null)
            return
        }
        if (expanded) refreshPasteChips()
        isExpanded = expanded
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

    // ---------------------------------------------------------------- clipboard: paste chips and search

    private val latinIME get() = context as? LatinIME

    /** Show the latest clip (if recent) and a verification code found in it. */
    fun refreshPasteChips() {
        val tb = toolbar ?: return
        val ime = latinIME ?: return
        val prefs = context.prefs()
        val text = if (ClipPrefs.pasteChip(prefs)) ime.clipboardHistoryManager.getRecentClipText() else null
        if (text == null || text == dismissedChipText) {
            tb.setPasteChips(null, null) { }
            return
        }
        tb.setPasteChips(text.take(200), ClipPrefs.findCode(text)) { paste ->
            dismissedChipText = text
            ime.onTextInput(paste)
            tb.setPasteChips(null, null) { }
        }
    }

    val isClipSearchActive get() = clipSearch.isActive

    /** Open the search bar: the keyboard types into it instead of the app. */
    fun startClipSearch() {
        val ime = latinIME ?: return
        val tb = toolbar ?: return
        if (clipSearch.isActive) return
        // letters instead of the clipboard panel
        if (KeyboardSwitcher.getInstance().isShowingClipboardHistory)
            ime.mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        clipSearch.start(RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype)
        expandedBeforeSearch = isExpanded
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
