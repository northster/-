// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.theme

import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.drawable.DrawableCompat
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.ColorType.*
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.utils.brightenOrDarken
import java.lang.ref.WeakReference

/**
 * fork: keyboard colors and key shapes from a [DtTheme].
 * Key backgrounds are drawn as rounded rectangles built here (instead of the theme drawables),
 * so corner radius and border can be set, and the enter key can have its own shape.
 *
 * There is one shared instance ([shared]). Changing its theme with [update] recolors every drawable and view
 * background it handed out, so views that keep their Colors (keyboard view, frame, toolbar) follow live edits.
 */
class ForkColors private constructor(theme: DtTheme, private var density: Float) : Colors {
    override val themeStyle = KeyboardTheme.STYLE_MATERIAL
    override val hasKeyBorders = true

    var theme: DtTheme = theme
        private set
    private var pressedKey = pressedKeyColor(theme)
    private val stateLists = HashMap<ColorType, ColorStateList>()
    private val filters = HashMap<ColorType, ColorFilter>()

    private enum class Shape { KEY, KEY_PRESSED, EMPTY_PRESSED, FUNCTIONAL, FUNCTIONAL_PRESSED, ENTER, ENTER_PRESSED, POPUP, POPUP_PRESSED }
    private class ShapeRef(drawable: GradientDrawable, val shape: Shape) { val ref = WeakReference(drawable) }
    private class BackgroundRef(view: View, val color: ColorType) { val ref = WeakReference(view) }
    private class ImageRef(view: ImageView, val color: ColorType) { val ref = WeakReference(view) }
    private val shapes = ArrayList<ShapeRef>()
    private val backgrounds = ArrayList<BackgroundRef>()
    private val images = ArrayList<ImageRef>()

    override fun get(color: ColorType): Int = when (color) {
        MAIN_BACKGROUND, NAVIGATION_BAR, STRIP_BACKGROUND, MORE_SUGGESTIONS_WORD_BACKGROUND -> theme.background
        KEY_BACKGROUND, SPACE_BAR_BACKGROUND -> theme.key
        FUNCTIONAL_KEY_BACKGROUND, EMOJI_SEARCH_BACKGROUND -> theme.functionalKey
        ACTION_KEY_BACKGROUND, ACTION_KEY_POPUP_KEYS_BACKGROUND -> theme.enterKey
        ACTION_KEY_ICON -> theme.enterIcon
        KEY_TEXT, KEY_ICON, EMOJI_KEY_TEXT, EMOJI_SEARCH_TEXT, CLIPBOARD_SUGGESTION_ICON, REMOVE_SUGGESTION_ICON -> theme.keyText
        FUNCTIONAL_KEY_TEXT -> theme.functionalText
        KEY_HINT_TEXT, SPACE_BAR_TEXT -> theme.hintText
        POPUP_KEYS_BACKGROUND, KEY_PREVIEW_BACKGROUND, MORE_SUGGESTIONS_BACKGROUND, GESTURE_PREVIEW,
            AUTOFILL_BACKGROUND_CHIP, CLIPBOARD_SUGGESTION_BACKGROUND, TOOL_BAR_EXPAND_KEY_BACKGROUND -> theme.popup
        POPUP_KEY_TEXT, POPUP_KEY_ICON, KEY_PREVIEW_TEXT -> theme.popupText
        TOOL_BAR_KEY_ENABLED_BACKGROUND, EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN, SHIFT_KEY_ICON, GESTURE_TRAIL -> theme.accent
        TOOL_BAR_KEY, TOOL_BAR_EXPAND_KEY, EMOJI_CATEGORY, ONE_HANDED_MODE_BUTTON, SUGGESTED_WORD,
            SUGGESTION_AUTO_CORRECT, SUGGESTION_TYPED_WORD, SUGGESTION_VALID_WORD, MORE_SUGGESTIONS_HINT -> theme.toolbarIcon
    }

    /** Switch to [newTheme] and recolor everything handed out so far (on the main thread). */
    fun update(newTheme: DtTheme, newDensity: Float = density) {
        if (newTheme == theme && newDensity == density) return
        theme = newTheme
        density = newDensity
        pressedKey = pressedKeyColor(newTheme)
        stateLists.clear()
        filters.clear()
        if (Looper.myLooper() == Looper.getMainLooper()) recolorViews()
        else Handler(Looper.getMainLooper()).post { recolorViews() }
    }

    private fun recolorViews() {
        shapes.removeAll { ref -> ref.ref.get()?.also { style(it, ref.shape) } == null }
        backgrounds.removeAll { ref -> ref.ref.get()?.also { applyBackground(it, ref.color); it.invalidate() } == null }
        images.removeAll { ref -> ref.ref.get()?.also { it.colorFilter = filter(ref.color) } == null }
    }

