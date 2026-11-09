// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import androidx.annotation.DrawableRes
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.getActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn

/*
 * fork: the top of the settings' main screen: the user's keyboard, and under it four big keys that pick which group of settings is listed (input, look, tools, more).
 */

private val KeyColor = Color(0xFF2B2B31)
private val FnKeyColor = Color(0xFF1E1E23)

/**
 * the user's keyboard as it looks: drawn here from the keyboard's own settings (theme, colors, size, font, layout,
 * language), again whenever a setting changes or the screen comes back; the drawn stand-in with [hint] only if that
 * fails. [caption] under it, and a button ([action]) next to it
 */
@Composable
fun KeyboardHero(korean: Boolean, appName: String, caption: String, hint: String, action: String, onAction: () -> Unit) {
    val s = LocalShadcn.current
    val ctx = LocalContext.current
    val changed = (ctx.getActivity() as? helium314.keyboard.settings.SettingsActivity)?.prefChanged?.collectAsState()
    var resumed by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val pic = remember(changed?.value, resumed) { KeyboardPicture.render(ctx)?.asImageBitmap() }
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(s.card)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)
    ) {
        if (pic != null) {
            Image(pic, null, Modifier.fillMaxWidth().aspectRatio(pic.width.toFloat() / pic.height)
                .clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.FillWidth)
        } else {
            DrawnKeyboard(korean, appName)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (pic == null) hint else caption, Modifier.weight(1f).padding(end = 10.dp), color = s.mutedForeground,
                fontSize = 13.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(s.muted).clickable(role = Role.Button) { onAction() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text(action, color = s.foreground, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** draws the keyboard's letter layout (current language) the way the keyboard does, without the keyboard running */
object KeyboardPicture {
    fun render(context: Context): Bitmap? = try {
        val values = Settings.getValues()
        val theme = KeyboardTheme.getKeyboardTheme(context)
        val themeContext = ContextThemeWrapper(context, theme.mStyleId)
        val editorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        val width = ResourceUtils.getKeyboardWidth(themeContext, values)
        val height = ResourceUtils.getKeyboardHeight(themeContext.resources, values)
        val layoutSet = KeyboardLayoutSet.Builder(themeContext, editorInfo, values)
            .setKeyboardGeometry(width, height)
            .setSubtype(RichInputMethodManager.getInstance().currentSubtype)
            .build()
        val keyboard = layoutSet.getKeyboard(KeyboardElement.ALPHABET)
        val view = KeyboardView(themeContext, null)
        view.setKeyboard(keyboard)
        val w = keyboard.mOccupiedWidth
        val h = keyboard.mOccupiedHeight
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // the keyboard's background (on the keyboard's frame in the real thing)
        View(themeContext).also { values.mColors.setBackground(it, ColorType.MAIN_BACKGROUND) }.background?.let {
            it.setBounds(0, 0, w, h)
            it.draw(canvas)
        }
        view.draw(canvas)
        bitmap
    } catch (e: Throwable) {
        helium314.keyboard.latin.utils.Log.w("KeyboardPicture", "can't draw the keyboard", e)
        null
    }
}

/** stands in for the picture until the keyboard was shown once */
@Composable
private fun DrawnKeyboard(korean: Boolean, appName: String) {
    Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val rows = if (korean) listOf("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ")
            else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { rows[0].forEach { Key(it.toString()) } }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            rows[1].forEach { Key(it.toString()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Key(icon = R.drawable.ic_line_shift, weight = 1.5f, fn = true)
            rows[2].forEach { Key(it.toString()) }
            Key(icon = R.drawable.ic_line_backspace, weight = 1.5f, fn = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Key("?123", weight = 1.5f, fn = true)
            Key(",", fn = true)
            Key(appName, weight = 5f, small = true)
            Key(".", fn = true)
            Key(icon = R.drawable.ic_line_enter, weight = 1.5f, accent = true)
        }
    }
}

@Composable
private fun RowScope.Key(
    text: String? = null, @DrawableRes icon: Int? = null, weight: Float = 1f,
    fn: Boolean = false, accent: Boolean = false, small: Boolean = false,
) {
    val s = LocalShadcn.current
    val bg = when { accent -> s.primary; fn -> FnKeyColor; else -> KeyColor }
    val fg = when { accent -> s.primaryForeground; fn -> Color(0xFFA3A3AD); small -> Color(0xFF6E6E78); else -> Color(0xFFECECF0) }
    Box(
        Modifier.weight(weight).height(40.dp).clip(RoundedCornerShape(8.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(19.dp), tint = fg)
        else if (text != null) Text(text, color = fg, fontSize = if (fn || small) 13.sp else 17.sp, maxLines = 1)
    }
}

/** the four keys that pick the listed group; the picked one is pressed in and lit */
@Composable
fun CategoryKeys(labels: List<String>, @DrawableRes icons: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val s = LocalShadcn.current
    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).height(72.dp).offset(y = if (on) 2.dp else 0.dp)
                    // the key's dark edge under it, gone while pressed in
                    .drawBehind {
                        if (!on) drawRoundRect(Color.Black, Offset(0f, 3.dp.toPx()), size,
                            CornerRadius(16.dp.toPx()))
                    }
                    .clip(shape).background(if (on) s.primary else Color(0xFF1A1A1E))
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                val fg = if (on) s.primaryForeground else Color(0xFFD6D6DC)
                Icon(painterResource(icons[i]), null, Modifier.size(20.dp), tint = fg)
                Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
