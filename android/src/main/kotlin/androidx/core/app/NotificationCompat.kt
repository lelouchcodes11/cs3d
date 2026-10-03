package androidx.core.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle

/**
 * androidx NotificationCompat: builds the platform [Notification] model which the desktop shows in
 * the app notification panel / system tray (see com.lagradost.desktop.runtime.Notifications).
 */
open class NotificationCompat {
    companion object {
        const val PRIORITY_DEFAULT = 0
        const val PRIORITY_LOW = -1
        const val PRIORITY_MIN = -2
        const val PRIORITY_HIGH = 1
        const val PRIORITY_MAX = 2
        const val VISIBILITY_PUBLIC = 1
        const val VISIBILITY_PRIVATE = 0
        const val VISIBILITY_SECRET = -1
        const val CATEGORY_PROGRESS = "progress"
        const val CATEGORY_SERVICE = "service"
        const val CATEGORY_STATUS = "status"
        const val CATEGORY_TRANSPORT = "transport"
        const val DEFAULT_ALL = -1
        const val FOREGROUND_SERVICE_IMMEDIATE = 1
        const val FOREGROUND_SERVICE_DEFAULT = 0
        const val FOREGROUND_SERVICE_DEFERRED = 2
        const val EXTRA_TITLE = "android.title"
        const val EXTRA_TEXT = "android.text"
        const val GROUP_ALERT_ALL = 0
        const val GROUP_ALERT_SUMMARY = 1
        const val GROUP_ALERT_CHILDREN = 2
    }

    abstract class Style {
        internal abstract fun apply(n: Notification)
    }

    open class BigTextStyle : Style() {
        private var big: CharSequence? = null
        open fun bigText(cs: CharSequence?): BigTextStyle = apply { big = cs }
        open fun setBigContentTitle(title: CharSequence?): BigTextStyle = this
        open fun setSummaryText(cs: CharSequence?): BigTextStyle = this
        override fun apply(n: Notification) {
            n.bigText = big
        }
    }

    open class BigPictureStyle : Style() {
        private var picture: Bitmap? = null
        open fun bigPicture(b: Bitmap?): BigPictureStyle = apply { picture = b }
        open fun bigLargeIcon(b: Bitmap?): BigPictureStyle = this
        open fun setBigContentTitle(title: CharSequence?): BigPictureStyle = this
        open fun setSummaryText(cs: CharSequence?): BigPictureStyle = this
        override fun apply(n: Notification) {
            if (n.largeIcon == null) n.largeIcon = picture
        }
    }

    open class InboxStyle : Style() {
        private val lines = ArrayList<CharSequence?>()
        open fun addLine(cs: CharSequence?): InboxStyle = apply { lines.add(cs) }
        open fun setBigContentTitle(title: CharSequence?): InboxStyle = this
        open fun setSummaryText(cs: CharSequence?): InboxStyle = this
        override fun apply(n: Notification) {
            n.bigText = lines.filterNotNull().joinToString("\n")
        }
    }

    class Action(
        @JvmField val icon: Int,
        @JvmField val title: CharSequence?,
        @JvmField val actionIntent: PendingIntent?,
    ) {
        class Builder(private val icon: Int, private val title: CharSequence?, private val intent: PendingIntent?) {
            fun build(): Action = Action(icon, title, intent)
        }
    }

    open class Builder(private val context: Context?, private var channelId: String?) {
        @Deprecated("Use Builder(Context, String)")
        constructor(context: Context?) : this(context, null)

        private val n = Notification()
        private var style: Style? = null

        open fun setContentTitle(title: CharSequence?): Builder = apply { n.title = title }
        open fun setContentText(text: CharSequence?): Builder = apply { n.text = text }
        open fun setSubText(text: CharSequence?): Builder = apply { n.subText = text }
        open fun setContentInfo(info: CharSequence?): Builder = this
        open fun setTicker(tickerText: CharSequence?): Builder = apply { n.tickerText = tickerText }
        open fun setSmallIcon(icon: Int): Builder = this
        open fun setSmallIcon(icon: Int, level: Int): Builder = this
        open fun setLargeIcon(icon: Bitmap?): Builder = apply { n.largeIcon = icon }
        open fun setContentIntent(intent: PendingIntent?): Builder = apply { n.contentIntent = intent }
        open fun setDeleteIntent(intent: PendingIntent?): Builder = this
        open fun setAutoCancel(autoCancel: Boolean): Builder = apply { setFlag(Notification.FLAG_AUTO_CANCEL, autoCancel) }
        open fun setOngoing(ongoing: Boolean): Builder = apply {
            n.ongoing = ongoing
            setFlag(Notification.FLAG_ONGOING_EVENT, ongoing)
        }
        open fun setOnlyAlertOnce(onlyAlertOnce: Boolean): Builder = apply { n.onlyAlertOnce = onlyAlertOnce }
        open fun setSilent(silent: Boolean): Builder = apply { n.silent = silent }
        open fun setPriority(pri: Int): Builder = apply { n.priority = pri }
        open fun setCategory(category: String?): Builder = this
        open fun setVisibility(visibility: Int): Builder = this
        open fun setColor(argb: Int): Builder = this
        open fun setColorized(colorize: Boolean): Builder = this
        open fun setShowWhen(show: Boolean): Builder = this
        open fun setWhen(`when`: Long): Builder = apply { n.`when` = `when` }
        open fun setUsesChronometer(b: Boolean): Builder = this
        open fun setGroup(groupKey: String?): Builder = apply { n.group = groupKey }
        open fun setGroupSummary(isGroupSummary: Boolean): Builder = this
        open fun setGroupAlertBehavior(groupAlertBehavior: Int): Builder = this
        open fun setSortKey(sortKey: String?): Builder = this
        open fun setChannelId(channelId: String?): Builder = apply { this.channelId = channelId }
        open fun setDefaults(defaults: Int): Builder = this
        open fun setSound(sound: android.net.Uri?): Builder = this
        open fun setVibrate(pattern: LongArray?): Builder = this
        open fun setLights(argb: Int, onMs: Int, offMs: Int): Builder = this
        open fun setNumber(number: Int): Builder = this
        open fun setTimeoutAfter(durationMs: Long): Builder = this
        open fun setForegroundServiceBehavior(behavior: Int): Builder = this
        open fun setLocalOnly(b: Boolean): Builder = this
        open fun setExtras(extras: Bundle?): Builder = apply { if (extras != null) n.extras = extras }
        open fun addExtras(extras: Bundle?): Builder = apply { if (extras != null) n.extras.putAll(extras) }
        open fun getExtras(): Bundle = n.extras
        open fun setStyle(style: Style?): Builder = apply { this.style = style }
        open fun setProgress(max: Int, progress: Int, indeterminate: Boolean): Builder = apply {
            n.progressMax = max
            n.progress = if (max == 0 && !indeterminate) -1 else progress
            n.progressIndeterminate = indeterminate
        }

