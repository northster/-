// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.preferences.Preference
import java.io.File

/** fork: keyboard fonts per script. The Latin font is HeliBoard's custom font, Hangul gets its own. */
object ForkFonts {
    /** settings key of the Korean font entry (the font itself is a file, see KeyboardTypeface.koreanFontFile) */
    const val KOREAN = "fork_font_korean"
    // file names of the loaded fonts, shown as description
    const val NAME_LATIN = "fork_font_name_latin"
    const val NAME_KOREAN = "fork_font_name_korean"
    const val NAME_EMOJI = "fork_font_name_emoji"
}

/** Load a font file into [fontFile], or remove it. Shows the loaded font's file name. */
@Composable
fun ForkFontPreference(setting: Setting, fontFile: File, nameKey: String) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        val tempFile = File(DeviceProtectedUtils.getFilesDir(ctx), "temp_file")
        FileUtils.copyContentUriToNewFile(uri, ctx, tempFile)
        try {
            Typeface.createFromFile(tempFile)
            fontFile.delete()
            tempFile.renameTo(fontFile)
            val name = runCatching {
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            prefs.edit { putString(nameKey, name ?: "") }
            KeyboardTypeface.clearCache()
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        } catch (_: Exception) {
            showErrorDialog = true
            tempFile.delete()
        }
    }
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
    val description = if (fontFile.exists()) prefs.getString(nameKey, null)?.ifEmpty { null } ?: stringResource(R.string.fork_font_custom)
        else stringResource(R.string.fork_font_default)
    Preference(
        name = setting.title,
        description = description,
        onClick = {
            if (fontFile.exists()) showDialog = true
            else launcher.launch(intent)
        },
    )
    if (showDialog)
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = { launcher.launch(intent) },
            onNeutral = {
                showDialog = false
                fontFile.delete()
                prefs.edit { remove(nameKey) }
                KeyboardTypeface.clearCache()
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            },
            neutralButtonText = stringResource(R.string.fork_font_reset),
            confirmButtonText = stringResource(R.string.load),
            title = { Text(setting.title) }
        )
    if (showErrorDialog)
        InfoDialog(stringResource(R.string.file_read_error)) { showErrorDialog = false }
}
