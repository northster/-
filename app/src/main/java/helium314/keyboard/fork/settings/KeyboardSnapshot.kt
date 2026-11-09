// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardView
import java.io.File
import java.lang.ref.WeakReference

/**
 * fork: a picture of the user's own keyboard for the top of the settings' main screen. When the keyboard hides, its
 * letter layout is drawn into a bitmap (with the keyboard's background, without the toolbar, so nothing typed or copied
 * is in it) and kept as a file of the app. A new picture is only made when the keyboard was built anew (other theme,
 * layout, size), not on every hide.
 */
object KeyboardSnapshot {
    private const val FILE = "fork_keyboard_snapshot.png"
    private var lastKeyboard: WeakReference<Keyboard>? = null

    fun file(context: Context) = File(context.filesDir, FILE)

    /** called when the keyboard hides, with the main keyboard view */
    fun capture(view: KeyboardView?) {
        try {
            if (view == null || view.width <= 0 || view.height <= 0 || !view.isShown) return
            val keyboard = view.keyboard ?: return
            val element = keyboard.mId.element
            if (element != KeyboardElement.ALPHABET && element != KeyboardElement.ALPHABET_AUTOMATIC_SHIFTED) return
            if (helium314.keyboard.fork.toolbar.ToolbarMotion.isActive) return
            val ctx = view.context.applicationContext
            val out = file(ctx)
            if (lastKeyboard?.get() === keyboard && out.exists()) return
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // the background is the keyboard frame's, drawn for the part under the keys
            (view.parent as? View)?.background?.let { bg ->
                canvas.save()
                canvas.translate(-view.left.toFloat(), -view.top.toFloat())
                bg.draw(canvas)
                canvas.restore()
            }
            view.draw(canvas)
            lastKeyboard = WeakReference(keyboard)
            Thread {
                try {
                    val tmp = File(out.parentFile, "$FILE.tmp")
                    tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    tmp.renameTo(out)
                } catch (_: Exception) {
                } finally {
                    bitmap.recycle()
                }
            }.start()
        } catch (_: Exception) {
            // a missing picture only means the drawn one is shown
        }
    }
}
