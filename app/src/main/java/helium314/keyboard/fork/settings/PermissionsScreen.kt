// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import helium314.keyboard.fork.clipboard.ClipPrefs
import helium314.keyboard.fork.clipboard.NotificationOtpCapture
import helium314.keyboard.fork.clipboard.ScreenshotWatcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LocalShadcn
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsSection
import helium314.keyboard.settings.preferences.Preference

/**
 * fork: what the app may do, in one place: each permission with what it is for, whether it is granted, and a tap
 * to grant it (the system dialog, or the system settings page when the dialog is not offered any more).
 */
@Composable
fun PermissionsScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // back from the system settings: read the state again
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val photos = remember(refresh) { ScreenshotWatcher.hasPermission(ctx) }
    val notifications = remember(refresh) { NotificationOtpCapture.hasAccess(ctx) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ctx.prefs().edit { putBoolean(ClipPrefs.SCREENSHOTS, true) }
        else openAppSettings(ctx) // asked too often: the system shows no dialog any more
        refresh++
    }
    SearchSettingsScreen(onClickBack = onClickBack, title = stringResource(R.string.fork_screen_permissions), settings = emptyList()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.fork_permissions_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalShadcn.current.mutedForeground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            SettingsSection(null, listOf(
                @Composable {
                    PermissionRow(stringResource(R.string.fork_permission_photos),
                        stringResource(R.string.fork_permission_photos_desc), photos) {
                        if (photos) openAppSettings(ctx) else photoLauncher.launch(ScreenshotWatcher.permission)
                    }
                },
                @Composable {
                    PermissionRow(stringResource(R.string.fork_permission_notifications),
                        stringResource(R.string.fork_permission_notifications_desc), notifications) {
                        runCatching {
                            ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }.onFailure { openAppSettings(ctx) }
                    }
                },
                @Composable {
                    PermissionRow(stringResource(R.string.fork_permission_app),
                        stringResource(R.string.fork_permission_app_desc), null) { openAppSettings(ctx) }
                },
            ))
        }
    }
}

@Composable
private fun PermissionRow(name: String, what: String, granted: Boolean?, onClick: () -> Unit) {
    Preference(name = name, description = what, onClick = onClick) {
        if (granted != null) Text(
            stringResource(if (granted) R.string.fork_permission_granted else R.string.fork_permission_needed),
            style = MaterialTheme.typography.labelLarge,
            color = if (granted) LocalShadcn.current.mutedForeground else Color(0xFFF97316),
        )
    }
}

private fun openAppSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", ctx.packageName, null)))
    }
}

/** the screenshot switch is on but the photo permission is gone (e.g. after reinstalling): show it as off */
fun fixScreenshotSwitch(ctx: Context) {
    val prefs = ctx.prefs()
    if (ClipPrefs.screenshots(prefs) && !ScreenshotWatcher.hasPermission(ctx))
        prefs.edit { putBoolean(ClipPrefs.SCREENSHOTS, false) }
}
