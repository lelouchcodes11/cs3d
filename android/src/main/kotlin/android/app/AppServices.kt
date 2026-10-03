@file:JvmName("AppServicesKt")

package android.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import com.lagradost.desktop.runtime.AndroidRuntime

open class ActivityManager {
    class MemoryInfo {
        @JvmField var availMem = 0L
        @JvmField var totalMem = 0L
        @JvmField var threshold = 0L
        @JvmField var lowMemory = false
    }

    class RunningAppProcessInfo {
        @JvmField var processName: String? = null
        @JvmField var pid = 0
        @JvmField var importance = IMPORTANCE_FOREGROUND

        companion object {
            const val IMPORTANCE_FOREGROUND = 100
            const val IMPORTANCE_VISIBLE = 200
        }
    }

    open fun getMemoryInfo(outInfo: MemoryInfo) {
        val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val os = bean as? com.sun.management.OperatingSystemMXBean
        outInfo.totalMem = os?.totalMemorySize ?: Runtime.getRuntime().maxMemory()
        outInfo.availMem = os?.freeMemorySize ?: Runtime.getRuntime().freeMemory()
        outInfo.threshold = outInfo.totalMem / 20
        outInfo.lowMemory = outInfo.availMem < outInfo.threshold
    }

    open fun getMemoryClass(): Int = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
    open fun getLargeMemoryClass(): Int = getMemoryClass()
    open fun isLowRamDevice(): Boolean = false
    open fun getRunningAppProcesses(): List<RunningAppProcessInfo> = listOf(RunningAppProcessInfo().also {
        it.processName = AndroidRuntime.PACKAGE_NAME
        it.pid = android.os.Process.myPid()
    })

    open fun clearApplicationUserData(): Boolean = false

    companion object {
        @JvmStatic
        fun isRunningInTestHarness(): Boolean = false

        @JvmStatic
        fun isUserAMonkey(): Boolean = false
    }
}

open class UiModeManager {
    companion object {
        const val MODE_NIGHT_AUTO = 0
        const val MODE_NIGHT_NO = 1
        const val MODE_NIGHT_YES = 2
    }

    open fun getCurrentModeType(): Int = Configuration.UI_MODE_TYPE_NORMAL
    open fun getNightMode(): Int = MODE_NIGHT_YES
    open fun setNightMode(mode: Int) {}
}

open class KeyguardManager {
    open fun isKeyguardLocked(): Boolean = false
    open fun isDeviceSecure(): Boolean = false
    open fun isKeyguardSecure(): Boolean = false
}

open class PendingIntent private constructor(@JvmField val intent: Intent?, private val kind: Int) {
    companion object {
        const val FLAG_ONE_SHOT = 1 shl 30
        const val FLAG_NO_CREATE = 1 shl 29
        const val FLAG_CANCEL_CURRENT = 1 shl 28
        const val FLAG_UPDATE_CURRENT = 1 shl 27
        const val FLAG_IMMUTABLE = 1 shl 26
        const val FLAG_MUTABLE = 1 shl 25

        @JvmStatic
        fun getActivity(context: Context?, requestCode: Int, intent: Intent?, flags: Int): PendingIntent = PendingIntent(intent, 0)

        @JvmStatic
        fun getActivity(context: Context?, requestCode: Int, intent: Intent?, flags: Int, options: Bundle?): PendingIntent = PendingIntent(intent, 0)

        @JvmStatic
        fun getBroadcast(context: Context?, requestCode: Int, intent: Intent?, flags: Int): PendingIntent = PendingIntent(intent, 1)

        @JvmStatic
        fun getService(context: Context?, requestCode: Int, intent: Intent?, flags: Int): PendingIntent = PendingIntent(intent, 2)

        @JvmStatic
        fun getForegroundService(context: Context?, requestCode: Int, intent: Intent?, flags: Int): PendingIntent = PendingIntent(intent, 2)
    }

    class CanceledException : Exception()

    fun send() {
        val i = intent ?: return
        val ctx = AndroidRuntime.applicationContext ?: return
        when (kind) {
            0 -> ctx.startActivity(i)
            1 -> ctx.sendBroadcast(i)
            else -> ctx.startService(i)
        }
    }

    fun cancel() {}

    val intentSender: android.content.IntentSender
        get() = android.content.IntentSender(this)
    fun getCreatorPackage(): String = AndroidRuntime.PACKAGE_NAME
}

open class NotificationChannel(private val id: String?, name: CharSequence?, importance: Int) {
    open var name: CharSequence? = name
    open var importance: Int = importance
    open var description: String? = null
    open var group: String? = null

    fun getId(): String? = id

    fun setSound(sound: android.net.Uri?, audioAttributes: Any?) {}
    fun enableVibration(vibration: Boolean) {}
    fun enableLights(lights: Boolean) {}
    fun setShowBadge(showBadge: Boolean) {}
    fun setLockscreenVisibility(lockscreenVisibility: Int) {}
    fun setBypassDnd(bypassDnd: Boolean) {}
}

open class Notification {
    @JvmField var `when` = System.currentTimeMillis()
    @JvmField var flags = 0
    @JvmField var priority = 0
    @JvmField var contentIntent: PendingIntent? = null
    @JvmField var tickerText: CharSequence? = null
    @JvmField var extras = Bundle()

