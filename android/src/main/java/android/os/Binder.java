package android.os;

public class Binder implements IBinder {
    public Binder() {
    }

    public static int getCallingUid() {
        return Process.myUid();
    }

    public static int getCallingPid() {
        return Process.myPid();
    }

    public static long clearCallingIdentity() {
        return 0;
    }

    public static void restoreCallingIdentity(long token) {
    }
}
