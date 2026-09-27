// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.fork.typo.TouchLearning
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsSection
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference

/**
 * fork: typo correction (TouchLearning): on / off, where the fingers land on each key (like Samsung's "most common
 * typos" screen: tap a key to see only its touches) and the most frequent typos.
 */
@Composable
fun TypoScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    var refresh by remember { mutableIntStateOf(0) }
    if (refresh < 0) return
    var selected by remember { mutableStateOf<Int?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val s = LocalShadcn.current
    val shapes = TouchLearning.keys(ctx)
    val ranking = TouchLearning.typoRanking(ctx)
    val palette = listOf(0xFF7E6BC4, 0xFFF2C94C, 0xFFEB5B3C, 0xFFF4B6A0, 0xFFC9D6A3, 0xFF1BA784, 0xFF5B5EA6, 0xFFE0A0C0)
        .map { Color(it) }

    SearchSettingsScreen(onClickBack = onClickBack, title = stringResource(R.string.fork_screen_typo), settings = emptyList()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection(null, listOf(@Composable {
                SwitchPreference(name = stringResource(R.string.fork_typo_enabled), key = TouchLearning.PREF_ENABLED,
                    default = true, description = stringResource(R.string.fork_typo_enabled_summary))
            }))
            // ---- where the keys are pressed
            SettingsSection(stringResource(R.string.fork_typo_touches), listOf(@Composable {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(if (shapes.isEmpty()) R.string.fork_typo_no_data else R.string.fork_typo_touches_summary),
                        style = MaterialTheme.typography.bodyMedium, color = s.mutedForeground)
                    if (shapes.isNotEmpty()) {
                        val keyColor = s.muted
                        val labelColor = s.foreground.copy(alpha = 0.8f).let { android.graphics.Color.argb(
                            (it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
                        val codes = shapes.keys.sorted()
                        val minY = shapes.values.minOf { it.y }
                        val maxY = shapes.values.maxOf { it.y + it.h }
                        val span = (maxY - minY).coerceAtLeast(0.1f)
                        Canvas(
                            Modifier.fillMaxWidth().padding(top = 8.dp).aspectRatio(TouchLearning.aspect / span)
                                .pointerInput(shapes) {
                                    detectTapGestures { tap ->
                                        // tap a key: only its touches
                                        val nx = tap.x / size.width
                                        val ny = minY + tap.y / size.height * span
                                        val hit = shapes.entries.firstOrNull { (_, k) -> nx in k.x..(k.x + k.w) && ny in k.y..(k.y + k.h) }?.key
                                        selected = if (hit == selected) null else hit
                                    }
                                }
                        ) {
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = labelColor
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            for ((code, k) in shapes) {
                                val left = k.x * size.width
                                val top = (k.y - minY) / span * size.height
                                val w = k.w * size.width
                                val h = k.h / span * size.height
                                drawRoundRect(keyColor, Offset(left + 2, top + 2), Size(w - 4, h - 4), CornerRadius(8f, 8f))
                                paint.textSize = h * 0.35f
                                drawContext.canvas.nativeCanvas.drawText(k.label, left + w / 2, top + h / 2 + paint.textSize / 3, paint)
                                if (selected != null && selected != code) continue
                                val color = palette[codes.indexOf(code) % palette.size].copy(alpha = 0.55f)
                                for (p in TouchLearning.points(code))
                                    drawCircle(color, radius = w * 0.07f, center = Offset(left + w / 2 + p[0] * w, top + h / 2 + p[1] * h))
                            }
                        }
                    }
                }
            }))
            // ---- most frequent typos
            SettingsSection(stringResource(R.string.fork_typo_ranking), if (ranking.isEmpty()) listOf(@Composable {
                Text(stringResource(R.string.fork_typo_no_typos), style = MaterialTheme.typography.bodyMedium,
                    color = s.mutedForeground, modifier = Modifier.padding(16.dp))
            }) else listOf(@Composable {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("", Modifier.width(32.dp))
                    Text(stringResource(R.string.fork_typo_typed), Modifier.weight(1f), color = Color(0xFFD9483B), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.fork_typo_meant), Modifier.weight(1f), color = Color(0xFF8F7BE0), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.fork_typo_count), fontWeight = FontWeight.Bold)
                }
            }) + ranking.take(20).mapIndexed { i, (typed, meant, count) ->
                @Composable {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("${i + 1}", Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
                        Text(typed, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Text(meant, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Text("$count", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4A8FE7))
                    }
                }
            })
            SettingsSection(null, listOf(@Composable {
                Preference(name = stringResource(R.string.fork_typo_reset), description = stringResource(R.string.fork_typo_reset_summary),
                    onClick = { confirmReset = true })
            }))
        }
    }
    if (confirmReset)
        ConfirmationDialog(
            onDismissRequest = { confirmReset = false },
            onConfirmed = { TouchLearning.reset(ctx); selected = null; refresh++ },
            content = { Text(stringResource(R.string.fork_typo_reset_confirm)) },
        )
}
