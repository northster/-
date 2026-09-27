// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.fork.slate.CommandType
import helium314.keyboard.fork.slate.SlateCommand
import helium314.keyboard.fork.slate.SlateCommands
import helium314.keyboard.fork.slate.SlateKeys
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsSection
import helium314.keyboard.settings.ShadcnSwitch
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.Preference

/**
 * fork: SwiftSlate style commands (fork/slate): on / off, trigger prefix, Gemini API keys and model, and the command
 * list (built-in ones read only, AI commands and text replacers editable).
 */
@Composable
fun SlateScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    var refresh by remember { mutableIntStateOf(0) } // keys aren't read through the recomposing preference flow alone
    if (refresh < 0) return
    val s = LocalShadcn.current
    var editing by remember { mutableStateOf<SlateCommand?>(null) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var addingKey by remember { mutableStateOf(false) }
    var deleteKey by remember { mutableIntStateOf(-1) }
    val custom = SlateCommands.custom(prefs)
    val prefix = SlateCommands.prefix(prefs)
    val keys = SlateKeys.keys(prefs)

    SearchSettingsScreen(onClickBack = onClickBack, title = stringResource(R.string.fork_screen_slate), settings = emptyList()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection(null, listOf(
                @Composable {
                    helium314.keyboard.settings.preferences.SwitchPreference(
                        name = stringResource(R.string.fork_slate_enabled), key = SlateCommands.PREF_ENABLED, default = true,
                        description = stringResource(R.string.fork_slate_enabled_summary, prefix),
                    )
                },
                @Composable {
                    var text by remember { mutableStateOf(prefix) }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.fork_slate_prefix), style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(text, { new ->
                            text = new.take(1)
                            val c = text.firstOrNull()
                            if (c != null && !c.isLetterOrDigit() && !c.isWhitespace() && text != prefix) {
                                // commands follow the new prefix
                                val moved = custom.map { it.copy(trigger = text + it.trigger.removePrefix(prefix)) }
                                prefs.edit { putString(SlateCommands.PREF_PREFIX, text) }
                                SlateCommands.saveCustom(prefs, moved)
                            }
                        }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                    }
                },
            ))
            // ---- Gemini
            SettingsSection(stringResource(R.string.fork_slate_gemini), keys.mapIndexed { i, key ->
                @Composable {
                    Preference(name = "••••" + key.takeLast(4), description = stringResource(R.string.fork_slate_key_saved),
                        onClick = { deleteKey = i }) {
                        Icon(painterResource(R.drawable.ic_dot_trash), stringResource(R.string.delete))
                    }
                }
            } + listOf(
                @Composable {
                    Preference(name = stringResource(R.string.fork_slate_key_add), description = stringResource(R.string.fork_slate_key_add_summary),
                        onClick = { addingKey = true }, icon = R.drawable.ic_plus)
                },
                @Composable {
                    var model by remember { mutableStateOf(SlateKeys.model(prefs)) }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.fork_slate_model), style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(model, { model = it.trim(); prefs.edit { putString(SlateKeys.PREF_MODEL, model) } },
                            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            placeholder = { Text(SlateKeys.DEFAULT_MODEL) })
                    }
                },
            ))
            // ---- commands
            SettingsSection(stringResource(R.string.fork_slate_commands), custom.mapIndexed { i, command ->
                @Composable {
                    Preference(
                        name = command.trigger,
                        description = (when {
                            command.type == CommandType.TEXT_REPLACER -> stringResource(R.string.fork_slate_type_replacer) + " · "
                            command.search -> stringResource(R.string.fork_slate_type_search) + " · "
                            else -> "AI · "
                        }) + command.prompt.take(80),
                        onClick = { editing = command; editingIndex = i },
                    )
                }
            } + listOf(@Composable {
                Preference(name = stringResource(R.string.fork_slate_command_add), onClick = {
                    editing = SlateCommand(prefix, ""); editingIndex = -1
                }, icon = R.drawable.ic_plus)
            }))
            SettingsSection(stringResource(R.string.fork_slate_builtin), SlateCommands.builtIns(prefs).map { command ->
                @Composable { Preference(name = command.trigger, description = command.prompt, onClick = { }) }
            })
            Text(stringResource(R.string.fork_slate_credit), style = MaterialTheme.typography.bodySmall, color = s.mutedForeground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }

    if (addingKey) {
        var key by remember { mutableStateOf("") }
        ThreeButtonAlertDialog(
            onDismissRequest = { addingKey = false },
            onConfirmed = {
                if (!SlateKeys.add(prefs, key)) Toast.makeText(ctx, R.string.fork_slate_key_failed, Toast.LENGTH_LONG).show()
                refresh++
            },
            checkOk = { key.isNotBlank() },
            title = { Text(stringResource(R.string.fork_slate_key_add)) },
            content = { OutlinedTextField(key, { key = it.trim() }, singleLine = true, placeholder = { Text("AIza…") }) },
        )
    }
    if (deleteKey >= 0) {
        ConfirmationDialog(
            onDismissRequest = { deleteKey = -1 },
            onConfirmed = { SlateKeys.remove(prefs, deleteKey); refresh++ },
            content = { Text(stringResource(R.string.fork_slate_key_delete)) },
            confirmButtonText = stringResource(R.string.delete),
        )
    }
    editing?.let { original ->
        var trigger by remember(original) { mutableStateOf(original.trigger) }
        var prompt by remember(original) { mutableStateOf(original.prompt) }
        var replacer by remember(original) { mutableStateOf(original.type == CommandType.TEXT_REPLACER) }
        var search by remember(original) { mutableStateOf(original.search) }
        val edited = SlateCommand(trigger.trim(), prompt, false, if (replacer) CommandType.TEXT_REPLACER else CommandType.AI, search && !replacer)
        ThreeButtonAlertDialog(
            onDismissRequest = { editing = null },
            onConfirmed = {
                val list = custom.toMutableList()
                if (editingIndex in list.indices) list[editingIndex] = edited else list.add(edited)
                SlateCommands.saveCustom(prefs, list)
                refresh++
            },
            checkOk = { SlateCommands.isValid(edited, prefix) },
            neutralButtonText = if (editingIndex >= 0) stringResource(R.string.delete) else null,
            onNeutral = {
                SlateCommands.saveCustom(prefs, custom.filterIndexed { i, _ -> i != editingIndex })
                refresh++
            },
            title = { Text(stringResource(R.string.fork_slate_command)) },
            scrollContent = true,
            content = {
                Column {
                    OutlinedTextField(trigger, { trigger = it.replace(" ", "") }, singleLine = true,
                        label = { Text(stringResource(R.string.fork_slate_trigger)) }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.fork_slate_type_replacer), modifier = Modifier.weight(1f))
                        ShadcnSwitch(checked = replacer, onCheckedChange = { replacer = it })
                    }
                    if (!replacer) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.fork_slate_search), modifier = Modifier.weight(1f))
                        ShadcnSwitch(checked = search, onCheckedChange = { search = it })
                    }
                    OutlinedTextField(prompt, { prompt = it.take(SlateCommands.MAX_PROMPT_LENGTH) }, minLines = 3,
                        label = { Text(stringResource(if (replacer) R.string.fork_slate_replacement else R.string.fork_slate_prompt)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
        )
    }
}
