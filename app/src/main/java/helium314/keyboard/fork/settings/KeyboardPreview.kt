// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn

/** fork: scrolling screen content; the keyboard test field is at the bottom of every settings screen ([KeyboardTestField]) */
@Composable
fun KeyboardPreviewScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        content()
    }
}

/**
 * Text field at the bottom of the settings screens: tap it to try the keyboard with the changed settings.
 * Not focused on its own, so the keyboard only shows when wanted.
 */
@Composable
fun KeyboardTestField() {
    var text by remember { mutableStateOf("") }
    val s = LocalShadcn.current
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = s.border)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.fork_test_field_hint), color = s.mutedForeground) },
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = s.ring,
                    unfocusedBorderColor = s.input,
                    cursorColor = s.foreground,
                ),
            )
        }
    }
}
