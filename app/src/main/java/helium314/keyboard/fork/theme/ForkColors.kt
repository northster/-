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

/**
 * fork: keyboard colors and key shapes from a [DtTheme].
 * Key backgrounds are drawn as rounded rectangles built here (instead of the theme drawables),
 * so corner radius and border can be set, and the enter key can have its own shape.
 */
class ForkColors(private val theme: DtTheme, private val density: Float) : Colors {
    override val themeStyle = KeyboardTheme.STYLE_MATERIAL
    override val hasKeyBorders = true

    private val pressedKey = theme.pressed ?: brightenOrDarken(theme.key, true)
    private val stateLists = HashMap<ColorType, ColorStateList>()
    private val filters = HashMap<ColorType, ColorFilter>()

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
    }

    override fun setBackground(view: View, color: ColorType) {
        if (view.background == null)
            view.setBackgroundColor(Color.WHITE) // white so tinting works
        setColor(view.background, color)
    }

    private fun rect(fill: Int, radiusDp: Float, withBorder: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * density
        setColor(fill)
        if (withBorder && theme.borderWidth > 0f)
            setStroke((theme.borderWidth * density).toInt().coerceAtLeast(1), theme.border)
    }

    /** normal / pressed / empty-key states like the original key drawables */
    private fun keyDrawable(normal: Int, pressed: Int, radiusDp: Float): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_empty, android.R.attr.state_pressed), rect(pressedKey, theme.keyRadius, false))
        addState(intArrayOf(android.R.attr.state_empty), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
        addState(intArrayOf(android.R.attr.state_pressed), rect(pressed, radiusDp, true))
        addState(intArrayOf(), rect(normal, radiusDp, true))
    }

    override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable = when (color) {
        ACTION_KEY_BACKGROUND, ACTION_KEY_POPUP_KEYS_BACKGROUND ->
            keyDrawable(theme.enterKey, brightenOrDarken(theme.enterKey, true), theme.enterRadius)
        FUNCTIONAL_KEY_BACKGROUND ->
            keyDrawable(theme.functionalKey, brightenOrDarken(theme.functionalKey, true), theme.keyRadius)
        POPUP_KEYS_BACKGROUND, MORE_SUGGESTIONS_WORD_BACKGROUND ->
            keyDrawable(Color.TRANSPARENT, brightenOrDarken(theme.popup, true), theme.keyRadius)
        else -> keyDrawable(theme.key, pressedKey, theme.keyRadius) // KEY_BACKGROUND, SPACE_BAR_BACKGROUND
    }
}
