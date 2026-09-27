// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.clipboard

import android.Manifest
import android.content.ClipDescription
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs

/**
 * fork: adds new screenshots to the clipboard history, so they can be pasted like copied images.
 * Watches MediaStore while the keyboard service runs; needs the photo permission and file clips enabled.
 */
class ScreenshotWatcher(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private var lastId = -1L

    /** register / unregister depending on the setting and permission, cheap enough to call often */
    fun update() {
        val prefs = context.prefs()
        val wanted = ClipPrefs.screenshots(prefs) && hasPermission(context)
            && prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY)
            && prefs.getBoolean(Settings.PREF_CLIPBOARD_USE_FILES, Defaults.PREF_CLIPBOARD_USE_FILES)
        if (wanted && observer == null) {
            observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    // the file may still be written, look a bit later
                    handler.removeCallbacks(check)
                    handler.postDelayed(check, 1200)
                }
            }.also {
                runCatching {
                    context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, it)
                }.onFailure { e -> Log.w(TAG, "can't watch screenshots", e); observer = null }
            }
        } else if (!wanted && observer != null) {
            stop()
        }
    }

    fun stop() {
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
        handler.removeCallbacks(check)
    }

    private val check = Runnable { addLatestScreenshot() }

    private fun addLatestScreenshot() {
        val projection = mutableListOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.MIME_TYPE)
        @Suppress("DEPRECATION")
        projection.add(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH
            else MediaStore.Images.Media.DATA)
        try {
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection.toTypedArray(),
                null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC").use { c ->
                if (c == null || !c.moveToFirst()) return
                val id = c.getLong(0)
                val name = c.getString(1).orEmpty()
                val added = c.getLong(2)
                val mime = c.getString(3) ?: "image/png"
                val path = c.getString(4).orEmpty()
                if (id == lastId) return
                if (System.currentTimeMillis() / 1000 - added > 20) return // not new
                if (!name.contains("screenshot", true) && !path.contains("screenshot", true)) return
                lastId = id
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                ClipboardDao.getInstance(context)?.addClipUri(System.currentTimeMillis(), false, uri,
                    ClipDescription(name, arrayOf(mime)), context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "can't read screenshot", e)
        }
    }

    companion object {
        private const val TAG = "ScreenshotWatcher"

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        fun hasPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
