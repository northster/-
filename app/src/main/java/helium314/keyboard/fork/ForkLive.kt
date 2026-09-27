// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import helium314.keyboard.fork.theme.DtTheme
import helium314.keyboard.fork.theme.DtThemeStore
import helium314.keyboard.fork.theme.ForkColors
import helium314.keyboard.keyboard.KeyboardSwitcher

/**
 * fork: live preview of settings changes on the open keyboard, while a slider is still being dragged.
 * Settings and keyboard run in the same process, so the shown keyboard is changed directly:
 * colors are swapped in place ([ForkColors.update]) and the keyboard is rebuilt at most every [MIN_INTERVAL_MS],
 * without the hide / show that [KeyboardSwitcher.setThemeNeedsReload] does.
 */
object ForkLive {
    private const val MIN_INTERVAL_MS = 50L
    private val handler = Handler(Looper.getMainLooper())
    private var lastReload = 0L
    private var scheduled = false
    private val reload = Runnable {
        scheduled = false
        lastReload = SystemClock.uptimeMillis()
        KeyboardSwitcher.getInstance().reloadKeyboardLive()
    }

    /** show [theme] on the keyboard (not saved) */
    @JvmStatic
    fun previewTheme(theme: DtTheme) {
        DtThemeStore.preview = theme
        ForkColors.current()?.update(theme)
        requestReload()
    }

    /** stop overriding the saved theme selection */
    @JvmStatic
    fun endThemePreview(activeTheme: DtTheme) {
        DtThemeStore.preview = null
        ForkColors.current()?.update(activeTheme)
        requestReload()
    }

    /** rebuild the keyboard soon, e.g. after a size preference changed */
    @JvmStatic
    fun requestReload() {
        if (scheduled) return
        scheduled = true
        val wait = (lastReload + MIN_INTERVAL_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(reload, wait)
    }
}
