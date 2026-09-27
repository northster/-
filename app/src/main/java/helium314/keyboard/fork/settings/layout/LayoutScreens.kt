// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings.layout

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutType.Companion.displayNameId
import helium314.keyboard.latin.utils.LayoutUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsSection
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.LayoutPickerDialog
import helium314.keyboard.settings.dialogs.TextInputDialog

// fork: layout editing like WM Keyboard's: every layout type explained and previewed, layouts edited on a key grid
//  (tap a key to change it, move / add / delete keys and rows) instead of typing json in a dialog.

/** what each layout type is for */
private fun LayoutType.descriptionId() = when (this) {
    LayoutType.SYMBOLS -> R.string.fork_layout_desc_symbols
    LayoutType.MORE_SYMBOLS -> R.string.fork_layout_desc_more_symbols
    LayoutType.FUNCTIONAL -> R.string.fork_layout_desc_functional
    LayoutType.NUMBER -> R.string.fork_layout_desc_number
    LayoutType.NUMBER_ROW -> R.string.fork_layout_desc_number_row
    LayoutType.NUMPAD -> R.string.fork_layout_desc_numpad
    LayoutType.NUMPAD_LANDSCAPE -> R.string.fork_layout_desc_numpad_landscape
    LayoutType.DPAD -> R.string.fork_layout_desc_dpad
    LayoutType.PHONE -> R.string.fork_layout_desc_phone
    LayoutType.PHONE_SYMBOLS -> R.string.fork_layout_desc_phone_symbols
    LayoutType.EMOJI_BOTTOM -> R.string.fork_layout_desc_emoji_bottom
    LayoutType.CLIPBOARD_BOTTOM -> R.string.fork_layout_desc_clipboard_bottom
    LayoutType.MAIN -> R.string.fork_layout_desc_main
}

private fun layoutText(type: LayoutType, name: String, ctx: android.content.Context): String? = runCatching {
    if (LayoutUtilsCustom.isCustomLayout(name)) LayoutUtilsCustom.getLayoutFile(name, type, ctx).readText()
    else LayoutUtils.getContent(type, name, ctx)
}.getOrNull()

private fun displayName(name: String, ctx: android.content.Context) =
    if (LayoutUtilsCustom.isCustomLayout(name)) LayoutUtilsCustom.getDisplayName(name)
    else name.getStringResourceOrName("layout_", ctx)

fun layoutEditRoute(type: LayoutType, name: String) =
    SettingsDestination.ForkLayoutEdit + type.name + "/" + Uri.encode(name)

