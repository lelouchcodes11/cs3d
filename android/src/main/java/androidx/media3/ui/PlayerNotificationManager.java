package androidx.media3.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.Player;
import java.util.Map;
import java.util.List;

public class PlayerNotificationManager {
    public static final String EXTRA_INSTANCE_ID = "INSTANCE_ID";

    public interface BitmapCallback {
        void onBitmap(Bitmap bitmap);
    }

    public interface MediaDescriptionAdapter {
        CharSequence getCurrentContentTitle(Player player);
        PendingIntent createCurrentContentIntent(Player player);
        CharSequence getCurrentContentText(Player player);
        Bitmap getCurrentLargeIcon(Player player, BitmapCallback callback);
    }

    public interface CustomActionReceiver {
        Map<String, NotificationCompat.Action> createCustomActions(Context context, int instanceId);
        List<String> getCustomActions(Player player);
        void onCustomAction(Player player, String action, Intent intent);
    }

    public interface NotificationListener {
        default void onNotificationCancelled(int notificationId, boolean dismissedByUser) {}
        default void onNotificationPosted(int notificationId, Object notification, boolean ongoing) {}
    }

    public static class Builder {
        public Builder(Context context, int notificationId, String channelId) {}
        public Builder setChannelNameResourceId(int resourceId) { return this; }
        public Builder setChannelDescriptionResourceId(int resourceId) { return this; }
        public Builder setMediaDescriptionAdapter(MediaDescriptionAdapter adapter) { return this; }
        public Builder setCustomActionReceiver(CustomActionReceiver receiver) { return this; }
        public Builder setNotificationListener(NotificationListener listener) { return this; }
        public Builder setSmallIcon(int icon) { return this; }
        public Builder setSmallIconResourceId(int icon) { return this; }
        public Builder setPlayActionIconResourceId(int icon) { return this; }
        public Builder setPauseActionIconResourceId(int icon) { return this; }
        public Builder setStopActionIconResourceId(int icon) { return this; }
        public Builder setRewindActionIconResourceId(int icon) { return this; }
        public Builder setFastForwardActionIconResourceId(int icon) { return this; }
        public Builder setNextActionIconResourceId(int icon) { return this; }
        public Builder setPreviousActionIconResourceId(int icon) { return this; }
        public PlayerNotificationManager build() { return new PlayerNotificationManager(); }
    }

    public void setPlayer(Object player) {}
    public void setMediaSessionToken(Object token) {}
    public void setPriority(int priority) {}
    public void setColorized(boolean colorized) {}
    public void setUseChronometer(boolean useChronometer) {}
    public void setUsePreviousAction(boolean usePreviousAction) {}
    public void setUsePreviousActionInCompactView(boolean usePreviousActionInCompactView) {}
    public void setUseNextAction(boolean useNextAction) {}
    public void setUseNextActionInCompactView(boolean useNextActionInCompactView) {}
    public void setUseFastForwardAction(boolean useFastForwardAction) {}
    public void setUseFastForwardActionInCompactView(boolean useFastForwardActionInCompactView) {}
    public void setUseRewindAction(boolean useRewindAction) {}
    public void setUseStopAction(boolean useStopAction) {}
}
