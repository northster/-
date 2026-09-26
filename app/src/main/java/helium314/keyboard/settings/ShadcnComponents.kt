// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.utils.LocalShadcn

// fork: small set of shadcn/ui style building blocks for the settings screens

/** shadcn Switch: dark track when on, input colored track when off, same size thumb in both states. */
@Composable
fun ShadcnSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val s = LocalShadcn.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = { Box(Modifier.size(SwitchDefaults.IconSize)) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = s.primaryForeground,
            checkedTrackColor = s.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = s.background,
            uncheckedTrackColor = s.input,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Small muted label above a card, like a shadcn form group label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalShadcn.current.mutedForeground,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** shadcn Card: 1px border, 8dp radius, rows separated by thin dividers. */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    rows: List<@Composable () -> Unit>,
) {
    val s = LocalShadcn.current
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(BorderStroke(1.dp, s.border), MaterialTheme.shapes.medium)
            .background(s.card)
    ) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = s.border, thickness = 1.dp)
            row()
        }
    }
}

@Composable
fun SettingsSection(title: String?, rows: List<@Composable () -> Unit>) {
    if (rows.isEmpty()) return
    if (title != null) SectionLabel(title)
    else Box(Modifier.padding(top = 12.dp))
    SettingsCard(rows = rows)
}

/** shadcn Alert (warning variant) with an action button, used for keyboard setup state. */
@Composable
fun SetupAlert(
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalShadcn.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = s.warningBackground,
        border = BorderStroke(1.dp, s.warning.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = s.warning)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = s.foreground)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                ShadcnButton(actionText, onAction)
            }
        }
    }
}

/** shadcn default Button: primary background, 6dp radius, compact. */
@Composable
fun ShadcnButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalShadcn.current
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = s.primary, contentColor = s.primaryForeground),
        contentPadding = ButtonDefaults.ContentPadding,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}