/** one layout type: what it is, the layouts to choose from with a preview, and editing */
@Composable
fun LayoutTypeScreen(type: LayoutType, onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val s = LocalShadcn.current
    val current = Settings.readDefaultLayoutName(type, prefs)
    val builtIn = LayoutUtils.getAvailableLayouts(type, ctx)
    val custom = LayoutUtilsCustom.getLayoutFiles(type, ctx).map { it.name }.sorted()
    var deleting by remember { mutableStateOf<String?>(null) }
    var jsonDialog by remember { mutableStateOf(false) }

    fun select(name: String) {
        Settings.writeDefaultLayoutName(name, type, prefs)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    SearchSettingsScreen(onClickBack = onClickBack, title = stringResource(type.displayNameId), settings = emptyList()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(type.descriptionId()), style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            SettingsSection(stringResource(R.string.fork_layout_choose), (builtIn + custom).map { name ->
                @Composable {
                    val isCustom = name in custom
                    Column(Modifier.fillMaxWidth().clickable { select(name) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = name == current, onClick = { select(name) },
                                colors = RadioButtonDefaults.colors(selectedColor = s.primary))
                            Text(displayName(name, ctx), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { SettingsDestination.navigateTo(layoutEditRoute(type, name)) }) {
                                Text(stringResource(if (isCustom) R.string.fork_layout_edit else R.string.fork_layout_copy_edit),
                                    style = MaterialTheme.typography.labelLarge)
                            }
                            if (isCustom)
                                TextButton(onClick = { deleting = name }) {
                                    Text(stringResource(R.string.delete), color = s.destructive, style = MaterialTheme.typography.labelLarge)
                                }
                        }
                        val rows = remember(name) { layoutText(type, name, ctx)?.let { runCatching { EditableLayout.parse(it) }.getOrNull() } }
                        if (rows != null) KeyGrid(rows, selected = null, small = true, onKey = null,
                            modifier = Modifier.padding(start = 48.dp, end = 8.dp, top = 4.dp))
                    }
                }
            })
            TextButton(onClick = { jsonDialog = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.fork_layout_json_advanced), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    deleting?.let { name ->
        ConfirmationDialog(
            onDismissRequest = { deleting = null },
            onConfirmed = { LayoutUtilsCustom.deleteLayout(name, type, ctx); (ctx.getActivity() as? SettingsActivity)?.prefChanged() },
            content = { Text(stringResource(R.string.delete_layout, displayName(name, ctx))) },
            confirmButtonText = stringResource(R.string.delete),
        )
    }
    if (jsonDialog)
        LayoutPickerDialog(onDismissRequest = { jsonDialog = false },
            setting = helium314.keyboard.settings.Setting(ctx, Settings.PREF_LAYOUT_PREFIX + type.name, type.displayNameId) {},
            layoutType = type)
}

/** Visual editor: the keys on a grid, the selected key's fields and actions below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutEditorScreen(type: LayoutType, layoutName: String, onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val s = LocalShadcn.current
    val isCustom = LayoutUtilsCustom.isCustomLayout(layoutName)
    var rows by remember { mutableStateOf(layoutText(type, layoutName, ctx)?.let { runCatching { EditableLayout.parse(it) }.getOrNull() }
        ?: listOf(listOf(EditableKey.ofLabel("a")))) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var changed by remember { mutableStateOf(!isCustom) }
    var askName by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    fun update(new: List<List<EditableKey>>, newSelection: Pair<Int, Int>? = selected) {
        rows = new.filter { it.isNotEmpty() }
        selected = newSelection?.takeIf { (r, k) -> r in rows.indices && k in rows[r].indices }
        changed = true
    }
    fun save(name: String) {
        val text = EditableLayout.toJson(rows)
        if (!LayoutUtilsCustom.checkLayout(text, ctx)) {
            Toast.makeText(ctx, R.string.fork_layout_invalid, Toast.LENGTH_LONG).show()
            return
        }
        LayoutUtilsCustom.getLayoutFile(name, type, ctx).writeText(text)
        LayoutUtilsCustom.onLayoutFileChanged()
        Settings.writeDefaultLayoutName(name, type, prefs)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
        changed = false
        Toast.makeText(ctx, R.string.fork_layout_saved, Toast.LENGTH_SHORT).show()
        onClickBack()
    }
    fun back() { if (changed && isCustom) confirmLeave = true else onClickBack() }
    BackHandler { back() }

    SearchSettingsScreen(onClickBack = ::back, title = displayName(layoutName, ctx), settings = emptyList(), extraActions = {
        TextButton(onClick = { if (isCustom) save(layoutName) else askName = true }) {
            Text(stringResource(R.string.fork_layout_save), style = MaterialTheme.typography.labelLarge)
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(if (isCustom) R.string.fork_layout_editor_hint else R.string.fork_layout_editor_hint_copy),
                style = MaterialTheme.typography.bodySmall, color = s.mutedForeground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            KeyGrid(rows, selected, small = false, onKey = { r, k -> selected = if (selected == r to k) null else r to k },
                modifier = Modifier.padding(horizontal = 12.dp))

            val sel = selected
            if (sel == null) {
                Text(stringResource(R.string.fork_layout_select_key), style = MaterialTheme.typography.bodyMedium,
                    color = s.mutedForeground, modifier = Modifier.padding(20.dp))
                TextButton(onClick = { update(rows + listOf(listOf(EditableKey.ofLabel("a")))) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.fork_layout_add_row_end), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                val (r, k) = sel
                val key = rows[r][k]
                fun setKey(newKey: EditableKey) = update(rows.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == k) newKey else old } else row })
                SettingsSection(stringResource(R.string.fork_layout_key_title, r + 1, k + 1), listOf(
                    @Composable {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            when {
                                key.isSpecial -> Text(stringResource(R.string.fork_layout_special_key), style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
                                key.isSpacer -> Text(stringResource(R.string.fork_layout_spacer_key), style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
                                else -> {
                                    var label by remember(sel, key.label) { mutableStateOf(key.label) }
                                    var popups by remember(sel) { mutableStateOf(key.popups.joinToString(" ")) }
                                    OutlinedTextField(label, { label = it; if (it.isNotBlank()) setKey(key.withLabel(it.trim())) },
                                        label = { Text(stringResource(R.string.fork_layout_key_label)) }, singleLine = true,
                                        supportingText = { Text(stringResource(R.string.fork_layout_key_label_hint)) },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = s.ring, unfocusedBorderColor = s.input),
                                        modifier = Modifier.fillMaxWidth())
                                    OutlinedTextField(popups, { popups = it; setKey(key.withPopups(it.split(Regex("\\s+")).filter { p -> p.isNotBlank() })) },
                                        label = { Text(stringResource(R.string.fork_layout_key_popups)) }, singleLine = true,
                                        supportingText = { Text(stringResource(R.string.fork_layout_key_popups_hint)) },
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = s.ring, unfocusedBorderColor = s.input),
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                                }
                            }
                            Text(stringResource(R.string.fork_layout_key_width), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0f to R.string.fork_auto, 0.1f to null, 0.15f to null, 0.2f to null, 0.3f to null, -1f to R.string.fork_layout_width_fill).forEach { (w, labelRes) ->
                                    FilterChip(selected = key.width == w, onClick = { setKey(key.withWidth(w)) },
                                        label = { Text(labelRes?.let { stringResource(it) } ?: "${(w * 100).toInt()}%") },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = s.primary, selectedLabelColor = s.primaryForeground, labelColor = s.foreground))
                                }
                            }
                        }
                    },
                    @Composable {
                        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                            fun act(text: Int, enabled: Boolean = true, destructive: Boolean = false, action: () -> Unit): @Composable () -> Unit = {
                                TextButton(onClick = action, enabled = enabled) {
                                    Text(stringResource(text), color = if (destructive) s.destructive else Color.Unspecified, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            act(R.string.fork_layout_move_left, k > 0) {
                                update(rows.mapIndexed { i, row -> if (i == r) row.toMutableList().apply { add(k - 1, removeAt(k)) } else row }, r to k - 1)
                            }()
                            act(R.string.fork_layout_move_right, k < rows[r].lastIndex) {
                                update(rows.mapIndexed { i, row -> if (i == r) row.toMutableList().apply { add(k + 1, removeAt(k)) } else row }, r to k + 1)
                            }()
                            act(R.string.fork_layout_move_up, r > 0) {
                                update(rows.mapIndexed { i, row -> when (i) { r - 1 -> row + key; r -> row.filterIndexed { j, _ -> j != k }; else -> row } }, r - 1 to rows[r - 1].size)
                            }()
                            act(R.string.fork_layout_move_down, r < rows.lastIndex) {
                                update(rows.mapIndexed { i, row -> when (i) { r + 1 -> row + key; r -> row.filterIndexed { j, _ -> j != k }; else -> row } }, r + 1 to rows[r + 1].size)
                            }()
                            act(R.string.fork_layout_add_key) {
                                update(rows.mapIndexed { i, row -> if (i == r) row.toMutableList().apply { add(k + 1, EditableKey.ofLabel("a")) } else row }, r to k + 1)
                            }()
                            act(if (key.isSpacer) R.string.fork_layout_make_key else R.string.fork_layout_make_spacer, !key.isSpecial) {
                                setKey(key.asSpacer(!key.isSpacer))
                            }()
                            act(R.string.fork_layout_add_row) {
                                update(rows.toMutableList().apply { add(r + 1, listOf(EditableKey.ofLabel("a"))) }, r + 1 to 0)
                            }()
                            act(R.string.fork_layout_delete_key, destructive = true) {
                                update(rows.mapIndexed { i, row -> if (i == r) row.filterIndexed { j, _ -> j != k } else row }, null)
                            }()
                            act(R.string.fork_layout_delete_row, rows.size > 1, destructive = true) {
                                update(rows.filterIndexed { i, _ -> i != r }, null)
                            }()
                        }
                    },
                ))
            }
        }
    }
    if (askName) {
        val custom = LayoutUtilsCustom.getLayoutFiles(type, ctx).map { it.name }
        TextInputDialog(
            onDismissRequest = { askName = false },
            onConfirmed = { save(LayoutUtilsCustom.getLayoutName(it.trim(), type)) },
            title = { Text(stringResource(R.string.fork_layout_name)) },
            initialText = displayName(layoutName, ctx) + " 2",
            checkTextValid = { it.isNotBlank() && LayoutUtilsCustom.getLayoutName(it.trim(), type) !in custom },
        )
    }
    if (confirmLeave)
        ConfirmationDialog(
            onDismissRequest = { confirmLeave = false },
            onConfirmed = { onClickBack() },
            content = { Text(stringResource(R.string.fork_layout_discard)) },
            confirmButtonText = stringResource(R.string.fork_layout_discard_confirm),
        )
}

/** keys as boxes, widths like on the keyboard (auto = 10%) */
@Composable
private fun KeyGrid(rows: List<List<EditableKey>>, selected: Pair<Int, Int>?, small: Boolean,
                    onKey: ((Int, Int) -> Unit)?, modifier: Modifier = Modifier) {
    val s = LocalShadcn.current
    val keyHeight = if (small) 18.dp else 44.dp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (small) 2.dp else 5.dp)) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (small) 2.dp else 4.dp)) {
                row.forEachIndexed { k, key ->
                    val weight = when {
                        key.width > 0f -> key.width * 10
                        key.width < 0f -> 3f
                        else -> 1f
                    }
                    val isSelected = selected == r to k
                    val shape = RoundedCornerShape(if (small) 3.dp else 6.dp)
                    Box(
                        Modifier.weight(weight).height(keyHeight).clip(shape)
                            .background(if (key.isSpacer) Color.Transparent else if (isSelected) s.primary else s.muted)
                            .then(if (isSelected || key.isSpacer) Modifier.border(1.dp, if (key.isSpacer) s.border else s.primary, shape) else Modifier)
                            .then(if (onKey != null) Modifier.clickable { onKey(r, k) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(EditableLayout.displayLabel(key), maxLines = 1, overflow = TextOverflow.Clip,
                            fontSize = if (small) 9.sp else 14.sp,
                            color = if (isSelected) s.primaryForeground else s.foreground)
                    }
                }
            }
        }
    }
}
