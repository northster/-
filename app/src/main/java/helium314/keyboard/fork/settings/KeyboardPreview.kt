// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.fork.ForkSettings
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity

/**
 * fork: screen content for screens with the keyboard preview toggle ([KeyboardPreviewToggle] in the top bar).
 * When it's on, a focused text field sits at the bottom so the real keyboard stays open and every change
 * can be seen right away.
 */
@Composable
fun KeyboardPreviewScaffold(content: @Composable ColumnScope.() -> Unit) {
    val ctx = LocalContext.current
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val showPreview = ctx.prefs().getBoolean(ForkSettings.PREF_SHOW_KEYBOARD_PREVIEW, false)
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        bottomBar = { if (showPreview) KeyboardPreviewField() },
    ) { innerPadding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).then(Modifier.padding(innerPadding)).padding(bottom = 24.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun KeyboardPreviewField() {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf("") }
    val s = LocalShadcn.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            HorizontalDivider(color = s.border)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focusRequester),
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

/** Top bar button that keeps the keyboard open at the bottom of the screen. */
@Composable
fun KeyboardPreviewToggle() {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val on = prefs.getBoolean(ForkSettings.PREF_SHOW_KEYBOARD_PREVIEW, false)
    val s = LocalShadcn.current
    androidx.compose.material3.IconButton(
        onClick = { prefs.edit().putBoolean(ForkSettings.PREF_SHOW_KEYBOARD_PREVIEW, !on).apply() },
        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
            containerColor = if (on) s.primary else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (on) s.primaryForeground else s.foreground,
        ),
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.ui.res.painterResource(R.drawable.ic_fork_keyboard),
            stringResource(R.string.fork_keyboard_preview),
        )
    }
}
