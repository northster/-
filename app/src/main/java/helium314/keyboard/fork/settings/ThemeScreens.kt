// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import helium314.keyboard.fork.theme.DtTheme
import helium314.keyboard.fork.theme.DtThemeStore
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsSection
import helium314.keyboard.settings.ShadcnButton
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.preferences.Preference
import kotlin.math.roundToInt

object ForkThemeSettings {
    /** settings key of the "Themes" entry in the appearance screen */
    const val THEME_FILES = "fork_theme_files"
}

fun createForkThemeSettings(context: Context) = listOf(
    Setting(context, ForkThemeSettings.THEME_FILES, R.string.fork_theme_files, R.string.fork_theme_files_summary) {
        Preference(
            name = it.title,
            description = it.description,
            onClick = { SettingsDestination.navigateTo(SettingsDestination.ForkThemes) },
        ) { NextScreenIcon() }
    },
)

private fun reloadKeyboard() = KeyboardSwitcher.getInstance().setThemeNeedsReload()

// ------------------------------------------------------------------ theme list

@Composable
fun ThemeListScreen(onClickBack: () -> Unit, onEdit: (String) -> Unit) {
    val prefs = LocalContext.current.prefs()
    var version by remember { mutableStateOf(0) } // bumped on every change to re-read the store
    fun changed() { version++; reloadKeyboard() }
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.fork_theme_files),
        settings = emptyList(),
    ) {
        val themes = remember(version) { DtThemeStore.all(prefs) }
        val lightId = remember(version) { DtThemeStore.activeId(prefs, false) }
        val darkId = remember(version) { DtThemeStore.activeId(prefs, true) }
        var rename by remember { mutableStateOf<DtTheme?>(null) }
        var delete by remember { mutableStateOf<DtTheme?>(null) }
        KeyboardPreviewScaffold {
            SettingsSection(stringResource(R.string.fork_theme_saved), themes.map { theme ->
                @Composable {
                    ThemeRow(
                        theme = theme,
                        isLight = theme.id == lightId,
                        isDark = theme.id == darkId,
                        canDelete = themes.size > 1,
                        onEdit = { onEdit(theme.id) },
                        onUseLight = { DtThemeStore.setActive(prefs, theme.id, false); changed() },
                        onUseDark = { DtThemeStore.setActive(prefs, theme.id, true); changed() },
                        onDuplicate = {
                            DtThemeStore.duplicate(prefs, theme, theme.name + " (2)")
                            changed()
                        },
                        onRename = { rename = theme },
                        onDelete = { delete = theme },
                    )
                }
            })
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                ShadcnButton(stringResource(R.string.fork_theme_new), onClick = {
                    val base = DtThemeStore.active(prefs, false)
                    val copy = DtThemeStore.duplicate(prefs, base, base.name + " (2)")
                    changed()
                    onEdit(copy.id)
                })
            }
            Text(
                stringResource(R.string.fork_theme_list_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalShadcn.current.mutedForeground,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        rename?.let { theme ->
            TextInputDialog(
                onDismissRequest = { rename = null },
                onConfirmed = { DtThemeStore.save(prefs, theme.copy(name = it.trim().ifEmpty { theme.name })); changed() },
                title = { Text(stringResource(R.string.fork_theme_rename)) },
                initialText = theme.name,
            )
        }
        delete?.let { theme ->
            ConfirmationDialog(
                onDismissRequest = { delete = null },
                onConfirmed = { DtThemeStore.delete(prefs, theme.id); changed() },
                content = { Text(stringResource(R.string.fork_theme_delete_confirm, theme.name)) },
                confirmButtonText = stringResource(R.string.delete),
            )
        }
    }
}

@Composable
private fun ThemeRow(
    theme: DtTheme, isLight: Boolean, isDark: Boolean, canDelete: Boolean,
    onEdit: () -> Unit, onUseLight: () -> Unit, onUseDark: () -> Unit,
    onDuplicate: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    val s = LocalShadcn.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeSwatch(theme)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(theme.name, style = MaterialTheme.typography.bodyLarge)
            val tags = listOfNotNull(
                if (isLight) stringResource(R.string.fork_theme_used_light) else null,
                if (isDark) stringResource(R.string.fork_theme_used_dark) else null,
            )
            if (tags.isNotEmpty())
                Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(R.drawable.ic_arrow_left), stringResource(R.string.fork_theme_menu), Modifier.rotate(-90f))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.fork_theme_edit)) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(stringResource(R.string.fork_theme_use_light)) }, onClick = { menu = false; onUseLight() })
                DropdownMenuItem(text = { Text(stringResource(R.string.fork_theme_use_dark)) }, onClick = { menu = false; onUseDark() })
                DropdownMenuItem(text = { Text(stringResource(R.string.fork_theme_duplicate)) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text(stringResource(R.string.fork_theme_rename)) }, onClick = { menu = false; onRename() })
                if (canDelete)
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = s.destructive) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** tiny keyboard: board, two keys and the enter key */
@Composable
private fun ThemeSwatch(theme: DtTheme) {
    val s = LocalShadcn.current
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, s.border, RoundedCornerShape(6.dp))
            .background(Color(theme.background)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(12.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(Color(theme.key)))
        Box(Modifier.size(12.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(Color(theme.functionalKey)))
        Box(Modifier.size(18.dp, 16.dp).clip(RoundedCornerShape(if (theme.enterRadius > 8f) 8.dp else 2.dp)).background(Color(theme.enterKey)))
    }
}