    private fun stateList(color: ColorType) = stateLists.getOrPut(color) {
        val normal = get(color)
        val pressed = if (color == KEY_BACKGROUND || color == SPACE_BAR_BACKGROUND) pressedKey
            else brightenOrDarken(normal, true)
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_pressed)),
            intArrayOf(pressed, normal)
        )
    }

    private fun filter(color: ColorType) = filters.getOrPut(color) {
        BlendModeColorFilterCompat.createBlendModeColorFilterCompat(get(color), BlendModeCompat.MODULATE)!!
    }

    override fun setColor(drawable: Drawable, color: ColorType) {
        when (color) {
            KEY_TEXT, KEY_ICON, KEY_HINT_TEXT, FUNCTIONAL_KEY_TEXT, ACTION_KEY_ICON, POPUP_KEY_TEXT, POPUP_KEY_ICON,
            KEY_PREVIEW_TEXT, SHIFT_KEY_ICON, EMOJI_CATEGORY, EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN,
            REMOVE_SUGGESTION_ICON, CLIPBOARD_SUGGESTION_ICON -> drawable.colorFilter = filter(color)
            else -> {
                DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
                DrawableCompat.setTintList(drawable, stateList(color))
            }
        }
    }

    override fun setColor(view: ImageView, color: ColorType) {
        view.colorFilter = filter(color)
        images.removeAll { it.ref.get() == null || it.ref.get() === view }
        images.add(ImageRef(view, color))
    }

    override fun setBackground(view: View, color: ColorType) {
        applyBackground(view, color)
        backgrounds.removeAll { it.ref.get() == null || it.ref.get() === view }
        backgrounds.add(BackgroundRef(view, color))
    }

    private fun applyBackground(view: View, color: ColorType) {
        if (view.background == null)
            view.setBackgroundColor(Color.WHITE) // white so tinting works
        setColor(view.background, color)
    }

    private fun style(d: GradientDrawable, shape: Shape) {
        val (fill, radius, border) = when (shape) {
            Shape.KEY -> Triple(theme.key, theme.keyRadius, true)
            Shape.KEY_PRESSED -> Triple(pressedKey, theme.keyRadius, true)
            Shape.EMPTY_PRESSED -> Triple(pressedKey, theme.keyRadius, false)
            Shape.FUNCTIONAL -> Triple(theme.functionalKey, theme.keyRadius, true)
            Shape.FUNCTIONAL_PRESSED -> Triple(brightenOrDarken(theme.functionalKey, true), theme.keyRadius, true)
            Shape.ENTER -> Triple(theme.enterKey, theme.enterRadius, true)
            Shape.ENTER_PRESSED -> Triple(brightenOrDarken(theme.enterKey, true), theme.enterRadius, true)
            Shape.POPUP -> Triple(Color.TRANSPARENT, theme.keyRadius, true)
            Shape.POPUP_PRESSED -> Triple(brightenOrDarken(theme.popup, true), theme.keyRadius, true)
        }
        d.cornerRadius = radius * density
        d.setColor(fill)
        if (border && theme.borderWidth > 0f)
            d.setStroke((theme.borderWidth * density).toInt().coerceAtLeast(1), theme.border)
        else
            d.setStroke(0, Color.TRANSPARENT)
    }

    private fun rect(shape: Shape) = GradientDrawable().apply {
        this.shape = GradientDrawable.RECTANGLE
        style(this, shape)
        shapes.add(ShapeRef(this, shape))
    }

    /** normal / pressed / empty-key states like the original key drawables */
    private fun keyDrawable(normal: Shape, pressed: Shape): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_empty, android.R.attr.state_pressed), rect(Shape.EMPTY_PRESSED))
        addState(intArrayOf(android.R.attr.state_empty), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
        addState(intArrayOf(android.R.attr.state_pressed), rect(pressed))
        addState(intArrayOf(), rect(normal))
    }

    override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable {
        shapes.removeAll { it.ref.get() == null }
        return when (color) {
            ACTION_KEY_BACKGROUND, ACTION_KEY_POPUP_KEYS_BACKGROUND -> keyDrawable(Shape.ENTER, Shape.ENTER_PRESSED)
            FUNCTIONAL_KEY_BACKGROUND -> keyDrawable(Shape.FUNCTIONAL, Shape.FUNCTIONAL_PRESSED)
            POPUP_KEYS_BACKGROUND, MORE_SUGGESTIONS_WORD_BACKGROUND -> keyDrawable(Shape.POPUP, Shape.POPUP_PRESSED)
            else -> keyDrawable(Shape.KEY, Shape.KEY_PRESSED) // KEY_BACKGROUND, SPACE_BAR_BACKGROUND
        }
    }

    companion object {
        private fun pressedKeyColor(theme: DtTheme) = theme.pressed ?: brightenOrDarken(theme.key, true)

        private var instance: ForkColors? = null

        /** The shared instance, switched to [theme] if it shows something else. */
        @JvmStatic
        @Synchronized
        fun shared(theme: DtTheme, density: Float): ForkColors {
            val colors = instance ?: ForkColors(theme, density).also { instance = it }
            colors.update(theme, density)
            return colors
        }

        @JvmStatic
        fun current(): ForkColors? = instance
    }
}
