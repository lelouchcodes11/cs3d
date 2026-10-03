package android.os;

public final class PowerManager {
    public static final int PARTIAL_WAKE_LOCK = 0x00000001;
    public static final int FULL_WAKE_LOCK = 0x0000001a;
    public static final int SCREEN_DIM_WAKE_LOCK = 0x00000006;
    public static final int SCREEN_BRIGHT_WAKE_LOCK = 0x0000000a;
    public static final int ACQUIRE_CAUSES_WAKEUP = 0x10000000;
    public static final int ON_AFTER_RELEASE = 0x20000000;

    public PowerManager() {
    }

    public WakeLock newWakeLock(int levelAndFlags, String tag) {
        return new WakeLock();
    }

    public boolean isInteractive() {
        return true;
    }

    @Deprecated
    public boolean isScreenOn() {
        return true;
    }

    public boolean isPowerSaveMode() {
        return false;
    }

    public boolean isIgnoringBatteryOptimizations(String packageName) {
        return true;
    }

    public boolean isDeviceIdleMode() {
        return false;
    }

    public final class WakeLock {
        private boolean mHeld;

        WakeLock() {
        }

        public void acquire() {
            mHeld = true;
        }

        public void acquire(long timeout) {
            mHeld = true;
        }

        public void release() {
            mHeld = false;
        }

        public void release(int flags) {
            mHeld = false;
        }

        public boolean isHeld() {
            return mHeld;
        }

        public void setReferenceCounted(boolean value) {
        }
    }
}
