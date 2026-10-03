package android.os;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Desktop Looper. The main looper dispatches on the AWT event dispatch thread, which is also the
 * thread Compose runs on, so "main thread" has the same meaning as on Android. Other loopers run
 * on their own thread (Looper.prepare() + Looper.loop(), or HandlerThread).
 */
public final class Looper {
    private static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<>();
    private static volatile Looper sMainLooper;
    private static final ScheduledExecutorService sTimer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "main-looper-timer");
        t.setDaemon(true);
        return t;
    });

    final MessageQueue mQueue;
    final Thread mThread;
    private final boolean mIsMain;
    private volatile boolean mQuit = false;

    private Looper(boolean main, Thread thread) {
        mIsMain = main;
        mThread = thread;
        mQueue = new MessageQueue(this);
    }

    public static void prepare() {
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        sThreadLocal.set(new Looper(false, Thread.currentThread()));
    }

    @Deprecated
    public static void prepareMainLooper() {
    }

    public static Looper getMainLooper() {
        Looper l = sMainLooper;
        if (l == null) {
            synchronized (Looper.class) {
                if (sMainLooper == null) sMainLooper = new Looper(true, null);
                l = sMainLooper;
            }
        }
        return l;
    }

    public static Looper myLooper() {
        if (EventQueue.isDispatchThread()) return getMainLooper();
        return sThreadLocal.get();
    }

    public static MessageQueue myQueue() {
        Looper l = myLooper();
        return l == null ? null : l.mQueue;
    }

    public static void loop() {
        Looper me = myLooper();
        if (me == null) throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        if (me.mIsMain) return;
        me.mQueue.runLoop();
    }

    public boolean isCurrentThread() {
        if (mIsMain) return EventQueue.isDispatchThread();
        return Thread.currentThread() == mThread;
    }

    public Thread getThread() {
        if (mIsMain) {
            final Thread[] edt = new Thread[1];
            if (EventQueue.isDispatchThread()) return Thread.currentThread();
            try {
                EventQueue.invokeAndWait(() -> edt[0] = Thread.currentThread());
            } catch (Exception ignored) {
            }
            return edt[0];
        }
        return mThread;
    }

    public MessageQueue getQueue() {
        return mQueue;
    }

    public void quit() {
        mQuit = true;
        mQueue.quit();
    }

    public void quitSafely() {
        quit();
    }

    boolean isMain() {
        return mIsMain;
    }

    boolean isQuitting() {
        return mQuit;
    }

    /** Schedule processing of the main queue at the given uptime */
    void scheduleMain(long whenUptime) {
        long delay = whenUptime - SystemClock.uptimeMillis();
        if (delay <= 0) {
            EventQueue.invokeLater(mQueue::processDueMessages);
        } else {
            sTimer.schedule(() -> EventQueue.invokeLater(mQueue::processDueMessages), delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public String toString() {
        return "Looper (" + (mIsMain ? "main" : String.valueOf(mThread)) + ")";
    }
}