// ------------------------------------------------------------------ editor

private class ColorSlot(val label: Int, val get: (DtTheme) -> Int, val set: (DtTheme, Int) -> DtTheme, val alpha: Boolean = false)

// order follows WM Keyboard's theme editor: board, keys, enter key, accent & popups, toolbar
private val boardSlots = listOf(
    ColorSlot(R.string.fork_color_background, { it.background }, { t, c -> t.copy(background = c) }),
)
private val keySlots = listOf(
    ColorSlot(R.string.fork_color_key, { it.key }, { t, c -> t.copy(key = c) }, alpha = true),
    ColorSlot(R.string.fork_color_key_text, { it.keyText }, { t, c -> t.copy(keyText = c) }),
    ColorSlot(R.string.fork_color_hint_text, { it.hintText }, { t, c -> t.copy(hintText = c) }),
    ColorSlot(R.string.fork_color_functional, { it.functionalKey }, { t, c -> t.copy(functionalKey = c) }, alpha = true),
    ColorSlot(R.string.fork_color_functional_text, { it.functionalText }, { t, c -> t.copy(functionalText = c) }),
    ColorSlot(R.string.fork_color_pressed, { it.pressed ?: it.key }, { t, c -> t.copy(pressed = c) }, alpha = true),
    ColorSlot(R.string.fork_color_border, { it.border }, { t, c -> t.copy(border = c) }, alpha = true),
)
private val enterSlots = listOf(
    ColorSlot(R.string.fork_color_enter, { it.enterKey }, { t, c -> t.copy(enterKey = c) }, alpha = true),
    ColorSlot(R.string.fork_color_enter_icon, { it.enterIcon }, { t, c -> t.copy(enterIcon = c) }),
)
private val accentSlots = listOf(
    ColorSlot(R.string.fork_color_accent, { it.accent }, { t, c -> t.copy(accent = c) }),
    ColorSlot(R.string.fork_color_popup, { it.popup }, { t, c -> t.copy(popup = c) }),
    ColorSlot(R.string.fork_color_popup_text, { it.popupText }, { t, c -> t.copy(popupText = c) }),
)
private val toolbarSlots = listOf(
    ColorSlot(R.string.fork_color_toolbar_icon, { it.toolbarIcon }, { t, c -> t.copy(toolbarIcon = c) }),
)

@Composable
fun ThemeEditorScreen(themeId: String, onClickBack: () -> Unit) {
    val prefs = LocalContext.current.prefs()
    var theme by remember(themeId) { mutableStateOf(DtThemeStore.get(prefs, themeId)) }
    val current = theme ?: return onClickBack()
    fun update(t: DtTheme) { theme = t; DtThemeStore.save(prefs, t); reloadKeyboard() }
    var expanded by remember { mutableStateOf<Int?>(null) }

    SearchSettingsScreen(onClickBack = onClickBack, title = current.name, settings = emptyList()) {
        KeyboardPreviewScaffold {
            fun colorRows(slots: List<ColorSlot>) = slots.map { slot ->
                @Composable {
                    ColorRow(
                        label = stringResource(slot.label),
                        color = slot.get(current),
                        alpha = slot.alpha,
                        expanded = expanded == slot.label,
                        onToggle = { expanded = if (expanded == slot.label) null else slot.label },
                        onColor = { update(slot.set(current, it)) },
                    )
                }
            }
            SettingsSection(stringResource(R.string.fork_theme_cat_board), colorRows(boardSlots))
            SettingsSection(stringResource(R.string.fork_theme_cat_keys), colorRows(keySlots) + listOf(
                @Composable {
                    ValueSlider(stringResource(R.string.fork_shape_key_radius), current.keyRadius, 0f..24f, "dp") {
                        update(current.copy(keyRadius = it))
                    }
                },
                @Composable {
                    ValueSlider(stringResource(R.string.fork_shape_border_width), current.borderWidth, 0f..3f, "dp", step = 0.5f) {
                        update(current.copy(borderWidth = it))
                    }
                },
            ))
            SettingsSection(stringResource(R.string.fork_theme_cat_enter), colorRows(enterSlots) + listOf(
                @Composable {
                    ValueSlider(stringResource(R.string.fork_shape_enter_radius), current.enterRadius, 0f..40f, "dp") {
                        update(current.copy(enterRadius = it))
                    }
                },
            ))
            SettingsSection(stringResource(R.string.fork_theme_cat_accent), colorRows(accentSlots))
            SettingsSection(stringResource(R.string.fork_theme_cat_toolbar), colorRows(toolbarSlots))
        }
    }
}

