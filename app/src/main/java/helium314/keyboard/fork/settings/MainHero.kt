// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
 * fork: the top of the settings' main screen: the keyboard itself (toolbar chips, the edge light, the keys), and under
 * it four big keys that pick which group of settings is listed (input, look, tools, more).
 */

private val KeyColor = Color(0xFF2B2B31)
private val FnKeyColor = Color(0xFF1E1E23)
private val ChipColor = Color(0xFF26262B)

/** a picture of the keyboard, not a working one: [caption] under it, and a button ([action]) next to it */
@Composable
fun KeyboardHero(korean: Boolean, appName: String, caption: String, action: String, onAction: () -> Unit) {
    val s = LocalShadcn.current
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(s.card)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)
    ) {
        Column(Modifier.clearAndSetSemantics { }) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(R.drawable.ic_line_clipboard, null)
                Chip(R.drawable.ic_line_sparkles, "AI")
                Chip(R.drawable.ic_line_translate, null)
                Spacer(Modifier.weight(1f))
                Chip(null, "GIF")
            }
            EdgeLight(s.primary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(caption, Modifier.weight(1f).padding(end = 10.dp), color = s.mutedForeground, fontSize = 13.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(s.muted).clickable(role = Role.Button) { onAction() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text(action, color = s.foreground, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Chip(@DrawableRes icon: Int?, text: String?) {
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(9.dp)).background(ChipColor).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(15.dp), tint = Color(0xFFD6D6DC))
        if (text != null) Text(text, color = Color(0xFFD6D6DC), fontSize = 13.sp)
    }
}

/** the toolbar's edge light: a thin line, white in the middle, with a soft bloom */
@Composable
private fun EdgeLight(color: Color) {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2
        val brush = { a: Float ->
            Brush.horizontalGradient(
                0f to Color.Transparent, 0.22f to color.copy(alpha = a), 0.5f to Color.White.copy(alpha = a),
                0.78f to color.copy(alpha = a), 1f to Color.Transparent,
            )
        }
        // bloom: wider, fainter strokes under the line
        listOf(10.dp to 0.08f, 6.dp to 0.16f, 3.dp to 0.35f).forEach { (w, a) ->
            drawLine(brush(a), Offset(4.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), w.toPx(), StrokeCap.Round)
        }
        drawLine(brush(1f), Offset(4.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), 1.6.dp.toPx(), StrokeCap.Round)
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
