package io.github.notifilter

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Receives every posted notification from the system and dismisses the ones matching a rule.
 * Matching runs on a background thread so a slow regex can never block the UI.
 */
class FilterListenerService : NotificationListenerService() {
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("NotificationFilter").apply { start() }
        handler = Handler(worker.looper)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        worker.quitSafely()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        instance = this
        Store.setListenerConnected(true)
        reapplyToActive()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        Store.setListenerConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handler.post { process(sbn) }
    }

    private fun reapplyToActive() {
        handler.post {
            val active = try {
                activeNotifications
            } catch (e: SecurityException) {
                Log.w(TAG, "Listener not connected", e)
                null
            }
            active?.forEach(::process)
        }
    }

    private fun process(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        // Dismissing a group summary also dismisses every notification in the group, including
        // ones that don't match. Summaries are left alone; the system tidies up empty groups.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val info = toInfo(sbn)
        val rule = if (Store.enabled.value) Store.match(info) else null
        if (rule == null) {
            Store.recordSeen(info)
            return
        }
        dismiss(sbn)
        Store.recordFiltered(info.copy(ruleId = rule.id, ruleName = rule.displayName), sbn.notification.contentIntent)
    }

    private fun dismiss(sbn: StatusBarNotification) {
        try {
            if (sbn.isClearable) {
                cancelNotification(sbn.key)
            } else {
                // Listeners can't cancel ongoing notifications; snoozing hides them instead. When
                // the snooze ends the notification is posted again and filtered again.
                snoozeNotification(sbn.key, ONGOING_SNOOZE_MS)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Couldn't dismiss ${sbn.key}", e)
        }
    }

    private fun toInfo(sbn: StatusBarNotification): NotificationInfo {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)

        val parts = LinkedHashSet<String>()
        fun add(cs: CharSequence?) {
            cs?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add)
        }
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach(::add)
        @Suppress("DEPRECATION")
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { add((it as? Bundle)?.getCharSequence("text")) }

        return NotificationInfo(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel(sbn),
            title = title?.toString().orEmpty().take(MAX_TEXT),
            text = parts.joinToString("\n").take(MAX_TEXT),
            postTime = sbn.postTime,
        )
    }

    private fun appLabel(sbn: StatusBarNotification): String {
        AppLabels.get(this, sbn.packageName)?.let { return it }
        // Apps without a launcher icon aren't visible to us, but notifications carry their app info.
        @Suppress("DEPRECATION")
        val info = sbn.notification.extras.getParcelable<ApplicationInfo>("android.appInfo")
        return info?.loadLabel(packageManager)?.toString() ?: sbn.packageName
    }

    companion object {
        private const val TAG = "FilterListener"
        private const val MAX_TEXT = 4000
        private const val ONGOING_SNOOZE_MS = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: FilterListenerService? = null

        /**
         * Re-runs the rules over everything currently in the notification shade, which also puts
         * unfiltered ones back in the Recent list. Returns false if the listener isn't connected.
         */
        fun reapply(): Boolean {
            val service = instance ?: return false
            service.reapplyToActive()
            return true
        }
    }
}
