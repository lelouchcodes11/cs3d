package android.service.notification;

import android.app.Notification;

public class StatusBarNotification {
    private final String tag;
    private final int id;
    private final Notification notification;

    public StatusBarNotification(String tag, int id, Notification notification) {
        this.tag = tag;
        this.id = id;
        this.notification = notification;
    }

    public String getTag() {
        return tag;
    }

    public int getId() {
        return id;
    }

    public Notification getNotification() {
        return notification;
    }

    public String getPackageName() {
        return "com.lagradost.cloudstream3";
    }

    public long getPostTime() {
        return notification.when;
    }

    public boolean isOngoing() {
        return notification.ongoing;
    }
}
