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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import helium314.keyboard.fork.ForkLive
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsSection
import helium314.keyboard.settings.SettingsSections
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

private class ColorSlot(
    val label: Int, val get: (DtTheme) -> Int, val set: (DtTheme, Int) -> DtTheme, val alpha: Boolean = false, val desc: Int? = null,
)

// order follows WM Keyboard's theme editor: board, keys, enter key, accent & popups, toolbar
private val boardSlots = listOf(
    ColorSlot(R.string.fork_color_background, { it.background }, { t, c -> t.copy(background = c) }),
)
private val keySlots = listOf(
    ColorSlot(R.string.fork_color_key, { it.key }, { t, c -> t.copy(key = c) }, alpha = true),
    ColorSlot(R.string.fork_color_key_text, { it.keyText }, { t, c -> t.copy(keyText = c) }),
    ColorSlot(R.string.fork_color_hint_text, { it.hintText }, { t, c -> t.copy(hintText = c) }),
    ColorSlot(R.string.fork_color_functional, { it.functionalKey }, { t, c -> t.copy(functionalKey = c) }, alpha = true,
        desc = R.string.fork_color_functional_desc),
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

/**
 * fork: "Theme & colors": pick a saved theme at the top, edit its colors and key shapes below.
 * The keyboard shows the selected theme while this screen is open, and follows sliders while they are dragged
 * (live preview, see [ForkLive]). Changes are saved to the theme file when a slider is released.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeColorsScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val s = LocalShadcn.current
    var version by remember { mutableStateOf(0) } // bumped on every change to re-read the store
    fun changed() { version++ }
    val themes = remember(version) { DtThemeStore.all(prefs) }
    val lightId = remember(version) { DtThemeStore.activeId(prefs, false) }
    val darkId = remember(version) { DtThemeStore.activeId(prefs, true) }
    val isNight = androidx.compose.foundation.isSystemInDarkTheme()
    var selectedId by remember { mutableStateOf(if (isNight) darkId else lightId) }
    val saved = themes.firstOrNull { it.id == selectedId } ?: themes.first()
    // unsaved state while a slider is dragged
    var draft by remember(saved) { mutableStateOf<DtTheme?>(null) }
    val current = draft ?: saved
    fun live(t: DtTheme) { draft = t; ForkLive.previewTheme(t) }
    fun update(t: DtTheme) { draft = t; DtThemeStore.save(prefs, t); changed() }
    LaunchedEffect(saved) { ForkLive.previewTheme(saved) }
    DisposableEffect(Unit) {
        onDispose {
            DtThemeStore.preview = null
            KeyboardTheme.getColorsForCurrentTheme(ctx) // switches the shared colors back to the active theme
            ForkLive.requestReload()
        }
    }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.fork_theme_colors),
        settings = emptyList(),
    ) {
        KeyboardPreviewScaffold {
            // ---- theme selection
            SettingsSection(stringResource(R.string.fork_theme_saved), listOf(
                @Composable {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        themes.forEach { theme ->
                            val selected = theme.id == current.id
                            Row(
                                Modifier.clip(MaterialTheme.shapes.small)
                                    .border(if (selected) 2.dp else 1.dp, if (selected) s.primary else s.border, MaterialTheme.shapes.small)
                                    .background(if (selected) s.primary.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable { selectedId = theme.id; expanded = null }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ThemeSwatch(theme)
                                Text(theme.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                },
                @Composable {
                    UseAsRow(stringResource(R.string.fork_theme_use_light), current.id == lightId) {
                        DtThemeStore.setActive(prefs, current.id, false); changed()
                    }
                },
                @Composable {
                    UseAsRow(stringResource(R.string.fork_theme_use_dark), current.id == darkId) {
                        DtThemeStore.setActive(prefs, current.id, true); changed()
                    }
                },
                @Composable {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        OutlineButton(stringResource(R.string.fork_theme_new)) {
                            val copy = DtThemeStore.duplicate(prefs, current, current.name + " (2)")
                            selectedId = copy.id
                            changed()
                        }
                        OutlineButton(stringResource(R.string.fork_theme_rename)) { rename = true }
                        if (themes.size > 1)
                            OutlineButton(stringResource(R.string.delete), destructive = true) { delete = true }
                    }
                },
            ))
            Text(
                stringResource(R.string.fork_theme_list_hint),
                style = MaterialTheme.typography.bodySmall,
                color = s.mutedForeground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            // ---- editor for the selected theme
            fun colorRows(slots: List<ColorSlot>) = slots.map { slot ->
                @Composable {
                    ColorRow(
                        label = stringResource(slot.label),
                        description = slot.desc?.let { stringResource(it) },
                        color = slot.get(current),
                        savedColor = slot.get(saved),
                        alpha = slot.alpha,
                        expanded = expanded == slot.label,
                        onToggle = { expanded = if (expanded == slot.label) null else slot.label },
                        onLive = { live(slot.set(saved, it)) },
                        onColor = { update(slot.set(saved, it)) },
                    )
                }
            }
            SettingsSection(stringResource(R.string.fork_theme_cat_board), colorRows(boardSlots))
            SettingsSection(stringResource(R.string.fork_theme_cat_keys), colorRows(keySlots) + listOf(
                @Composable {
                    ValueSlider(stringResource(R.string.fork_shape_key_radius), saved.keyRadius, 0f..24f, "dp",
                        onLive = { live(saved.copy(keyRadius = it)) }) {
                        update(saved.copy(keyRadius = it))
                    }
                },
                @Composable {
                    ValueSlider(stringResource(R.string.fork_shape_border_width), saved.borderWidth, 0f..3f, "dp", step = 0.5f,
                        onLive = { live(saved.copy(borderWidth = it)) }) {
                        update(saved.copy(borderWidth = it))
                    }
                },
            ))
            SettingsSection(stringResource(R.string.fork_theme_cat_enter), colorRows(enterSlots) + listOf(
                @Composable {
                    ValueSlider(stringResource(R.string.fork_shape_enter_radius), saved.enterRadius, 0f..40f, "dp",
                        onLive = { live(saved.copy(enterRadius = it)) }) {
                        update(saved.copy(enterRadius = it))
                    }
                },
            ))
            SettingsSection(stringResource(R.string.fork_theme_cat_accent), colorRows(accentSlots))
            SettingsSection(stringResource(R.string.fork_theme_cat_toolbar), colorRows(toolbarSlots))
            // things that belong to the look but are not part of a theme file
            SettingsSections(listOf(
                R.string.fork_theme_cat_display,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                    helium314.keyboard.latin.settings.Settings.PREF_THEME_DAY_NIGHT else null,
                helium314.keyboard.latin.settings.Settings.PREF_NAVBAR_COLOR,
                helium314.keyboard.latin.settings.Settings.PREF_CUSTOM_ICON_NAMES,
            ))
        }
        if (rename)
            TextInputDialog(
                onDismissRequest = { rename = false },
                onConfirmed = { update(current.copy(name = it.trim().ifEmpty { current.name })) },
                title = { Text(stringResource(R.string.fork_theme_rename)) },
                initialText = current.name,
            )
        if (delete)
            ConfirmationDialog(
                onDismissRequest = { delete = false },
                onConfirmed = {
                    DtThemeStore.delete(prefs, current.id)
                    selectedId = DtThemeStore.activeId(prefs, isNight)
                    changed()
                },
                content = { Text(stringResource(R.string.fork_theme_delete_confirm, current.name)) },
                confirmButtonText = stringResource(R.string.delete),
            )
    }
}

/** "Use as default theme" / "Use in dark mode": a check mark when active, tap to assign */
@Composable
private fun UseAsRow(text: String, active: Boolean, onClick: () -> Unit) {
    val s = LocalShadcn.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !active, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // stays enabled so "on" looks on, turning it off does nothing (another theme has to be picked)
        helium314.keyboard.settings.ShadcnSwitch(checked = active, onCheckedChange = { if (it) onClick() })
    }
}

