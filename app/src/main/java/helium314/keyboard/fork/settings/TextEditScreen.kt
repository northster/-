// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import org.json.JSONObject
import java.util.Locale

/**
 * fork: More > Edit texts: every text of the app in Korean; tap one to write it the way it should read. The changes are
 * kept (not applied: they go into the app with the next build) and sent all together with "Send" (share sheet), as
 * lines of "file, name, old text, new text" that scripts/texts.py applies.
 */
class AppText(val label: String, val key: String, val text: String)

object TextEdits {
    private const val PREF = "fork_text_edits"

    fun load(prefs: SharedPreferences): Map<String, String> {
        val json = runCatching { JSONObject(prefs.getString(PREF, "{}") ?: "{}") }.getOrElse { JSONObject() }
        return json.keys().asSequence().associateWith { json.optString(it) }
    }

    fun save(prefs: SharedPreferences, edits: Map<String, String>) {
        val json = JSONObject()
        edits.forEach { (k, v) -> json.put(k, v) }
        prefs.edit { putString(PREF, json.toString()) }
    }

    /** the texts in Korean, in the order of the string files (the app's own first) */
    fun texts(context: Context): List<AppText> {
        val names = runCatching {
            context.assets.open("text_keys.txt").bufferedReader().readLines()
        }.getOrElse { emptyList() }
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.KOREAN) }
        val ko = context.createConfigurationContext(config).resources
        return names.mapNotNull { line ->
            val (label, key) = line.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
            val id = context.resources.getIdentifier(key, "string", context.packageName)
            if (id == 0) return@mapNotNull null
            val text = runCatching { ko.getString(id) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AppText(label, key, text)
        }
    }

    /** what is sent: one line per changed text, tabs and line breaks written as \t and \n */
    fun export(texts: List<AppText>, edits: Map<String, String>): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
        val byKey = texts.associateBy { it.key }
        return buildString {
            append("DTboard 텍스트 수정 ").append(edits.size).append("개\n")
            edits.forEach { (key, new) ->
                val t = byKey[key] ?: return@forEach
                append(t.label).append('\t').append(key).append('\t').append(esc(t.text)).append('\t').append(esc(new)).append('\n')
            }
        }
    }
}

@Composable
fun TextEditScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val s = LocalShadcn.current
    val texts = remember { TextEdits.texts(ctx) }
    var edits by remember { mutableStateOf(TextEdits.load(prefs)) }
    var editing by remember { mutableStateOf<AppText?>(null) }
    var onlyChanged by remember { mutableStateOf(false) }
    fun setEdit(key: String, value: String?) {
        edits = if (value == null) edits - key else edits + (key to value)
        TextEdits.save(prefs, edits)
    }
    val row: @Composable (AppText) -> Unit = { t -> TextRow(t, edits[t.key]) { editing = t } }
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.fork_screen_texts)) },
        filteredItems = { q ->
            texts.filter { it.text.contains(q, ignoreCase = true) || edits[it.key]?.contains(q, ignoreCase = true) == true }
        },
        itemContent = row,
        content = {
            Column(Modifier.fillMaxSize()) {
                // what this is, how many are changed, and sending them
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)).background(s.card).padding(16.dp)) {
                    Text(stringResource(R.string.fork_texts_intro), color = s.mutedForeground, fontSize = 13.5.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.fork_texts_count, edits.size), Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Pill(stringResource(R.string.fork_texts_only_changed), onlyChanged, enabled = true) {
                            onlyChanged = !onlyChanged
                        }
                        Pill(stringResource(R.string.fork_texts_send), true, enabled = edits.isNotEmpty()) {
                            val text = TextEdits.export(texts, edits)
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "DTboard 텍스트 수정")
                                .putExtra(Intent.EXTRA_TEXT, text)
                            ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
                val shown = if (onlyChanged) texts.filter { it.key in edits } else texts
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.key }) { row(it) }
                }
            }
        },
    )
    editing?.let { t ->
        EditDialog(t, edits[t.key], onDismiss = { editing = null }) { value ->
            setEdit(t.key, value?.takeIf { it != t.text })
            editing = null
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val s = LocalShadcn.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on && enabled) s.primary else s.muted)
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
            color = when { !enabled -> s.ring; on -> s.primaryForeground; else -> s.foreground })
    }
}

@Composable
private fun TextRow(t: AppText, edited: String?, onClick: () -> Unit) {
    val s = LocalShadcn.current
    Column(Modifier.fillMaxWidth().clickable { onClick() }.heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 12.dp)) {
        if (edited != null) {
            Text(edited, color = s.primary, fontSize = 15.5.sp)
            Text(stringResource(R.string.fork_texts_original, t.text), color = s.mutedForeground, fontSize = 12.5.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        } else {
            Text(t.text, color = s.foreground, fontSize = 15.5.sp)
        }
    }
    HorizontalDivider(color = s.border)
}

@Composable
private fun EditDialog(t: AppText, edited: String?, onDismiss: () -> Unit, onDone: (String?) -> Unit) {
    val s = LocalShadcn.current
    var value by remember { mutableStateOf(edited ?: t.text) }
    // %s, %1$s, %d...: where the app puts a name or a number, must stay
    val marks = Regex("%(\\d+\\$)?[sd]").findAll(t.text).map { it.value }.toList()
    val missing = marks.any { it !in value }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column {
                Text(stringResource(R.string.fork_texts_original, t.text), color = s.mutedForeground, fontSize = 13.sp)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = s.primary, cursorColor = s.primary),
                )
                if (missing) Text(stringResource(R.string.fork_texts_placeholder_warning, marks.joinToString(" ")),
                    color = s.warning, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onDone(value) }) { Text(stringResource(R.string.fork_texts_save)) } },
        dismissButton = {
            Row {
                if (edited != null) TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.fork_texts_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}