    /** Desktop fields read by the notification host */
    @JvmField var title: CharSequence? = null
    @JvmField var text: CharSequence? = null
    @JvmField var bigText: CharSequence? = null
    @JvmField var subText: CharSequence? = null
    @JvmField var channelId: String? = null
    @JvmField var progress = -1
    @JvmField var progressMax = 0
    @JvmField var progressIndeterminate = false
    @JvmField var ongoing = false
    @JvmField var silent = false
    @JvmField var largeIcon: Bitmap? = null
    @JvmField var actions: MutableList<Action> = ArrayList()
    @JvmField var group: String? = null
    @JvmField var onlyAlertOnce = false

    class Action(@JvmField val icon: Int, @JvmField val title: CharSequence?, @JvmField val actionIntent: PendingIntent?)

    companion object {
        const val FLAG_ONGOING_EVENT = 0x00000002
        const val FLAG_AUTO_CANCEL = 0x00000010
        const val FLAG_NO_CLEAR = 0x00000020
        const val FLAG_FOREGROUND_SERVICE = 0x00000040
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
        const val DEFAULT_ALL = -1
        const val EXTRA_TITLE = "android.title"
        const val EXTRA_TEXT = "android.text"
    }
}

open class NotificationManager {
    companion object {
        const val IMPORTANCE_UNSPECIFIED = -1000
        const val IMPORTANCE_NONE = 0
        const val IMPORTANCE_MIN = 1
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
        const val IMPORTANCE_HIGH = 4
        const val IMPORTANCE_MAX = 5
    }

    private val channels = LinkedHashMap<String?, NotificationChannel>()

    open fun createNotificationChannel(channel: NotificationChannel) {
        channels[channel.getId()] = channel
    }

    open fun createNotificationChannels(channels: List<NotificationChannel>) = channels.forEach { createNotificationChannel(it) }
    open fun getNotificationChannel(channelId: String?): NotificationChannel? = channels[channelId]
    open fun getNotificationChannels(): List<NotificationChannel> = channels.values.toList()
    open fun deleteNotificationChannel(channelId: String?) {
        channels.remove(channelId)
    }

    open fun notify(id: Int, notification: Notification) = notify(null, id, notification)
    open fun notify(tag: String?, id: Int, notification: Notification) {
        com.lagradost.desktop.runtime.Notifications.post(tag, id, notification)
    }

    open fun cancel(id: Int) = cancel(null, id)
    open fun cancel(tag: String?, id: Int) {
        com.lagradost.desktop.runtime.Notifications.cancel(tag, id)
    }

    open fun cancelAll() {
        com.lagradost.desktop.runtime.Notifications.cancelAll()
    }

    open fun areNotificationsEnabled(): Boolean = true
    open fun getActiveNotifications(): Array<android.service.notification.StatusBarNotification> =
        com.lagradost.desktop.runtime.Notifications.active.entries.map {
            android.service.notification.StatusBarNotification(it.key.tag, it.key.id, it.value)
        }.toTypedArray()
}

open class Service : android.content.ContextWrapper(null) {
    companion object {
        const val START_STICKY = 1
        const val START_NOT_STICKY = 2
        const val START_REDELIVER_INTENT = 3
        const val STOP_FOREGROUND_REMOVE = 1
        const val STOP_FOREGROUND_DETACH = 2
    }

    fun attach(base: Context) = attachBaseContext(base)
    open fun onCreate() {}
    open fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    open fun onDestroy() {}
    open fun onBind(intent: Intent?): android.os.IBinder? = null
    open fun onTaskRemoved(rootIntent: Intent?) {}
    private var foregroundId = 0

    fun startForeground(id: Int, notification: Notification) {
        foregroundId = id
        notification.ongoing = true
        com.lagradost.desktop.runtime.Notifications.post(null, id, notification)
    }

    fun startForeground(id: Int, notification: Notification, foregroundServiceType: Int) = startForeground(id, notification)
    fun stopForeground(removeNotification: Boolean) {
        if (removeNotification && foregroundId != 0) com.lagradost.desktop.runtime.Notifications.cancel(null, foregroundId)
    }
    fun stopForeground(notificationBehavior: Int) = stopForeground(notificationBehavior == STOP_FOREGROUND_REMOVE)
    fun stopSelf() {
        stopForeground(true)
        com.lagradost.desktop.runtime.Services.stop(this)
    }
    fun stopSelf(startId: Int) = stopSelf()
    fun stopSelfResult(startId: Int): Boolean {
        stopSelf()
        return true
    }
    open fun onTimeout(startId: Int) {}
    open fun onTimeout(startId: Int, fgsType: Int) {}
}

open class IntentService(name: String?) : Service() {
    protected open fun onHandleIntent(intent: Intent?) {}
}

open class SearchManager {
    companion object {
        const val QUERY = "query"
        const val SUGGEST_COLUMN_TEXT_1 = "suggest_text_1"
    }
}

open class DownloadManager {
    class Request(uri: android.net.Uri?)
    class Query
}

open class WallpaperManager