/** shadcn "outline" button */
@Composable
private fun OutlineButton(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val s = LocalShadcn.current
    Box(
        Modifier.clip(MaterialTheme.shapes.small).border(1.dp, s.border, MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (destructive) s.destructive else s.foreground)
    }
}

@Composable
private fun ValueSlider(
    name: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, step: Float = 1f,
    onLive: (Float) -> Unit = {}, onCommit: (Float) -> Unit,
) {
    val s = LocalShadcn.current
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val shown = if (step < 1f) "%.1f".format(v) else v.roundToInt().toString()
            Text("$shown $unit", style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
        }
        helium314.keyboard.settings.ScrollSafeSlider(
            value = v,
            onValueChange = {
                val stepped = (it / step).roundToInt() * step
                if (stepped != v) { v = stepped; onLive(stepped) }
            },
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
private fun ColorRow(
    label: String, description: String?, color: Int, savedColor: Int, alpha: Boolean, expanded: Boolean,
    onToggle: () -> Unit, onLive: (Int) -> Unit, onColor: (Int) -> Unit,
) {
    val s = LocalShadcn.current
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (description != null)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = s.mutedForeground)
            }
            Text(color.hex(alpha), style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 10.dp))
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, s.border, RoundedCornerShape(6.dp)).background(Color(color)))
        }
        AnimatedVisibility(expanded) {
            // the editor starts from the saved color, so its sliders don't jump while the draft changes
            ColorEditor(savedColor, alpha, onLive, onColor)
        }
    }
}

/** hue / saturation / brightness / opacity sliders, hex field and presets */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorEditor(color: Int, alpha: Boolean, onLive: (Int) -> Unit, onColor: (Int) -> Unit) {
    val s = LocalShadcn.current
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var h by remember(color) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(color) { mutableFloatStateOf(hsv[1]) }
    var v by remember(color) { mutableFloatStateOf(hsv[2]) }
    var a by remember(color) { mutableFloatStateOf(android.graphics.Color.alpha(color) / 255f) }
    fun current() = android.graphics.Color.HSVToColor((a * 255).roundToInt(), floatArrayOf(h, sat, v))
    fun commit() = onColor(current())
    fun live() = onLive(current())
    var hexText by remember(color) { mutableStateOf(color.hex(alpha)) }

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        // live preview of the color being edited
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp))
            .border(1.dp, s.border, RoundedCornerShape(6.dp)).background(Color(current())))
        GradientSlider(stringResource(R.string.fork_color_hue), h, 0f..360f,
            Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }), { h = it; live() }, ::commit)
        GradientSlider(stringResource(R.string.fork_color_saturation), sat, 0f..1f,
            Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v))), { sat = it; live() }, ::commit)
        GradientSlider(stringResource(R.string.fork_color_brightness), v, 0f..1f,
            Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, sat, 1f))), { v = it; live() }, ::commit)
        if (alpha)
            GradientSlider(stringResource(R.string.fork_color_opacity), a, 0f..1f,
                Brush.horizontalGradient(listOf(Color.Transparent, Color.hsv(h, sat, v))), { a = it; live() }, ::commit)
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
            helium314.keyboard.settings.ScrollSafeSlider(
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
