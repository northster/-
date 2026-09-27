// SPDX-License-Identifier: GPL-3.0-only
// Ported from WM Keyboard (github.com/wasi-master/wmkeyboard, core/otp/NotificationOtp.kt,
// core/otp/NotificationOtpMonitor.kt, core/media/MediaNotificationListener.kt), MIT License,
// Copyright (c) 2026 Wasi Master.
// fork: the code is handed to the dynamic toolbar's paste chip instead of WM's suggestion strip.
package helium314.keyboard.fork.clipboard

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import helium314.keyboard.latin.utils.prefs

/**
 * A one-time code lifted out of a just-arrived notification, offered as a paste chip.
 * Never persisted: it lives in [NotificationOtpBus] until it is used, dismissed, replaced or ages out.
 */
class NotificationOtp(
    /** the code itself, exactly as it should be typed */
    val code: String,
    /** launcher label of the app that posted it */
    val sourceApp: String,
    /** the notification text the code was found in, capped, shown as the chip's text */
    val message: String,
    val postedAt: Long,
)

/**
 * The newest code found in a notification, or null. One slot, newest wins: two codes in flight means the older one
 * was an abandoned login, and offering a stale code loses to offering none. Listener and keyboard are in the same
 * process, so the code never crosses a boundary and is never written down.
 */
object NotificationOtpBus {
    @Volatile
    var latest: NotificationOtp? = null
        private set

    fun publish(otp: NotificationOtp) {
        latest = otp
        // listener callbacks come on the main thread, but don't rely on it
        Handler(Looper.getMainLooper()).post {
            helium314.keyboard.fork.toolbar.DynamicToolbarController.current?.refreshPasteChips()
        }
    }

    /** the code was used, dismissed or expired */
    fun clear() {
        latest = null
    }
}

/** Whether notifications may be read at all (the setting), and whether the system granted access. */
object NotificationOtpCapture {
    const val PREF = "fork_clip_notification_codes"

    fun isEnabled(context: Context) = context.prefs().getBoolean(PREF, false)

    /** notification access for [OtpNotificationListener] is granted in the system settings */
    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, OtpNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

/** Turns a posted notification into a [NotificationOtp] on the bus, or ignores it. Cheap checks first. */
object NotificationOtpMonitor {
    private const val MAX_SCANNED_CHARS = 1000
    private const val MAX_MESSAGE_CHARS = 300

    /** recently processed notifications, so an app updating the same notification does not re-raise a chip */
    private val processed = object : LinkedHashMap<String, Unit>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>) = size > 64
    }

    fun process(context: Context, sbn: StatusBarNotification) {
        // our own notifications, and anything pinned (media, downloads, services) never carry a code
        if (sbn.packageName == context.packageName) return
        if (sbn.isOngoing) return
        val notification = sbn.notification ?: return
        // the summary repeats its children's text
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val dedupeKey = "${sbn.key}:${sbn.postTime}"
        synchronized(processed) {
            if (processed.containsKey(dedupeKey)) return
            processed[dedupeKey] = Unit
        }
        val text = harvest(notification).take(MAX_SCANNED_CHARS)
        val code = NotificationOtpExtractor.extract(text) ?: return
        NotificationOtpBus.publish(NotificationOtp(code, appLabel(context, sbn.packageName),
            text.take(MAX_MESSAGE_CHARS), sbn.postTime))
    }

    /** every text surface of the notification, visible ones first, so a keyword in the title anchors a code in the body */
    private fun harvest(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val parts = ArrayList<CharSequence>(6)
        extras.getCharSequence(Notification.EXTRA_TITLE)?.let(parts::add)
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let(parts::add)
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let(parts::add)
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { parts.addAll(it) }
        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let(parts::add)
        extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let(parts::add)
        return parts.joinToString(" ")
    }

    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName.substringAfterLast('.'))
}

/** The keyboard's notification listener: reads arriving notifications only while the setting is on. */
class OtpNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // while the setting is off, nothing of the notification is read
        if (!NotificationOtpCapture.isEnabled(this)) return
        NotificationOtpMonitor.process(this, sbn)
    }
}
