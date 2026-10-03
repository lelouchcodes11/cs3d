package androidx.work

import android.app.Notification

class ForegroundInfo(
    val notificationId: Int,
    val notification: Notification,
    val foregroundServiceType: Int = 0
) {
    constructor(notificationId: Int, notification: Notification) : this(notificationId, notification, 0)
}
