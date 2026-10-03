// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.fork.emoji.EmojiTabs
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.ShadcnSwitch

/** fork: emoji settings added to the appearance screen */
fun createForkEmojiSettings(context: Context) = listOf(
    Setting(context, EmojiTabs.PREF, R.string.fork_emoji_tabs, R.string.fork_emoji_tabs_summary) { setting ->
        EmojiTabOrderPreference(setting.title, setting.description)
    },
)

/** tabs with their icon, a switch to show / hide and buttons to move them up or down */
@Composable
private fun EmojiTabOrderPreference(name: String, description: String?) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val s = LocalShadcn.current
    val tabs = EmojiTabs.all(prefs)
    fun save(new: List<Pair<helium314.keyboard.keyboard.emoji.EmojiCategory.Category, Boolean>>) {
        EmojiTabs.save(prefs, new)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (description != null)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = s.mutedForeground)
            }
            TextButton(onClick = { EmojiTabs.reset(prefs); KeyboardSwitcher.getInstance().setThemeNeedsReload() },
                enabled = prefs.contains(EmojiTabs.PREF)) {
                Text(stringResource(R.string.button_default), style = MaterialTheme.typography.labelMedium)
            }
        }
        tabs.forEachIndexed { i, (category, shown) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(EmojiTabs.icon(category)), null, Modifier.size(22.dp),
                    tint = if (shown) s.foreground else s.mutedForeground)
                Text(ctx.getString(category.element.descriptionResId), style = MaterialTheme.typography.bodyMedium,
                    color = if (shown) s.foreground else s.mutedForeground,
                    modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = { save(tabs.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                    Icon(painterResource(helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_left)), stringResource(R.string.fork_move_up), Modifier.rotate(90f))
                }
                IconButton(onClick = { save(tabs.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < tabs.lastIndex) {
                    Icon(painterResource(helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_left)), stringResource(R.string.fork_move_down), Modifier.rotate(-90f))
                }
                ShadcnSwitch(checked = shown, onCheckedChange = { on ->
                    save(tabs.toMutableList().apply { set(i, category to on) })
                })
            }
        }
    }
}
