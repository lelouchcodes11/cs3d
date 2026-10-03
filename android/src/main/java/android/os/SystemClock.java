package android.os;

public final class SystemClock {
    private static final long START_NANOS = System.nanoTime();
    // Pretend the device booted a while ago so that uptime is never 0
    private static final long BOOT_OFFSET_MS = 60_000L;

    private SystemClock() {
    }

    public static void sleep(long ms) {
        long start = uptimeMillis();
        long duration = ms;
        boolean interrupted = false;
        do {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            duration = start + ms - uptimeMillis();
        } while (duration > 0);
        if (interrupted) Thread.currentThread().interrupt();
    }

    public static boolean setCurrentTimeMillis(long millis) {
        return false;
    }

    public static long uptimeMillis() {
        return (System.nanoTime() - START_NANOS) / 1_000_000L + BOOT_OFFSET_MS;
    }

    public static long uptimeNanos() {
        return (System.nanoTime() - START_NANOS) + BOOT_OFFSET_MS * 1_000_000L;
    }

    public static long elapsedRealtime() {
        return uptimeMillis();
    }

    public static long elapsedRealtimeNanos() {
        return uptimeNanos();
    }

    public static long currentThreadTimeMillis() {
        return uptimeMillis();
    }

    public static long currentThreadTimeMicro() {
        return uptimeNanos() / 1000L;
    }
}
