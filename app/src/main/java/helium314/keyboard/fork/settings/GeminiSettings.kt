// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.fork.slate.SlateKeys
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.Preference

/** fork: the Gemini API keys and model for AI commands and translation, in Settings > Advanced */
fun createGeminiSettings(context: Context) = listOf(
    Setting(context, SlateKeys.PREF_KEYS, R.string.fork_slate_gemini) { GeminiKeysPreference() },
    Setting(context, SlateKeys.PREF_MODEL, R.string.fork_slate_model) { setting ->
        val prefs = LocalContext.current.prefs()
        var model by remember { mutableStateOf(SlateKeys.model(prefs)) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(setting.title, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(model, { model = it.trim(); prefs.edit { putString(SlateKeys.PREF_MODEL, model) } },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text(SlateKeys.DEFAULT_MODEL) })
        }
    },
)

/** the saved keys (last 4 characters, tap to delete) and a row to add one */
@Composable
private fun GeminiKeysPreference() {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var refresh by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableIntStateOf(-1) }
    val keys = remember(refresh) { SlateKeys.keys(prefs) }
    Column {
        keys.forEachIndexed { i, key ->
            Preference(name = "••••" + key.takeLast(4), description = stringResource(R.string.fork_slate_key_saved),
                onClick = { deleting = i }) {
                Icon(painterResource(R.drawable.ic_dot_trash), stringResource(R.string.delete))
            }
        }
        Preference(name = stringResource(R.string.fork_slate_key_add), description = stringResource(R.string.fork_slate_key_add_summary),
            onClick = { adding = true }, icon = R.drawable.ic_plus)
    }
    if (adding) {
        var key by remember { mutableStateOf("") }
        ThreeButtonAlertDialog(
            onDismissRequest = { adding = false },
            onConfirmed = {
                if (!SlateKeys.add(prefs, key)) Toast.makeText(ctx, R.string.fork_slate_key_failed, Toast.LENGTH_LONG).show()
                refresh++
            },
            checkOk = { key.isNotBlank() },
            title = { Text(stringResource(R.string.fork_slate_key_add)) },
            content = { OutlinedTextField(key, { key = it.trim() }, singleLine = true, placeholder = { Text("AIza…") }) },
        )
    }
    if (deleting >= 0) {
        ConfirmationDialog(
            onDismissRequest = { deleting = -1 },
            onConfirmed = { SlateKeys.remove(prefs, deleting); refresh++ },
            content = { Text(stringResource(R.string.fork_slate_key_delete)) },
            confirmButtonText = stringResource(R.string.delete),
        )
    }
}
