// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.fork.widget.TriviaInterests
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.Preference

/** fork: the trivia interests, written freely: added with the field, removed with ✕ */
@Composable
fun TriviaInterestsPreference(title: String) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var refresh by remember { mutableIntStateOf(0) }
    val interests = remember(refresh) { TriviaInterests.all(prefs).map { it.text } }
    var editing by remember { mutableStateOf(false) }
    Preference(
        name = title,
        description = if (interests.isEmpty()) stringResource(R.string.fork_trivia_interests_none)
            else interests.joinToString(", "),
        onClick = { editing = true },
    )
    if (!editing) return
    var text by remember { mutableStateOf("") }
    fun addTyped() {
        if (text.isBlank()) return
        TriviaInterests.add(prefs, text)
        text = ""
        refresh++
    }
    ThreeButtonAlertDialog(
        onDismissRequest = { editing = false },
        onConfirmed = { addTyped(); editing = false },
        title = { Text(title) },
        scrollContent = true,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.fork_trivia_interests_help), style = MaterialTheme.typography.bodySmall,
                    color = LocalShadcn.current.mutedForeground)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.fork_trivia_interests_hint)) })
                    TextButton(onClick = { addTyped() }, enabled = text.isNotBlank()) {
                        Text(stringResource(R.string.fork_trivia_interests_add))
                    }
                }
                interests.forEach { interest ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(interest, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                        TextButton(onClick = { TriviaInterests.remove(prefs, interest); refresh++ }) { Text("✕") }
                    }
                }
            }
        },
    )
}
