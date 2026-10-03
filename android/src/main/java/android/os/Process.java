package android.os;

public class Process {
    public static final int THREAD_PRIORITY_DEFAULT = 0;
    public static final int THREAD_PRIORITY_LOWEST = 19;
    public static final int THREAD_PRIORITY_BACKGROUND = 10;
    public static final int THREAD_PRIORITY_FOREGROUND = -2;
    public static final int THREAD_PRIORITY_DISPLAY = -4;
    public static final int THREAD_PRIORITY_URGENT_DISPLAY = -8;
    public static final int THREAD_PRIORITY_AUDIO = -16;
    public static final int THREAD_PRIORITY_URGENT_AUDIO = -19;
    public static final int THREAD_PRIORITY_MORE_FAVORABLE = -1;
    public static final int THREAD_PRIORITY_LESS_FAVORABLE = +1;

    public static int myPid() {
        return (int) ProcessHandle.current().pid();
    }

    public static int myTid() {
        return (int) Thread.currentThread().threadId();
    }

    public static int myUid() {
        return 10000;
    }

    public static void setThreadPriority(int priority) {
    }

    public static void setThreadPriority(int tid, int priority) {
    }

    public static int getThreadPriority(int tid) {
        return THREAD_PRIORITY_DEFAULT;
    }

    /** Killing our own pid exits the app like on Android */
    public static void killProcess(int pid) {
        if (pid == myPid()) System.exit(0);
    }
}