@Composable
private fun ValueSlider(name: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, step: Float = 1f, onCommit: (Float) -> Unit) {
    val s = LocalShadcn.current
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val shown = if (step < 1f) "%.1f".format(v) else v.roundToInt().toString()
            Text("$shown $unit", style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
        }
        Slider(
            value = v,
            onValueChange = { v = (it / step).roundToInt() * step },
            onValueChangeFinished = { onCommit(v) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = s.primary, activeTrackColor = s.primary, inactiveTrackColor = s.muted),
        )
    }
}

// ------------------------------------------------------------------ color editing

// 15 colors to start from + 6 neutrals, like WM Keyboard's picker
private val presetColors = listOf(
    0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF84CC16, 0xFF22C55E, 0xFF10B981, 0xFF14B8A6,
    0xFF06B6D4, 0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFA855F7, 0xFFEC4899, 0xFFDC2626,
    0xFFFFFFFF, 0xFFE4E4E7, 0xFF71717A, 0xFF303030, 0xFF1B1B1B, 0xFF0A0A0A,
).map { it.toInt() }

private fun Int.hex(alpha: Boolean) =
    if (alpha && ColorUtils.setAlphaComponent(this, 255) != this) "#%08X".format(this) else "#%06X".format(this and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    return when (t.length) {
        6 -> t.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        8 -> t.toLongOrNull(16)?.toInt()
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(label: String, color: Int, alpha: Boolean, expanded: Boolean, onToggle: () -> Unit, onColor: (Int) -> Unit) {
    val s = LocalShadcn.current
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(color.hex(alpha), style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 10.dp))
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, s.border, RoundedCornerShape(6.dp)).background(Color(color)))
        }
        AnimatedVisibility(expanded) {
            ColorEditor(color, alpha, onColor)
        }
    }
}

/** hue / saturation / brightness / opacity sliders, hex field and presets */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorEditor(color: Int, alpha: Boolean, onColor: (Int) -> Unit) {
    val s = LocalShadcn.current
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var h by remember(color) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(color) { mutableFloatStateOf(hsv[1]) }
    var v by remember(color) { mutableFloatStateOf(hsv[2]) }
    var a by remember(color) { mutableFloatStateOf(android.graphics.Color.alpha(color) / 255f) }
    fun current() = android.graphics.Color.HSVToColor((a * 255).roundToInt(), floatArrayOf(h, sat, v))
    fun commit() = onColor(current())
    var hexText by remember(color) { mutableStateOf(color.hex(alpha)) }

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        // live preview of the color being edited
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp))
            .border(1.dp, s.border, RoundedCornerShape(6.dp)).background(Color(current())))
        GradientSlider(stringResource(R.string.fork_color_hue), h, 0f..360f,
            Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }), { h = it }, ::commit)
        GradientSlider(stringResource(R.string.fork_color_saturation), sat, 0f..1f,
            Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v))), { sat = it }, ::commit)
        GradientSlider(stringResource(R.string.fork_color_brightness), v, 0f..1f,
            Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, sat, 1f))), { v = it }, ::commit)
        if (alpha)
            GradientSlider(stringResource(R.string.fork_color_opacity), a, 0f..1f,
                Brush.horizontalGradient(listOf(Color.Transparent, Color.hsv(h, sat, v))), { a = it }, ::commit)
        OutlinedTextField(
            value = hexText,
            onValueChange = { text ->
                hexText = text
                parseHex(text)?.let { parsed -> onColor(if (alpha) parsed else ColorUtils.setAlphaComponent(parsed, 255)) }
            },
            label = { Text("HEX") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = s.ring, unfocusedBorderColor = s.input, cursorColor = s.foreground),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            presetColors.forEach { preset ->
                // presets keep the current opacity
                val withAlpha = ColorUtils.setAlphaComponent(preset, (a * 255).roundToInt())
                Box(Modifier.size(28.dp).clip(CircleShape).border(1.dp, s.border, CircleShape)
                    .background(Color(preset)).clickable { onColor(withAlpha) })
            }
        }
    }
}

@Composable
private fun GradientSlider(
    name: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush,
    onChange: (Float) -> Unit, onCommit: () -> Unit,
) {
    val s = LocalShadcn.current
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val shown = if (range.endInclusive > 1f) value.roundToInt().toString() else "${(value * 100).roundToInt()}%"
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
        }
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(track))
            Slider(
                value = value,
                onValueChange = onChange,
                onValueChangeFinished = onCommit,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = s.primary,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}