        open fun addAction(icon: Int, title: CharSequence?, intent: PendingIntent?): Builder = apply {
            n.actions.add(Notification.Action(icon, title, intent))
        }

        open fun addAction(action: Action?): Builder = apply {
            if (action != null) n.actions.add(Notification.Action(action.icon, action.title, action.actionIntent))
        }

        open fun clearActions(): Builder = apply { n.actions.clear() }

        private fun setFlag(mask: Int, value: Boolean) {
            n.flags = if (value) n.flags or mask else n.flags and mask.inv()
        }

        open fun build(): Notification {
            style?.apply(n)
            n.channelId = channelId
            n.extras.putCharSequence(EXTRA_TITLE, n.title)
            n.extras.putCharSequence(EXTRA_TEXT, n.text)
            return n
        }

        @Suppress("unused")
        private val unusedManager = NotificationManager::class.java
    }
}

open class NotificationManagerCompat private constructor(private val manager: NotificationManager) {
    companion object {
        const val IMPORTANCE_UNSPECIFIED = -1000
        const val IMPORTANCE_NONE = 0
        const val IMPORTANCE_MIN = 1
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
        const val IMPORTANCE_HIGH = 4
        const val IMPORTANCE_MAX = 5

        @JvmStatic
        fun from(context: Context): NotificationManagerCompat =
            NotificationManagerCompat(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    }

    open fun notify(id: Int, notification: Notification) = manager.notify(id, notification)
    open fun notify(tag: String?, id: Int, notification: Notification) = manager.notify(tag, id, notification)
    open fun cancel(id: Int) = manager.cancel(id)
    open fun cancel(tag: String?, id: Int) = manager.cancel(tag, id)
    open fun cancelAll() = manager.cancelAll()
    open val activeNotifications: List<android.service.notification.StatusBarNotification>
        get() =
        com.lagradost.desktop.runtime.Notifications.active.entries.map {
            android.service.notification.StatusBarNotification(it.key.tag, it.key.id, it.value)
        }
    open fun areNotificationsEnabled(): Boolean = true
    open fun createNotificationChannel(channel: android.app.NotificationChannel) = manager.createNotificationChannel(channel)
}

open class ActivityOptionsCompat protected constructor() {
    companion object {
        @JvmStatic
        fun makeBasic(): ActivityOptionsCompat = ActivityOptionsCompat()

        @JvmStatic
        fun makeCustomAnimation(context: Context, enterResId: Int, exitResId: Int): ActivityOptionsCompat = ActivityOptionsCompat()
    }

    open fun toBundle(): Bundle? = null
}

object ActivityCompat {
    @JvmStatic
    fun checkSelfPermission(context: Context, permission: String): Int = android.content.pm.PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun requestPermissions(activity: android.app.Activity, permissions: Array<String>, requestCode: Int) {
    }

    @JvmStatic
    fun shouldShowRequestPermissionRationale(activity: android.app.Activity, permission: String): Boolean = false

    @JvmStatic
    fun finishAffinity(activity: android.app.Activity) = activity.finish()
}

object PendingIntentCompat {
    @JvmStatic
    fun getActivity(context: Context, requestCode: Int, intent: android.content.Intent, flags: Int, isMutable: Boolean): android.app.PendingIntent? =
        android.app.PendingIntent.getActivity(context, requestCode, intent, flags)

    @JvmStatic
    fun getActivity(context: Context, requestCode: Int, intent: android.content.Intent, flags: Int, options: Bundle?, isMutable: Boolean): android.app.PendingIntent? =
        android.app.PendingIntent.getActivity(context, requestCode, intent, flags, options)

    @JvmStatic
    fun getService(context: Context, requestCode: Int, intent: android.content.Intent, flags: Int, isMutable: Boolean): android.app.PendingIntent? =
        android.app.PendingIntent.getService(context, requestCode, intent, flags)

    @JvmStatic
    fun getBroadcast(context: Context, requestCode: Int, intent: android.content.Intent, flags: Int, isMutable: Boolean): android.app.PendingIntent? =
        android.app.PendingIntent.getBroadcast(context, requestCode, intent, flags)

    @JvmStatic
    fun getForegroundService(context: Context, requestCode: Int, intent: android.content.Intent, flags: Int, isMutable: Boolean): android.app.PendingIntent? =
        android.app.PendingIntent.getForegroundService(context, requestCode, intent, flags)
}
