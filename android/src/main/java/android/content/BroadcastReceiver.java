package android.content;

public abstract class BroadcastReceiver {
    public BroadcastReceiver() {
    }

    public abstract void onReceive(Context context, Intent intent);

    public final PendingResult goAsync() {
        return new PendingResult();
    }

    public static class PendingResult {
        public final void finish() {
        }
    }
}
